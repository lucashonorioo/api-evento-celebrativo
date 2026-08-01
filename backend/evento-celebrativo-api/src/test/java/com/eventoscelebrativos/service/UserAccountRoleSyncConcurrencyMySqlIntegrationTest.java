package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
 * Prova, com transacoes reais em MySQL 8.4 real (nao mocks), que duas operacoes concorrentes de
 * troca de role na mesma Person - cada uma passando pelo lock pessimista real definido na secao 21
 * (Person via findByIdWithRolesForUpdate, depois UserAccount via findByPersonIdForUpdate) - sao
 * serializadas sem deadlock nao tratado e terminam com Person.roles == UserAccount.roles, uma
 * unica UserAccount, nenhuma role duplicada e nenhum rollback parcial.
 * <p>
 * Usa uma database MySQL isolada propria (@DynamicPropertySource), nunca a compartilhada pelo
 * restante da suite, e e' ignorado automaticamente quando MySQL nao estiver acessivel (mesmas
 * propriedades documentadas em PersonUnavailabilityIntervalMatrixMySqlIntegrationTest), para que
 * `mvnw test` continue verde sem Docker.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class UserAccountRoleSyncConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private PersonService personService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private UserAccountSynchronizationService userAccountSynchronizationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        databaseName = "v14_role_sync_concurrency_" + UUID.randomUUID().toString().replace("-", "");
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
    void shouldSerializeConcurrentRoleUpdatesOnTheSamePersonWithoutDivergence() throws Exception {
        // Segundo administrador de ancora: garante que nenhuma das duas trocas concorrentes de
        // role seja bloqueada pela protecao de ultimo administrador, independentemente da ordem
        // de execucao real dos dois threads.
        Long anchorAdminId = createAdminPersonWithSyncedAccount();
        Long personId = createOperatorPersonWithSyncedAccount();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> adminAttempt = executor.submit(
                    () -> runRoleUpdate(personId, "ROLE_ADMIN", readyLatch, startLatch));
            Future<Exception> operatorAttempt = executor.submit(
                    () -> runRoleUpdate(personId, "ROLE_OPERATOR", readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Exception adminException = adminAttempt.get(30, TimeUnit.SECONDS);
            Exception operatorException = operatorAttempt.get(30, TimeUnit.SECONDS);

            assertTrue(adminException == null, "Atualizacao concorrente de role falhou: " + adminException);
            assertTrue(operatorException == null, "Atualizacao concorrente de role falhou: " + operatorException);

            assertFinalStateIsConsistent(personId);
        } finally {
            executor.shutdownNow();
            cleanupPerson(personId);
            cleanupPerson(anchorAdminId);
        }
    }

    private Exception runRoleUpdate(Long personId, String targetRole, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            personService.updatePersonRole(personId, new PersonRoleUpdateRequestDTO(targetRole));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void assertFinalStateIsConsistent(Long personId) {
        Person person = personRepository.findByIdWithRoles(personId).orElseThrow();
        UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
        List<UserAccountRole> accountRoles = userAccountRoleRepository.findByUserAccountId(account.getId());

        Set<String> personRoleAuthorities = person.getRoles().stream().map(Role::getAuthority).collect(Collectors.toSet());
        Set<String> accountRoleAuthorities = accountRoles.stream()
                .map(UserAccountRole::getRole).map(Role::getAuthority).collect(Collectors.toSet());

        assertEquals(1, personRoleAuthorities.size());
        assertEquals(personRoleAuthorities, accountRoleAuthorities);
        assertEquals(1, accountRoles.size());
        assertTrue(account.isEnabled());
        assertEquals(1, userAccountRepository.findByPersonId(personId).stream().count());
        assertEquals(account.getUsername(), person.getPhoneNumber());
        assertEquals(account.getPasswordHash(), person.getPassword());
    }

    private Long createOperatorPersonWithSyncedAccount() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            person.setName("Concurrency Role Person");
            person.setPhoneNumber(uniquePhoneNumber());
            person.setPassword("encoded-password");
            person.addRole(roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow());
            Person saved = personRepository.save(person);
            userAccountSynchronizationService.synchronizeNewPerson(saved);
            return saved.getId();
        });
    }

    private Long createAdminPersonWithSyncedAccount() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            person.setName("Concurrency Anchor Admin");
            person.setPhoneNumber(uniquePhoneNumber());
            person.setPassword("encoded-password");
            person.addRole(roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow());
            Person saved = personRepository.save(person);
            userAccountSynchronizationService.synchronizeNewPerson(saved);
            return saved.getId();
        });
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> personRepository.deleteById(personId));
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }
}
