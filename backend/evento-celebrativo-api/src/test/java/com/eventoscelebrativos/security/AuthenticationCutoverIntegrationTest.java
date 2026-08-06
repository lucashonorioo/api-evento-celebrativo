package com.eventoscelebrativos.security;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, contra o encadeamento real de filtros (login via {@code /oauth2/token}, emissao de JWT
 * assinado, validacao de bearer via {@link UserAccountJwtAuthenticationConverter}), que a
 * autenticacao passou a usar exclusivamente {@code UserAccount}/{@code Person.active}, sem fallback
 * para o modelo legado, e que estado atual (enabled/active/username/roles) tem efeito imediato na
 * proxima requisicao, sem esperar expiracao do token.
 * <p>
 * Usa MockMvc para todo o fluxo, inclusive {@code /oauth2/token}: esse endpoint e servido dentro do
 * mesmo contexto Spring (nao requer socket real). Apenas o proxy {@code /public/login} precisa de
 * servidor real (chamada HTTP de loopback via RestTemplate) e e testado separadamente em
 * {@link PublicLoginProxyIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationCutoverIntegrationTest {

    private static final String CLIENT_ID = "myclientid";
    private static final String CLIENT_SECRET = "myclientsecret";
    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final Object MISSING_CLAIM = new Object();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private PersonRepository personRepository;

    @MockitoSpyBean
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- Login

    @Test
    void shouldIssueTokenWhenAccountEnabledAndPersonActive() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "correct-horse-battery", true, true, "ROLE_OPERATOR");

        MvcResult result = requestToken(phone, "correct-horse-battery");

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.has("access_token"));
    }

    @Test
    void shouldRejectLoginWhenAccountDisabled() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-valida", false, true, "ROLE_OPERATOR");

        assertInvalidGrant(requestToken(phone, "senha-valida"));
    }

    @Test
    void shouldRejectLoginWhenPersonInactive() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-valida", true, false, "ROLE_OPERATOR");

        assertInvalidGrant(requestToken(phone, "senha-valida"));
    }

    @Test
    void shouldRejectLoginWhenUsernameDoesNotExist() throws Exception {
        assertInvalidGrant(requestToken(uniquePhone(), "qualquer-coisa"));
    }

    @Test
    void shouldRejectLoginWhenPasswordIsWrong() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-correta", true, true, "ROLE_OPERATOR");

        assertInvalidGrant(requestToken(phone, "senha-errada"));
    }

    @Test
    void shouldRejectLoginWhenPersonHasNoAccount() throws Exception {
        String phone = uniquePhone();
        Person person = new Person();
        person.setName("Sem Conta");
        person.setPhoneNumber(phone);
        person.setActive(true);
        personRepository.saveAndFlush(person);

        assertInvalidGrant(requestToken(phone, "qualquer-senha"));
    }

    @Test
    void shouldRejectLoginWhenAccountHasNoRoles() throws Exception {
        String phone = uniquePhone();
        Person person = createPerson(phone, true);
        Person saved = personRepository.saveAndFlush(person);
        createAccount(saved, phone, "senha-da-conta", Set.of());

        clearAuthenticationInvocations();
        MvcResult result = requestToken(phone, "senha-da-conta");

        assertInvalidGrant(result);
        verify(personRepository, never()).findByPhoneNumber(anyString());
    }

    @Test
    void shouldUseAccountRolesForAuthorities() throws Exception {
        String phone = uniquePhone();
        Person person = createPerson(phone, true);
        personRepository.saveAndFlush(person);
        createAccount(person, phone, "senha", Set.of("ROLE_OPERATOR"));

        MvcResult result = requestToken(phone, "senha");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());
        List<String> authorities = jwt.getClaimAsStringList("authorities");

        assertEquals(List.of("ROLE_OPERATOR"), authorities);
    }

    @Test
    void shouldNotExposePasswordHashInIssuedToken() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-secreta", true, true, "ROLE_OPERATOR");

        MvcResult result = requestToken(phone, "senha-secreta");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());

        assertNull(jwt.getClaim("passwordHash"));
        assertNull(jwt.getClaim("password"));
        assertFalse(jwt.getClaims().toString().contains("senha-secreta"));
    }

    @Test
    void shouldStoreOnlySanitizedAuthenticationStateDuringLoginFlow() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-secreta", true, true, "ROLE_OPERATOR");

        MvcResult result = requestToken(phone, "senha-secreta");

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String tokenValue = body.get("access_token").asText();
        OAuth2Authorization authorization = authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
        assertInstanceOf(OAuth2Authorization.class, authorization);

        Authentication savedPrincipal = authorization.getAttribute(Principal.class.getName());
        assertInstanceOf(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class, savedPrincipal);
        assertInstanceOf(AuthenticatedUser.class, savedPrincipal.getPrincipal());
        assertNull(savedPrincipal.getCredentials());
        assertNull(savedPrincipal.getDetails());
        assertDoesNotExposeSensitiveValues(String.valueOf(savedPrincipal), "senha-secreta", "passwordHash", "UserAccountCredentials");

        Authentication securityContextAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (securityContextAuthentication != null) {
            assertDoesNotExposeSensitiveValues(
                    String.valueOf(securityContextAuthentication)
                            + securityContextAuthentication.getPrincipal()
                            + securityContextAuthentication.getCredentials()
                            + securityContextAuthentication.getDetails(),
                    "senha-secreta",
                    "passwordHash",
                    "UserAccountCredentials"
            );
        }
    }

    @Test
    void shouldReturnSameErrorContractForEveryInvalidCredentialScenario() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha-valida", true, true, "ROLE_OPERATOR");
        createPersonWithAccount(uniquePhone(), "senha-valida", false, true, "ROLE_OPERATOR");

        MvcResult wrongPassword = requestToken(phone, "senha-errada");
        MvcResult unknownUsername = requestToken(uniquePhone(), "qualquer");

        assertEquals(wrongPassword.getResponse().getStatus(), unknownUsername.getResponse().getStatus());
        JsonNode wrongPasswordBody = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode unknownUsernameBody = objectMapper.readTree(unknownUsername.getResponse().getContentAsString());
        assertEquals(wrongPasswordBody.get("error").asText(), unknownUsernameBody.get("error").asText());
        assertDoesNotExposeSensitiveValues(wrongPassword.getResponse().getContentAsString(), phone, "senha-errada", "passwordHash");
        assertDoesNotExposeSensitiveValues(unknownUsername.getResponse().getContentAsString(), "qualquer", "passwordHash");
    }

    // ---------------------------------------------------------------- Bearer / claims

    @Test
    void shouldAcceptBearerTokenOnProtectedEndpointWithAccountIdClaim() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldIncludeAccountIdAndTokenVersionClaimsInEveryIssuedToken() throws Exception {
        // Prova, ponta a ponta, que todo token emitido apos o corte tem account_id (condicao
        // necessaria para que tokens emitidos ANTES do corte, sem esse claim, sejam rejeitados). A
        // rejeicao explicita de um token sem account_id e testada isoladamente em
        // UserAccountJwtAuthenticationConverterTest (nao ha como forjar, nesta suite, um JWT valido
        // assinado pela chave privada real da aplicacao sem passar por este mesmo fluxo de emissao).
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");

        Jwt jwt = jwtDecoder.decode(obtainAccessToken(phone, "senha"));

        assertTrue(jwt.hasClaim("account_id"));
        assertTrue(jwt.hasClaim("token_version"));
        assertEquals(0L, ((Number) jwt.getClaim("token_version")).longValue());
    }

    @Test
    void shouldAcceptLegacyBearerWithoutTokenVersionOnlyWhileAccountVersionIsZero() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        Long accountId = userAccountRepository.findByUsername(phone).orElseThrow().getId();
        String legacyTokenWithoutVersionClaim = signedBearerToken(accountId, phone, phone);

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyTokenWithoutVersionClaim))
                .andExpect(status().isOk());
    }

    @Test
    void shouldInvalidateCurrentTokenAfterOwnPasswordChangeAndRequireNewTokenVersion() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "123456", true, true, "ROLE_OPERATOR");
        String oldToken = obtainAccessToken(phone, "123456");

        mockMvc.perform(put("/pessoas/me/conta/senha")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "123456",
                                  "newPassword": "654321"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        String newToken = obtainAccessToken(phone, "654321");
        Jwt newJwt = jwtDecoder.decode(newToken);
        assertEquals(1L, ((Number) newJwt.getClaim("token_version")).longValue());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectBearerTokensWithInvalidClaimsWithoutInternalErrorOrLegacyFallback() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        Long accountId = userAccountRepository.findByUsername(phone).orElseThrow().getId();
        String otherPhone = uniquePhone();

        List<InvalidTokenScenario> scenarios = List.of(
                new InvalidTokenScenario("account_id ausente", signedBearerToken(MISSING_CLAIM, phone, phone)),
                new InvalidTokenScenario("account_id String", signedBearerToken(String.valueOf(accountId), phone, phone)),
                new InvalidTokenScenario("account_id decimal", signedBearerToken(BigDecimal.valueOf(accountId).add(BigDecimal.valueOf(0.5)), phone, phone)),
                new InvalidTokenScenario("account_id zero", signedBearerToken(0L, phone, phone)),
                new InvalidTokenScenario("account_id negativo", signedBearerToken(-1L, phone, phone)),
                new InvalidTokenScenario("account_id inexistente", signedBearerToken(999_999_999_999L, phone, phone)),
                new InvalidTokenScenario("sub ausente", signedBearerToken(accountId, null, phone)),
                new InvalidTokenScenario("username ausente", signedBearerToken(accountId, phone, MISSING_CLAIM)),
                new InvalidTokenScenario("sub divergente", signedBearerToken(accountId, otherPhone, phone)),
                new InvalidTokenScenario("username divergente", signedBearerToken(accountId, phone, otherPhone)),
                new InvalidTokenScenario("claim com estrutura inesperada", signedBearerToken(Map.of("id", accountId), phone, phone))
        );

        for (InvalidTokenScenario scenario : scenarios) {
            clearAuthenticationInvocations();
            MvcResult result = mockMvc.perform(get("/pessoas/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + scenario.token()))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            String exposedError = result.getResponse().getContentAsString()
                    + String.valueOf(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE));
            assertDoesNotExposeSensitiveValues(
                    exposedError,
                    phone,
                    otherPhone,
                    "senha",
                    "passwordHash",
                    "UserAccountCredentials",
                    "PersonDetailsServiceImpl",
                    "phoneNumber",
                    "account_id"
            );
            verify(userAccountRepository, never()).findByUsernameForAuthentication(anyString());
            verify(personRepository, never()).findByPhoneNumber(anyString());
        }
    }

    @Test
    void shouldRejectBearerTokenWhenAccountIdDoesNotExist() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");

        Long accountId = userAccountRepository.findByUsername(phone).orElseThrow().getId();
        jdbcTemplate.update("DELETE FROM tb_user_account_role WHERE user_account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM tb_user_account WHERE id = ?", accountId);

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBearerTokenWhenAccountHasNoRolesEvenIfTokenCarriesAuthoritiesClaim() throws Exception {
        String phone = uniquePhone();
        Person person = createPerson(phone, true);
        Person saved = personRepository.saveAndFlush(person);
        UserAccount account = createAccount(saved, phone, "senha-da-conta", Set.of());
        String token = signedBearerToken(account.getId(), phone, phone, List.of("ROLE_ADMIN"));

        clearAuthenticationInvocations();
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(userAccountRepository, times(1)).findByIdForAuthentication(account.getId());
        verify(userAccountRepository, never()).findByUsernameForAuthentication(anyString());
        verify(personRepository, never()).findByPhoneNumber(anyString());
    }

    @Test
    void shouldInvalidateTokenImmediatelyWhenAccountIsDisabled() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE WHERE username = ?", phone);

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldInvalidateTokenImmediatelyWhenPersonBecomesInactive() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE tb_person SET active = FALSE WHERE phone_number = ?", phone);

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldInvalidateTokenImmediatelyWhenUsernameChangesThroughRealFlow() throws Exception {
        Long personId = createReaderViaRealFlow();
        Person before = personRepository.findById(personId).orElseThrow();
        String token = obtainAccessToken(before.getPhoneNumber(), "123456");

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String newPhone = uniquePhone();
        mockMvc.perform(put("/leitores/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Reader Renamed",
                                "phoneNumber", newPhone,
                                "birthdayDate", BIRTHDAY.toString()
                        )))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAdminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepOldTokenInvalidWhenUsernameReturnsToPreviousValue() throws Exception {
        Long personId = createReaderViaRealFlow();
        Person before = personRepository.findById(personId).orElseThrow();
        String originalPhone = before.getPhoneNumber();
        String oldToken = obtainAccessToken(originalPhone, "123456");
        String adminToken = obtainAdminToken();
        String intermediatePhone = uniquePhone();

        updateReaderPhone(personId, intermediatePhone, adminToken);
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        updateReaderPhone(personId, originalPhone, adminToken);

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        String newToken = obtainAccessToken(originalPhone, "123456");
        Jwt newJwt = jwtDecoder.decode(newToken);
        assertEquals(2L, ((Number) newJwt.getClaim("token_version")).longValue());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepOldTokenInvalidAfterAccountIsReenabled() throws Exception {
        Long personId = createPersonIdWithRole("ROLE_OPERATOR");
        Person person = personRepository.findById(personId).orElseThrow();
        String oldToken = obtainAccessToken(person.getPhoneNumber(), "senha");
        String adminToken = obtainAdminToken();

        mockMvc.perform(put("/pessoas/{id}/conta/habilitacao", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/pessoas/{id}/conta/habilitacao", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        String newToken = obtainAccessToken(person.getPhoneNumber(), "senha");
        Jwt newJwt = jwtDecoder.decode(newToken);
        assertEquals(1L, ((Number) newJwt.getClaim("token_version")).longValue());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepOldTokenInvalidAfterPersonIsReactivated() throws Exception {
        Long personId = createPersonIdWithRole("ROLE_OPERATOR");
        Person person = personRepository.findById(personId).orElseThrow();
        String oldToken = obtainAccessToken(person.getPhoneNumber(), "senha");
        String adminToken = obtainAdminToken();

        mockMvc.perform(put("/pessoas/{id}/status", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/pessoas/{id}/status", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        String newToken = obtainAccessToken(person.getPhoneNumber(), "senha");
        Jwt newJwt = jwtDecoder.decode(newToken);
        assertEquals(1L, ((Number) newJwt.getClaim("token_version")).longValue());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldInvalidateTokenAndPasswordsAfterAdministrativePasswordResetOfAnotherAccount() throws Exception {
        Long personId = createPersonIdWithRole("ROLE_OPERATOR");
        Person person = personRepository.findById(personId).orElseThrow();
        String oldToken = obtainAccessToken(person.getPhoneNumber(), "senha");
        String adminToken = obtainAdminToken();

        mockMvc.perform(put("/pessoas/{id}/conta/senha", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"654321\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        assertInvalidGrant(requestToken(person.getPhoneNumber(), "senha"));
        String newToken = obtainAccessToken(person.getPhoneNumber(), "654321");
        Jwt newJwt = jwtDecoder.decode(newToken);
        assertEquals(1L, ((Number) newJwt.getClaim("token_version")).longValue());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAdministrativePasswordResetOfOwnAccount() throws Exception {
        Person admin = createPersonWithAccount(uniquePhone(), "senha", true, true, "ROLE_ADMIN");
        String adminToken = obtainAccessToken(admin.getPhoneNumber(), "senha");

        MvcResult result = mockMvc.perform(put("/pessoas/{id}/conta/senha", admin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"654321\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andReturn();

        assertEquals("SELF_ADMIN_PASSWORD_RESET_NOT_ALLOWED",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReflectDemotionImmediatelyThroughRealFlow() throws Exception {
        Long personId = createPersonIdWithRole("ROLE_ADMIN");
        Person person = personRepository.findById(personId).orElseThrow();
        String token = obtainAccessToken(person.getPhoneNumber(), "senha");

        mockMvc.perform(get("/pessoas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // segundo admin como ancora, para nao esbarrar na protecao de ultimo administrador
        createPersonIdWithRole("ROLE_ADMIN");

        mockMvc.perform(put("/pessoas/{id}/roles", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_OPERATOR\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAdminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReflectPromotionImmediatelyThroughRealFlow() throws Exception {
        Long personId = createPersonIdWithRole("ROLE_OPERATOR");
        Person person = personRepository.findById(personId).orElseThrow();
        String token = obtainAccessToken(person.getPhoneNumber(), "senha");

        mockMvc.perform(get("/pessoas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/{id}/roles", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAdminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectForbiddenAuthorityWithoutQueryingAccount() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");

        mockMvc.perform(get("/pessoas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowPublicEndpointWithoutBearerOrAccountLookup() throws Exception {
        clearAuthenticationInvocations();

        mockMvc.perform(get("/eventos")).andExpect(status().isOk());

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldLookupAccountExactlyOnceForValidBearerAndNeverByUsername() throws Exception {
        String phone = uniquePhone();
        createPersonWithAccount(phone, "senha", true, true, "ROLE_OPERATOR");
        String token = obtainAccessToken(phone, "senha");
        Long accountId = userAccountRepository.findByUsername(phone).orElseThrow().getId();
        clearAuthenticationInvocations();

        mockMvc.perform(get("/pessoas/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        verify(userAccountRepository, times(1)).findByIdForAuthentication(accountId);
        verify(userAccountRepository, never()).findByUsernameForAuthentication(anyString());
        verify(personRepository, never()).findByPhoneNumber(anyString());    }

    @Test
    void shouldNotRequireAuthenticationForOptionsPreflight() throws Exception {
        mockMvc.perform(options("/pessoas/me")
                        .header("Access-Control-Request-Method", "GET")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Helpers

    private void assertInvalidGrant(MvcResult result) throws Exception {
        assertNotEquals(200, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("invalid_grant", body.get("error").asText());
    }

    private void assertDoesNotExposeSensitiveValues(String content, String... forbiddenValues) {
        for (String forbiddenValue : forbiddenValues) {
            assertFalse(content.contains(forbiddenValue), "Sensitive or internal value was exposed");
        }
    }

    private void clearAuthenticationInvocations() {
        clearInvocations(userAccountRepository, personRepository);
    }

    private String signedBearerToken(Object accountIdClaim, String subject, Object usernameClaim) {
        return signedBearerToken(accountIdClaim, subject, usernameClaim, List.of("ROLE_OPERATOR"));
    }

    private String signedBearerToken(
            Object accountIdClaim,
            String subject,
            Object usernameClaim,
            List<String> authorities
    ) {
        InstantPair instantPair = InstantPair.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuedAt(instantPair.issuedAt())
                .expiresAt(instantPair.expiresAt())
                .claim("authorities", authorities);
        if (subject != null) {
            claimsBuilder.subject(subject);
        }
        if (usernameClaim != MISSING_CLAIM) {
            claimsBuilder.claim("username", usernameClaim);
        }
        if (accountIdClaim != MISSING_CLAIM) {
            claimsBuilder.claim("account_id", accountIdClaim);
        }
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(),
                        claimsBuilder.build()
                ))
                .getTokenValue();
    }

    private MvcResult requestToken(String username, String password) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", username)
                        .param("password", password))
                .andReturn();
    }

    private String obtainAccessToken(String username, String password) throws Exception {
        MvcResult result = requestToken(username, password);
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("access_token").asText();
    }

    private String obtainAdminToken() throws Exception {
        return obtainAccessToken(createPersonWithAccount(uniquePhone(), "senha", true, true, "ROLE_ADMIN").getPhoneNumber(), "senha");
    }

    private Long createPersonIdWithRole(String authority) {
        Person person = createPersonWithAccount(uniquePhone(), "senha", true, true, authority);
        return person.getId();
    }

    private Long createReaderViaRealFlow() throws Exception {
        String adminToken = obtainAdminToken();
        String phone = uniquePhone();
        MvcResult result = mockMvc.perform(post("/leitores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Reader Cutover",
                                "phoneNumber", phone,
                                "birthdayDate", BIRTHDAY.toString(),
                                "password", "123456"
                        )))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void updateReaderPhone(Long personId, String phone, String adminToken) throws Exception {
        mockMvc.perform(put("/leitores/{id}", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Reader Phone Updated",
                                "phoneNumber", phone,
                                "birthdayDate", BIRTHDAY.toString()
                        )))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private Person createPersonWithAccount(String phone, String rawPassword, boolean accountEnabled, boolean personActive, String authority) {
        Person person = createPerson(phone, personActive);
        Person saved = personRepository.saveAndFlush(person);
        createAccount(saved, phone, rawPassword, Set.of(authority));
        if (!accountEnabled) {
            jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE WHERE person_id = ?", saved.getId());
        }
        return saved;
    }

    private Person createPerson(String phone, boolean active) {
        Person person = new Person();
        person.setName("Auth Cutover Person");
        person.setPhoneNumber(phone);
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(active);
        return person;
    }

    private UserAccount createAccount(Person person, String username, String rawPassword, Set<String> authorities) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(person, username, passwordEncoder.encode(rawPassword), now, now);
        UserAccount saved = userAccountRepository.saveAndFlush(account);
        for (String authority : authorities) {
            Role role = roleRepository.findByAuthority(authority).orElseThrow();
            userAccountRoleRepository.saveAndFlush(new UserAccountRole(saved, role));
        }
        return saved;
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }

    private record InvalidTokenScenario(String name, String token) {
    }

    private record InstantPair(java.time.Instant issuedAt, java.time.Instant expiresAt) {

        private static InstantPair now() {
            java.time.Instant issuedAt = java.time.Instant.now();
            return new InstantPair(issuedAt, issuedAt.plusSeconds(60));
        }
    }
}
