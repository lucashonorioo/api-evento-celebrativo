package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.normalizedName;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Prova, pelos cinco endpoints ministeriais reais, a matriz de criacao de acesso (createAccess x
 * senha x accessRole) e o comportamento pos-remocao do login legado: updates ministeriais aceitam
 * somente dados cadastrais e rejeitam a PRESENCA de password/createAccess/accessRole no JSON
 * (mesmo com valor null/vazio/false) com ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE, sem tocar em
 * PasswordEncoder; telefone continua sincronizado para UserAccount.username com tokenVersion
 * incrementado uma unica vez. Person nao carrega mais password nem roles - todo o estado de acesso
 * e verificado exclusivamente via UserAccount/UserAccountRole.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class MinisterialAccessLifecycleIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final LocalDate UPDATED_BIRTHDAY = LocalDate.of(1991, 2, 11);
    private static final String CURRENT_PASSWORD = "123456";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private final List<String> cleanupPhones = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String phone : cleanupPhones.reversed()) {
            List<Long> personIds = jdbcTemplate.queryForList(
                    """
                    SELECT id FROM tb_person WHERE phone_number = ?
                    UNION
                    SELECT person_id FROM tb_user_account WHERE username = ?
                    """,
                    Long.class,
                    phone,
                    phone
            );
            for (Long personId : personIds) {
                cleanupPerson(personId);
            }
            jdbcTemplate.update(
                    "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE username = ?)",
                    phone
            );
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE username = ?", phone);
        }
        cleanupPhones.clear();
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("creationMatrix")
    @WithMockUser(roles = "ADMIN")
    void shouldApplyCreationAccessMatrixForEveryMinisterialEndpoint(
            MinisterialEndpoint endpoint,
            CreationScenario scenario
    ) throws Exception {
        String phone = uniquePhone();
        if (scenario.rollbackByUsernameConflict()) {
            createConflictingAccountUsername(phone);
        }

        MvcResult result = mockMvc.perform(post(endpoint.path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationPayload(
                                "Create " + endpoint.label(),
                                phone,
                                scenario.password(),
                                scenario.createAccess(),
                                scenario.accessRole()
                        )))
                .andReturn();

        assertEquals(
                scenario.expectedStatus(),
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString()
        );

        if (scenario.expectedStatus() == 201) {
            long personId = readId(result);
            assertCreationState(
                    personId,
                    endpoint.ministryType(),
                    scenario.expectedAccount(),
                    scenario.expectedRole(),
                    scenario.password()
            );
        } else {
            assertEquals(0, countRows("tb_person", "phone_number", phone));
            if (scenario.rollbackByUsernameConflict()) {
                assertEquals(1, countRows("tb_user_account", "username", phone));
            } else {
                assertEquals(0, countRows("tb_user_account", "username", phone));
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1}={2}")
    @MethodSource("forbiddenUpdateFieldMatrix")
    @WithMockUser(roles = "ADMIN")
    void shouldRejectAccountFieldOnMinisterialUpdateRegardlessOfValue(
            MinisterialEndpoint endpoint,
            String fieldName,
            String rawJsonValue
    ) throws Exception {
        Person person = createMinisterialPerson(endpoint, uniquePhone(), false, null, null);
        String originalPhone = person.getPhoneNumber();
        clearInvocations(passwordEncoder);

        String payload = updatePayloadWithRawField("Should Reject", uniquePhone(), UPDATED_BIRTHDAY, fieldName, rawJsonValue);

        MvcResult result = mockMvc.perform(put(endpoint.updatePath(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(
                "ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText()
        );
        assertPersonUnchangedWithoutAccount(person.getId(), originalPhone, "Person " + endpoint.label());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ministerialEndpoints")
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateMinisterialPersonWithoutAccountWhenNoAccountFieldsPresent(MinisterialEndpoint endpoint) throws Exception {
        Person person = createMinisterialPerson(endpoint, uniquePhone(), false, null, null);
        String newPhone = uniquePhone();
        clearInvocations(passwordEncoder);

        MvcResult result = mockMvc.perform(put(endpoint.updatePath(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastralUpdatePayload("No Account Updated", newPhone, UPDATED_BIRTHDAY)))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertPersonWithoutAccount(person.getId(), newPhone, "No Account Updated", UPDATED_BIRTHDAY, endpoint.ministryType());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ministerialEndpoints")
    @WithMockUser(roles = "ADMIN")
    void shouldSynchronizePhoneChangeAndIncrementVersionOnceOnMinisterialUpdate(MinisterialEndpoint endpoint) throws Exception {
        Person person = createMinisterialPerson(endpoint, uniquePhone(), true, CURRENT_PASSWORD, "ROLE_OPERATOR");
        long accountId = accountIdByPersonId(person.getId());
        String oldHash = accountHash(accountId);
        String newPhone = uniquePhone();
        clearInvocations(passwordEncoder);

        MvcResult result = mockMvc.perform(put(endpoint.updatePath(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastralUpdatePayload("Phone Updated", newPhone, BIRTHDAY)))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(newPhone, personPhone(person.getId()));
        assertEquals(newPhone, accountUsername(accountId));
        assertEquals(oldHash, accountHash(accountId));
        assertEquals(Set.of("ROLE_OPERATOR"), accountRoleAuthorities(accountId));
        assertEquals(1L, tokenVersion(accountId));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "ADMIN")
    void shouldNotIncrementVersionWhenOnlyMinistriesChange() throws Exception {
        MinisterialEndpoint endpoint = new MinisterialEndpoint("leitor", "/leitores", MinistryType.READER);
        Person person = createMinisterialPerson(endpoint, uniquePhone(), true, CURRENT_PASSWORD, "ROLE_OPERATOR");
        long accountId = accountIdByPersonId(person.getId());

        MvcResult result = mockMvc.perform(put("/pessoas/{id}/ministries", person.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministryIds": [%d, %d]
                                }
                                """.formatted(ministryId(MinistryType.READER), ministryId(MinistryType.COMMENTATOR))))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(0L, tokenVersion(accountId));
        assertEquals(1, activeMinistryCount(person.getId(), MinistryType.READER));
        assertEquals(1, activeMinistryCount(person.getId(), MinistryType.COMMENTATOR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ministerialEndpoints")
    @WithMockUser(roles = "ADMIN")
    void shouldNotIncrementVersionWhenOnlyNameOrBirthdayChangesOnMinisterialUpdate(MinisterialEndpoint endpoint) throws Exception {
        Person person = createMinisterialPerson(endpoint, uniquePhone(), true, CURRENT_PASSWORD, "ROLE_OPERATOR");
        long accountId = accountIdByPersonId(person.getId());
        String originalPhone = person.getPhoneNumber();
        String originalHash = accountHash(accountId);
        clearInvocations(passwordEncoder);

        MvcResult result = mockMvc.perform(put(endpoint.updatePath(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastralUpdatePayload("Only Name Birthday Updated", originalPhone, UPDATED_BIRTHDAY)))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("Only Name Birthday Updated", personName(person.getId()));
        assertEquals(UPDATED_BIRTHDAY, personBirthday(person.getId()));
        assertEquals(originalPhone, accountUsername(accountId));
        assertEquals(originalHash, accountHash(accountId));
        assertEquals(0L, tokenVersion(accountId));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ministerialEndpoints")
    @WithMockUser(roles = "ADMIN")
    void shouldRollbackPersonAndMinistryWhenMinisterialSynchronizationFails(MinisterialEndpoint endpoint) throws Exception {
        Person person = createMinisterialPerson(endpoint, uniquePhone(), true, CURRENT_PASSWORD, "ROLE_OPERATOR");
        long accountId = accountIdByPersonId(person.getId());
        String originalPhone = person.getPhoneNumber();
        String conflictingUsername = uniquePhone();
        createConflictingAccountUsername(conflictingUsername);

        MvcResult result = mockMvc.perform(put(endpoint.updatePath(person.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastralUpdatePayload("Should Rollback Sync", conflictingUsername, UPDATED_BIRTHDAY)))
                .andReturn();

        assertEquals(409, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals("USER_ACCOUNT_USERNAME_CONFLICT", objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText());
        assertEquals(originalPhone, personPhone(person.getId()));
        assertEquals(originalPhone, accountUsername(accountId));
        assertEquals("Person " + endpoint.label(), personName(person.getId()));
        assertEquals(BIRTHDAY, personBirthday(person.getId()));
        assertEquals(1, activeMinistryCount(person.getId(), endpoint.ministryType()));
        assertEquals(1L, countRows("tb_person_ministry", "person_id", person.getId()));
        assertEquals(0L, tokenVersion(accountId));
    }

    private static Stream<Arguments> creationMatrix() {
        List<CreationScenario> scenarios = List.of(
                new CreationScenario("createAccess=true + senha valida", true, CURRENT_PASSWORD, null, 201, true, "ROLE_OPERATOR", false),
                new CreationScenario("createAccess=true sem senha", true, null, null, 400, false, null, false),
                new CreationScenario("createAccess=false sem senha", false, null, null, 201, false, null, false),
                new CreationScenario("createAccess=false com senha", false, CURRENT_PASSWORD, null, 400, false, null, false),
                new CreationScenario("createAccess=false com accessRole", false, null, "ROLE_OPERATOR", 400, false, null, false),
                new CreationScenario("createAccess ausente + senha", null, CURRENT_PASSWORD, null, 201, true, "ROLE_OPERATOR", false),
                new CreationScenario("createAccess ausente sem senha", null, null, null, 201, false, null, false),
                new CreationScenario("accessRole presente sem senha", null, null, "ROLE_OPERATOR", 400, false, null, false),
                new CreationScenario("ROLE_OPERATOR explicita", true, CURRENT_PASSWORD, "ROLE_OPERATOR", 201, true, "ROLE_OPERATOR", false),
                new CreationScenario("ROLE_ADMIN explicita", true, CURRENT_PASSWORD, "ROLE_ADMIN", 201, true, "ROLE_ADMIN", false),
                new CreationScenario("role invalida", true, CURRENT_PASSWORD, "ROLE_UNKNOWN", 400, false, null, false),
                new CreationScenario("senha vazia", true, "", null, 400, false, null, false),
                new CreationScenario("senha somente com espacos", true, "   ", null, 400, false, null, false),
                new CreationScenario("rollback integral", true, CURRENT_PASSWORD, "ROLE_OPERATOR", 409, false, null, true)
        );
        return ministerialEndpoints()
                .flatMap(endpoint -> scenarios.stream().map(scenario -> Arguments.of(endpoint, scenario)));
    }

    private static Stream<Arguments> forbiddenUpdateFieldMatrix() {
        List<Arguments> fieldScenarios = List.of(
                Arguments.of("password", "\"654321\""),
                Arguments.of("password", "\"\""),
                Arguments.of("password", "\"   \""),
                Arguments.of("password", "null"),
                Arguments.of("createAccess", "true"),
                Arguments.of("createAccess", "false"),
                Arguments.of("createAccess", "null"),
                Arguments.of("accessRole", "\"ROLE_ADMIN\""),
                Arguments.of("accessRole", "\"\""),
                Arguments.of("accessRole", "null")
        );
        return ministerialEndpoints().flatMap(endpoint -> fieldScenarios.stream()
                .map(scenario -> Arguments.of(endpoint, scenario.get()[0], scenario.get()[1])));
    }

    private static Stream<MinisterialEndpoint> ministerialEndpoints() {
        return Stream.of(
                new MinisterialEndpoint("padre", "/padres", MinistryType.PRIEST),
                new MinisterialEndpoint("leitor", "/leitores", MinistryType.READER),
                new MinisterialEndpoint("comentarista", "/comentaristas", MinistryType.COMMENTATOR),
                new MinisterialEndpoint("ministro da Palavra", "/ministrosDaPalavra", MinistryType.MINISTER_OF_THE_WORD),
                new MinisterialEndpoint("ministro da Eucaristia", "/ministrosDeEucaristia", MinistryType.EUCHARISTIC_MINISTER)
        );
    }

    private String creationPayload(String name, String phone, String password, Boolean createAccess, String accessRole) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("phoneNumber", phone);
        payload.put("birthdayDate", BIRTHDAY.toString());
        if (password != null) {
            payload.put("password", password);
        }
        if (createAccess != null) {
            payload.put("createAccess", createAccess);
        }
        if (accessRole != null) {
            payload.put("accessRole", accessRole);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String cadastralUpdatePayload(String name, String phone, LocalDate birthday) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("phoneNumber", phone);
        payload.put("birthdayDate", birthday.toString());
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Monta o JSON manualmente (nao via serializacao de Map) para poder incluir uma chave com
     * valor JSON literal `null`, algo que a serializacao padrao de um Map com valor Java null nao
     * garante representar da mesma forma em todo ObjectMapper.
     */
    private String updatePayloadWithRawField(String name, String phone, LocalDate birthday, String fieldName, String rawJsonValue) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "%s",
                  "%s": %s
                }
                """.formatted(name, phone, birthday, fieldName, rawJsonValue);
    }

    private Person createMinisterialPerson(
            MinisterialEndpoint endpoint,
            String phone,
            boolean withAccount,
            String rawPassword,
            String authority
    ) {
        Person person = new Person("Person " + endpoint.label(), phone, BIRTHDAY);
        person.activate();
        person = personRepository.saveAndFlush(person);
        if (withAccount) {
            Role role = roleRepository.findByAuthority(authority).orElseThrow();
            String hash = passwordEncoder.encode(rawPassword);
            UserAccount account = new UserAccount(person, phone, hash, currentSecond(), currentSecond());
            UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
            userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));
        }
        personMinistryRepository.saveAndFlush(personMinistry(person, endpoint.ministryType(), ministryRepository));
        return person;
    }

    private Long ministryId(MinistryType ministryType) {
        return ministryRepository.findByNormalizedName(normalizedName(ministryType))
                .orElseThrow()
                .getId();
    }

    private void createConflictingAccountUsername(String username) {
        String anchorPhone = uniquePhone();
        Person anchor = new Person("Conflict Anchor", anchorPhone, BIRTHDAY);
        anchor.activate();
        anchor = personRepository.saveAndFlush(anchor);
        Role operator = roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
        String hash = passwordEncoder.encode(CURRENT_PASSWORD);
        UserAccount account = new UserAccount(anchor, username, hash, currentSecond(), currentSecond());
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, operator));
    }

    private void assertCreationState(
            long personId,
            MinistryType ministryType,
            boolean expectedAccount,
            String expectedRole,
            String rawPassword
    ) {
        assertEquals(1, countRows("tb_person", "id", personId));
        assertEquals(1, activeMinistryCount(personId, ministryType));
        if (!expectedAccount) {
            assertEquals(0, countRows("tb_user_account", "person_id", personId));
            return;
        }

        long accountId = accountIdByPersonId(personId);
        String accountHash = accountHash(accountId);
        assertNotNull(accountHash);
        assertTrue(passwordEncoder.matches(rawPassword, accountHash));
        assertEquals(personPhone(personId), accountUsername(accountId));
        assertTrue(accountEnabled(accountId));
        assertEquals(0L, tokenVersion(accountId));
        assertEquals(Set.of(expectedRole), accountRoleAuthorities(accountId));
    }

    private void assertPersonUnchangedWithoutAccount(long personId, String originalPhone, String originalName) {
        assertPersonWithoutAccount(personId, originalPhone, originalName, BIRTHDAY, null);
    }

    private void assertPersonWithoutAccount(
            long personId,
            String phone,
            String name,
            LocalDate birthday,
            MinistryType ministryType
    ) {
        assertEquals(phone, personPhone(personId));
        assertEquals(name, personName(personId));
        assertEquals(birthday, personBirthday(personId));
        assertEquals(0, countRows("tb_user_account", "person_id", personId));
        if (ministryType != null) {
            assertEquals(1, activeMinistryCount(personId, ministryType));
        }
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private long accountIdByPersonId(long personId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_user_account WHERE person_id = ?",
                Long.class,
                personId
        );
        return id == null ? -1L : id;
    }

    private String personPhone(long personId) {
        return jdbcTemplate.queryForObject("SELECT phone_number FROM tb_person WHERE id = ?", String.class, personId);
    }

    private String personName(long personId) {
        return jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId);
    }

    private LocalDate personBirthday(long personId) {
        return jdbcTemplate.queryForObject("SELECT birthday_date FROM tb_person WHERE id = ?", LocalDate.class, personId);
    }

    private String accountUsername(long accountId) {
        return jdbcTemplate.queryForObject("SELECT username FROM tb_user_account WHERE id = ?", String.class, accountId);
    }

    private String accountHash(long accountId) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM tb_user_account WHERE id = ?", String.class, accountId);
    }

    private boolean accountEnabled(long accountId) {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM tb_user_account WHERE id = ?", Boolean.class, accountId);
        return Boolean.TRUE.equals(enabled);
    }

    private long tokenVersion(long accountId) {
        Long tokenVersion = jdbcTemplate.queryForObject("SELECT token_version FROM tb_user_account WHERE id = ?", Long.class, accountId);
        return tokenVersion == null ? -1L : tokenVersion;
    }

    private Set<String> accountRoleAuthorities(long accountId) {
        return userAccountRoleRepository.findByUserAccountId(accountId).stream()
                .map(UserAccountRole::getRole)
                .map(Role::getAuthority)
                .collect(Collectors.toSet());
    }

    private int activeMinistryCount(long personId, MinistryType ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ? AND ministry_type = ? AND active = TRUE",
                Integer.class,
                personId,
                ministryType.name()
        );
        return count == null ? 0 : count;
    }

    private long countRows(String tableName, String columnName, Object value) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Long.class,
                value
        );
        return count == null ? 0L : count;
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update(
                "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                personId
        );
        jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private LocalDateTime currentSecond() {
        return LocalDateTime.now().withNano(0);
    }

    private String uniquePhone() {
        String phone = "3495" + String.format("%07d", Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000));
        cleanupPhones.add(phone);
        return phone;
    }

    private record MinisterialEndpoint(String label, String path, MinistryType ministryType) {

        private String updatePath(long id) {
            return path + "/" + id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record CreationScenario(
            String label,
            Boolean createAccess,
            String password,
            String accessRole,
            int expectedStatus,
            boolean expectedAccount,
            String expectedRole,
            boolean rollbackByUsernameConflict
    ) {

        @Override
        public String toString() {
            return label;
        }
    }
}
