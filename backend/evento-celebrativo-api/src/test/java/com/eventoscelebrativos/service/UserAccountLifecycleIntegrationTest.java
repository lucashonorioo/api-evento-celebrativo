package com.eventoscelebrativos.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class UserAccountLifecycleIntegrationTest {

    private static final String CLIENT_ID = "myclientid";
    private static final String CLIENT_SECRET = "myclientsecret";
    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateMinisterialPersonWithoutAccountAndCreateAccountLater() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            String adminToken = adminToken(cleanupPersonIds);
            String phone = uniquePhone();

            MvcResult createdReader = mockMvc.perform(post("/leitores")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Reader Sem Conta",
                                      "phoneNumber": "%s",
                                      "birthdayDate": "%s",
                                      "createAccess": false
                                    }
                                    """.formatted(phone, BIRTHDAY)))
                    .andExpect(status().isCreated())
                    .andReturn();

            long personId = objectMapper.readTree(createdReader.getResponse().getContentAsString()).get("id").asLong();
            cleanupPersonIds.add(personId);

            assertEquals(1, countRows("tb_person", "id", personId));
            assertEquals(0, countRows("tb_user_account", "person_id", personId));

            mockMvc.perform(post("/pessoas/{id}/conta", personId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "initialPassword": "123456"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.personId").value(personId))
                    .andExpect(jsonPath("$.accountExists").value(true))
                    .andExpect(jsonPath("$.username").value(phone))
                    .andExpect(jsonPath("$.accountEnabled").value(true))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                    .andExpect(jsonPath("$.accountId").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.tokenVersion").doesNotExist());

            Long accountId = jdbcTemplate.queryForObject(
                    "SELECT id FROM tb_user_account WHERE person_id = ?", Long.class, personId);
            assertNotNull(accountId);
            assertEquals(0L, tokenVersion(accountId));
            assertEquals(phone, jdbcTemplate.queryForObject(
                    "SELECT username FROM tb_user_account WHERE id = ?", String.class, accountId));

            String accountHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM tb_user_account WHERE id = ?", String.class, accountId);
            assertTrue(passwordEncoder.matches("123456", accountHash));
            assertEquals(List.of("ROLE_OPERATOR"), accountRoles(accountId));
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @ParameterizedTest
    @MethodSource("ministerialEndpoints")
    void shouldRejectCreateAccessFalseWithPasswordInEveryMinisterialCreationFlow(String endpoint) throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            String adminToken = adminToken(cleanupPersonIds);
            String phone = uniquePhone();

            mockMvc.perform(post(endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Ministerial Invalido",
                                      "phoneNumber": "%s",
                                      "birthdayDate": "%s",
                                      "password": "123456",
                                      "createAccess": false
                                    }
                                    """.formatted(phone, BIRTHDAY)))
                    .andExpect(status().isBadRequest());

            assertEquals(0, countRows("tb_person", "phone_number", phone));
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldRejectMinisterialUpdateWithPasswordField() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            String adminToken = adminToken(cleanupPersonIds);
            String originalPhone = uniquePhone();

            MvcResult createdReader = mockMvc.perform(post("/leitores")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Reader Sem Conta Update",
                                      "phoneNumber": "%s",
                                      "birthdayDate": "%s"
                                    }
                                    """.formatted(originalPhone, BIRTHDAY)))
                    .andExpect(status().isCreated())
                    .andReturn();

            long personId = objectMapper.readTree(createdReader.getResponse().getContentAsString()).get("id").asLong();
            cleanupPersonIds.add(personId);
            String newPhone = uniquePhone();

            mockMvc.perform(put("/leitores/{id}", personId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Reader Alterado",
                                      "phoneNumber": "%s",
                                      "birthdayDate": "%s",
                                      "password": "654321"
                                    }
                                    """.formatted(newPhone, BIRTHDAY)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE"));

            assertEquals(originalPhone, jdbcTemplate.queryForObject(
                    "SELECT phone_number FROM tb_person WHERE id = ?", String.class, personId));
            assertEquals(0, countRows("tb_user_account", "person_id", personId));
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    private static Stream<String> ministerialEndpoints() {
        return Stream.of(
                "/leitores",
                "/comentaristas",
                "/ministrosDaPalavra",
                "/ministrosDeEucaristia",
                "/padres"
        );
    }

    private String adminToken(List<Long> cleanupPersonIds) throws Exception {
        String phone = uniquePhone();
        Person admin = new Person("Lifecycle Admin " + UUID.randomUUID(), phone, BIRTHDAY);
        admin.setActive(true);
        Person savedAdmin = personRepository.saveAndFlush(admin);
        cleanupPersonIds.add(savedAdmin.getId());
        createAccount(savedAdmin, phone, "123456", "ROLE_ADMIN");
        return obtainAccessToken(phone, "123456");
    }

    private void createAccount(Person person, String username, String rawPassword, String authority) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(person, username, passwordEncoder.encode(rawPassword), now, now);
        UserAccount saved = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByAuthority(authority).orElseThrow();
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(saved, role));
    }

    private String obtainAccessToken(String username, String password) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("access_token").asText();
    }

    private long tokenVersion(long accountId) {
        Long tokenVersion = jdbcTemplate.queryForObject(
                "SELECT token_version FROM tb_user_account WHERE id = ?", Long.class, accountId);
        return tokenVersion == null ? -1L : tokenVersion;
    }

    private List<String> accountRoles(long accountId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.authority
                FROM tb_user_account_role uar
                JOIN tb_role r ON r.id = uar.role_id
                WHERE uar.user_account_id = ?
                ORDER BY r.authority
                """,
                String.class,
                accountId
        );
    }

    private int countRows(String tableName, String columnName, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private int countRows(String tableName, String columnName, Object value, String extraCondition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ? AND " + extraCondition,
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private void cleanupPeople(List<Long> personIds) {
        for (Long personId : personIds.reversed()) {
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
            jdbcTemplate.update(
                    "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId
            );
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);            jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
        }
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }
}
