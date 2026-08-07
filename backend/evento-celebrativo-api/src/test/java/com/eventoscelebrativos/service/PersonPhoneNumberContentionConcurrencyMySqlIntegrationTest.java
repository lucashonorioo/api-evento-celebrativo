package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonAdminUpdateRequestDTO;
import com.eventoscelebrativos.exception.exceptions.PersonPhoneNumberConflictException;
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
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com transacoes reais em MySQL 8.4 (nao mocks), o caso de disputa por um telefone NOVO
 * (ainda nao pertencente a ninguem) entre duas pessoas distintas, chamando diretamente o fluxo real
 * de producao PersonServiceImpl.updatePersonAdmin -> PersonCadastralUpdateService.updateCadastral
 * (mesmo padrao de chamada direta ao service, sem MockMvc, usado por
 * UserAccountPhoneSyncConcurrencyMySqlIntegrationTest - chamar via HTTP/MockMvc a partir de threads de
 * um ExecutorService nao funciona aqui porque o SecurityContext de @WithMockUser vive na
 * ThreadLocal da thread de teste e nao e propagado automaticamente para threads do executor).
 * <p>
 * Diferente de UserAccountPhoneSyncConcurrencyMySqlIntegrationTest (que serializa duas atualizacoes
 * na MESMA Person), este teste ataca o risco identificado na revisao de codigo: um
 * {@code SELECT ... FOR UPDATE} sobre um telefone que ainda pode nao pertencer a ninguem toma um gap
 * lock do InnoDB, o que podia gerar deadlock genuino entre as duas transacoes (antes vazando como
 * CannotAcquireLockException nao tratada). Desde a correcao, nem {@code Person.phoneNumber} nem
 * {@code UserAccount.username} sao verificados com lock pessimista sobre o valor novo
 * (PersonRepository.findByPhoneNumber e UserAccountRepository.findByUsername sao consultas simples,
 * usadas apenas para uma checagem amigavel); a garantia final de unicidade fica com as constraints
 * {@code uk_tb_person_phone_number}/{@code uk_tb_user_account_username}, verificadas em flush
 * explicito e traduzidas para {@link PersonPhoneNumberConflictException} (telefone) ou
 * {@code LifecycleConflictException} com codigo {@code USER_ACCOUNT_USERNAME_CONFLICT} (username) -
 * nenhum dos dois depende de deadlock. {@link com.eventoscelebrativos.exception.handler.GlobalExceptionHandler#handlePessimisticLockingFailure}
 * permanece apenas como fallback generico para contencao transitoria de lock nao relacionada a este
 * cenario (coberto isoladamente em GlobalExceptionHandlerTest).
 * <p>
 * Repetido 5 vezes ({@link RepeatedTest}) para reduzir a chance de uma execucao isolada mascarar uma
 * regressao de concorrencia por timing favoravel.
 * <p>
 * Ignorado automaticamente quando MySQL 8.4 real nao estiver acessivel, mesmo padrao das demais
 * classes MySql desta suite.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class PersonPhoneNumberContentionConcurrencyMySqlIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

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

        databaseName = "v14_phone_contention_" + UUID.randomUUID().toString().replace("-", "");
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

    @RepeatedTest(5)
    void shouldLetOnlyOneOfTwoConcurrentAdminUpdatesClaimTheSameNewPhoneNumber() throws Exception {
        String originalPhoneA = uniquePhoneNumber();
        String originalPhoneB = uniquePhoneNumber();
        String contestedPhone = uniquePhoneNumber();
        Long personAId = createPersonWithSyncedAccount(originalPhoneA);
        Long personBId = createPersonWithSyncedAccount(originalPhoneB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> attemptA = executor.submit(
                    () -> runAdminPhoneUpdate(personAId, contestedPhone, readyLatch, startLatch));
            Future<Exception> attemptB = executor.submit(
                    () -> runAdminPhoneUpdate(personBId, contestedPhone, readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Exception exceptionA = attemptA.get(30, TimeUnit.SECONDS);
            Exception exceptionB = attemptB.get(30, TimeUnit.SECONDS);

            boolean succeededA = exceptionA == null;
            boolean succeededB = exceptionB == null;
            assertNotEquals(succeededA, succeededB,
                    "Exatamente uma das duas atualizacoes deve concluir. A=" + exceptionA + " B=" + exceptionB);

            Exception failure = succeededA ? exceptionB : exceptionA;
            assertTrue(failure != null, "A tentativa perdedora deve receber um erro");
            assertFalse(failure instanceof PessimisticLockingFailureException,
                    "A tentativa perdedora nao deve depender de deadlock de gap lock do InnoDB "
                            + "(regressao do lock pessimista sobre telefone/username novos): " + failure);
            assertTrue(
                    failure instanceof PersonPhoneNumberConflictException,
                    "Erro deve ser o conflito semantico e estavel de telefone "
                            + "(PersonPhoneNumberConflictException / 409 PERSON_PHONE_NUMBER_CONFLICT), "
                            + "nao um tipo generico nem uma excecao SQL crua: " + failure
            );
            PersonPhoneNumberConflictException conflict = (PersonPhoneNumberConflictException) failure;
            assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
            assertEquals("PERSON_PHONE_NUMBER_CONFLICT", conflict.getErrorCode());

            assertFinalStateIsConsistent(personAId, originalPhoneA, personBId, originalPhoneB, contestedPhone, succeededA);
        } finally {
            executor.shutdownNow();
            cleanupPerson(personAId);
            cleanupPerson(personBId);
        }
    }

    private Exception runAdminPhoneUpdate(Long personId, String newPhone, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            personService.updatePersonAdmin(personId, new PersonAdminUpdateRequestDTO(
                    "Contention Person " + personId, newPhone, BIRTHDAY));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void assertFinalStateIsConsistent(
            Long personAId, String originalPhoneA,
            Long personBId, String originalPhoneB,
            String contestedPhone, boolean winnerIsA
    ) {
        Person personA = personRepository.findById(personAId).orElseThrow();
        Person personB = personRepository.findById(personBId).orElseThrow();
        UserAccount accountA = userAccountRepository.findByPersonId(personAId).orElseThrow();
        UserAccount accountB = userAccountRepository.findByPersonId(personBId).orElseThrow();

        assertNotEquals(personA.getPhoneNumber(), personB.getPhoneNumber(),
                "Duas pessoas nao podem terminar com o mesmo telefone");
        assertEquals(personA.getPhoneNumber(), accountA.getUsername());
        assertEquals(personB.getPhoneNumber(), accountB.getUsername());

        if (winnerIsA) {
            assertEquals(contestedPhone, personA.getPhoneNumber());
            assertEquals(1L, accountA.getTokenVersion());
            assertEquals(originalPhoneB, personB.getPhoneNumber());
            assertEquals(0L, accountB.getTokenVersion());
        } else {
            assertEquals(contestedPhone, personB.getPhoneNumber());
            assertEquals(1L, accountB.getTokenVersion());
            assertEquals(originalPhoneA, personA.getPhoneNumber());
            assertEquals(0L, accountA.getTokenVersion());
        }

        long personsWithContestedPhone = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person WHERE phone_number = ?", Long.class, contestedPhone);
        assertEquals(1L, personsWithContestedPhone);
        Long accountsWithContestedUsername = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user_account WHERE username = ?", Long.class, contestedPhone);
        assertEquals(1L, accountsWithContestedUsername);

        Long accountsForBothPersons = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user_account WHERE person_id IN (?, ?)", Long.class, personAId, personBId);
        assertEquals(2L, accountsForBothPersons, "Nenhuma conta deve ser criada ou removida pela disputa de telefone");

        assertEquals(List.of("ROLE_OPERATOR"), sortedRoleAuthorities(accountA.getId()));
        assertEquals(List.of("ROLE_OPERATOR"), sortedRoleAuthorities(accountB.getId()));
    }

    private List<String> sortedRoleAuthorities(Long accountId) {
        return userAccountRoleRepository.findByUserAccountId(accountId).stream()
                .map(userAccountRole -> userAccountRole.getRole().getAuthority())
                .sorted()
                .toList();
    }

    private Long createPersonWithSyncedAccount(String phoneNumber) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            person.setName("Contention Person");
            person.setPhoneNumber(phoneNumber);
            person.setBirthdayDate(BIRTHDAY);
            Person saved = personRepository.save(person);
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
            jdbcTemplate.update("DELETE FROM tb_user_account_role WHERE user_account_id IN "
                    + "(SELECT id FROM tb_user_account WHERE person_id = ?)", personId);
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
            personRepository.deleteById(personId);
        });
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }
}
