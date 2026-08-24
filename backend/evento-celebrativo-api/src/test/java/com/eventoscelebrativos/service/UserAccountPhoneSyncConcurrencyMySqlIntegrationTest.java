package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com transacoes reais em MySQL 8.4 real (nao mocks), que duas atualizacoes concorrentes de
 * telefone na mesma Person - cada uma passando pelo fluxo real de producao ReaderServiceImpl.updateReader,
 * que agora bloqueia Person via findByIdForUpdate antes do mapper e so entao bloqueia UserAccount via
 * findByPersonIdForUpdate - sao serializadas sem deadlock nao tratado e terminam com
 * Person.phoneNumber == UserAccount.username, uma unica UserAccount, username unico, senha e roles
 * equivalentes e enabled preservado.
 * <p>
 * Usa uma database MySQL isolada e propria (@DynamicPropertySource), nunca a compartilhada pelo
 * restante da suite, e e' ignorado automaticamente quando MySQL nao estiver acessivel (mesmas
 * propriedades documentadas em UserAccountRoleSyncConcurrencyMySqlIntegrationTest), para que
 * `mvnw test` continue verde sem Docker.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class UserAccountPhoneSyncConcurrencyMySqlIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private ReaderService readerService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void provisionIsolatedDatabase() throws SQLException {
        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty("mysql.validation.password", System.getenv("MYSQL_VALIDATION_PASSWORD"));

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password)) {
            mysqlAvailable = connection.isValid(3);
        } catch (SQLException e) {
            mysqlAvailable = false;
            return;
        }

        databaseName = "v14_phone_sync_concurrency_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        if (!mysqlAvailable) {
            return;
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    @AfterAll
    static void dropIsolatedDatabase() {
        if (!mysqlAvailable || databaseName == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @Test
    void shouldUseDatetimeZeroPrecisionColumnsOnMySql() {
        Map<String, Object> createdAtColumn = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_account' AND COLUMN_NAME = 'created_at'");
        assertEquals("datetime", String.valueOf(createdAtColumn.get("DATA_TYPE")));
        assertEquals(0, ((Number) createdAtColumn.get("DATETIME_PRECISION")).intValue());

        Map<String, Object> updatedAtColumn = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_account' AND COLUMN_NAME = 'updated_at'");
        assertEquals("datetime", String.valueOf(updatedAtColumn.get("DATA_TYPE")));
        assertEquals(0, ((Number) updatedAtColumn.get("DATETIME_PRECISION")).intValue());
    }

    @Test
    void shouldSerializeConcurrentPhoneUpdatesOnTheSamePersonWithoutDivergence() throws Exception {
        Long personId = createReaderPersonWithSyncedAccount();
        String phoneA = uniquePhoneNumber();
        String phoneB = uniquePhoneNumber();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> attemptA = executor.submit(
                    () -> runPhoneUpdate(personId, phoneA, readyLatch, startLatch));
            Future<Exception> attemptB = executor.submit(
                    () -> runPhoneUpdate(personId, phoneB, readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Exception exceptionA = attemptA.get(30, TimeUnit.SECONDS);
            Exception exceptionB = attemptB.get(30, TimeUnit.SECONDS);

            assertTrue(exceptionA == null, "Atualizacao concorrente de telefone falhou: " + exceptionA);
            assertTrue(exceptionB == null, "Atualizacao concorrente de telefone falhou: " + exceptionB);

            assertFinalStateIsConsistent(personId, Set.of(phoneA, phoneB));
        } finally {
            executor.shutdownNow();
            cleanupPerson(personId);
        }
    }

    private Exception runPhoneUpdate(Long personId, String newPhone, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            readerService.updateReader(personId, new ReaderUpdateRequestDTO("Concurrent Phone Reader", newPhone, BIRTHDAY));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void assertFinalStateIsConsistent(Long personId, Set<String> acceptablePhones) {
        Person person = personRepository.findById(personId).orElseThrow();
        UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
        List<UserAccountRole> accountRoles = userAccountRoleRepository.findByUserAccountId(account.getId());

        assertTrue(acceptablePhones.contains(person.getPhoneNumber()));
        assertEquals(person.getPhoneNumber(), account.getUsername());
        assertEquals(1, userAccountRepository.findByPersonId(personId).stream().count());
        assertTrue(account.isEnabled());

        Set<String> accountRoleAuthorities = accountRoles.stream()
                .map(UserAccountRole::getRole).map(Role::getAuthority).collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_OPERATOR"), accountRoleAuthorities);
    }

    private Long createReaderPersonWithSyncedAccount() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person("Concurrent Phone Reader", uniquePhoneNumber(), BIRTHDAY);
            Person saved = personRepository.save(person);
            personMinistryRepository.save(new PersonMinistry(saved, MinistryType.READER));
            Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
            LocalDateTime now = LocalDateTime.now().withNano(0);
            UserAccount account = userAccountRepository.save(
                    new UserAccount(saved, saved.getPhoneNumber(), "encoded-password", now, now));
            userAccountRoleRepository.save(new UserAccountRole(account, operatorRole));
            return saved.getId();
        });
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_user_account_role WHERE user_account_id IN "
                    + "(SELECT id FROM tb_user_account WHERE person_id = ?)", personId);
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
            personRepository.deleteById(personId);
        });
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }
}
