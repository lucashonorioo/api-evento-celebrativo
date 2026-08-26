package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.NotificationInvalidRecipientsException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
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
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com transacoes reais em MySQL 8.4 (nao mocks), a semantica concorrente do envio e leitura
 * de notificacoes definida na secao 14/17: locks pessimistas reais (Person -> UserAccount ->
 * PersonMinistry), update condicional de leitura e o unique de destinatario. Usa uma database MySQL
 * isolada propria (@DynamicPropertySource) e e ignorado automaticamente quando MySQL 8.4 real nao
 * estiver acessivel, para que {@code mvnw test} continue verde sem Docker.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class NotificationConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private NotificationInboxService notificationInboxService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        databaseName = "v16_notification_concurrency_" + UUID.randomUUID().toString().replace("-", "");
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
    void shouldResolveSendVersusAccountDisableWithoutHybridState() throws Exception {
        Fixture target = createOperator("Concurrency Disable Target");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Outcome> sendFuture = executor.submit(() -> runDirectSend(target.personId(), readyLatch, startLatch));
            Future<Exception> disableFuture = executor.submit(() ->
                    runDisableAccount(target.personId(), target.accountId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Outcome sendOutcome = sendFuture.get(30, TimeUnit.SECONDS);
            Exception disableException = disableFuture.get(30, TimeUnit.SECONDS);

            assertNull(disableException, "Desabilitacao concorrente nao deveria falhar");
            assertTrue(sendOutcome.error() == null || sendOutcome.error() instanceof NotificationInvalidRecipientsException,
                    "Envio concorrente falhou de forma inesperada: " + sendOutcome.error());

            boolean accountNowDisabled = !userAccountRepository.findById(target.accountId()).orElseThrow().isEnabled();
            assertTrue(accountNowDisabled, "A desabilitacao deve sempre ter sucesso, independentemente da ordem");

            if (sendOutcome.error() == null) {
                assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(
                        sendOutcome.notificationId(), target.accountId()),
                        "Envio que venceu o lock deve ter incluido o destinatario com o estado anterior");
            } else {
                assertEquals(0, notificationRepository.count(), "DIRECT invalido deve deixar zero linhas parciais");
            }
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
        }
    }

    @RepeatedTest(5)
    void shouldResolveSendVersusPersonDeactivationWithoutHybridState() throws Exception {
        Fixture target = createOperator("Concurrency Deactivate Target");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Outcome> sendFuture = executor.submit(() -> runDirectSend(target.personId(), readyLatch, startLatch));
            Future<Exception> deactivateFuture = executor.submit(() -> runDeactivatePerson(target.personId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Outcome sendOutcome = sendFuture.get(30, TimeUnit.SECONDS);
            Exception deactivateException = deactivateFuture.get(30, TimeUnit.SECONDS);

            assertNull(deactivateException, "Desativacao concorrente nao deveria falhar");
            assertTrue(sendOutcome.error() == null || sendOutcome.error() instanceof NotificationInvalidRecipientsException,
                    "Envio concorrente falhou de forma inesperada: " + sendOutcome.error());

            boolean personNowInactive = !personRepository.findById(target.personId()).orElseThrow().isActive();
            assertTrue(personNowInactive, "A desativacao deve sempre ter sucesso, independentemente da ordem");
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
        }
    }

    @RepeatedTest(5)
    void shouldRevalidateMinistryMembershipConcurrentlyWithoutDuplicateOrCorruptedRecipients() throws Exception {
        Fixture target = createOperatorWithMinistry("Concurrency Ministry Target", MinistryType.READER);
        Fixture anchor = createOperatorWithMinistry("Concurrency Ministry Anchor", MinistryType.READER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Outcome> sendFuture = executor.submit(() ->
                    runMinistrySend(List.of(MinistryType.READER), readyLatch, startLatch));
            Future<Exception> deactivateFuture = executor.submit(() ->
                    runDeactivatePersonMinistry(target.personId(), MinistryType.READER, readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Outcome sendOutcome = sendFuture.get(30, TimeUnit.SECONDS);
            Exception deactivateException = deactivateFuture.get(30, TimeUnit.SECONDS);

            assertNull(deactivateException);
            assertNull(sendOutcome.error(), "O ancora garante cobertura minima independentemente da ordem: " + sendOutcome.error());

            long targetRows = notificationRecipientRepository
                    .findByNotificationIdAndUserAccountId(sendOutcome.notificationId(), target.accountId())
                    .stream().count();
            assertTrue(targetRows <= 1, "Nunca mais de uma linha para o mesmo destinatario");
            assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(
                    sendOutcome.notificationId(), anchor.accountId()));
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
            cleanupPerson(anchor.personId());
        }
    }

    @RepeatedTest(5)
    void shouldDeduplicateOverlappingMinistryRecipientEvenWhenSecondMinistryActivatesConcurrently() throws Exception {
        Fixture target = createOperatorWithMinistry("Concurrency Overlap Target", MinistryType.READER);
        Fixture readerAnchor = createOperatorWithMinistry("Concurrency Overlap Reader Anchor", MinistryType.READER);
        Fixture eucharisticAnchor = createOperatorWithMinistry("Concurrency Overlap Eucharistic Anchor", MinistryType.EUCHARISTIC_MINISTER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Outcome> sendFuture = executor.submit(() ->
                    runMinistrySend(List.of(MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER), readyLatch, startLatch));
            Future<Exception> activateFuture = executor.submit(() ->
                    runActivatePersonMinistry(target.personId(), MinistryType.EUCHARISTIC_MINISTER, readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Outcome sendOutcome = sendFuture.get(30, TimeUnit.SECONDS);
            Exception activateException = activateFuture.get(30, TimeUnit.SECONDS);

            assertNull(activateException);
            assertNull(sendOutcome.error());

            long targetRows = notificationRecipientRepository
                    .findByNotificationIdAndUserAccountId(sendOutcome.notificationId(), target.accountId())
                    .stream().count();
            assertEquals(1, targetRows, "Mesmo com sobreposicao concorrente, deve haver exatamente uma linha para o destinatario");
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
            cleanupPerson(readerAnchor.personId());
            cleanupPerson(eucharisticAnchor.personId());
        }
    }

    @RepeatedTest(5)
    void shouldPreserveFirstReadAtUnderTwoConcurrentIndividualReads() throws Exception {
        Fixture target = createOperator("Concurrency Two Reads Target");
        Long notificationId = sendDirectSync(target.personId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> firstRead = executor.submit(() -> runMarkAsRead(target.accountId(), notificationId, readyLatch, startLatch));
            Future<Exception> secondRead = executor.submit(() -> runMarkAsRead(target.accountId(), notificationId, readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            assertNull(firstRead.get(30, TimeUnit.SECONDS));
            assertNull(secondRead.get(30, TimeUnit.SECONDS));

            assertNotNull(notificationRecipientRepository
                    .findByNotificationIdAndUserAccountId(notificationId, target.accountId())
                    .orElseThrow()
                    .getReadAt());
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
        }
    }

    @RepeatedTest(5)
    void shouldResolveIndividualReadVersusMarkAllAsReadConsistently() throws Exception {
        Fixture target = createOperator("Concurrency Read Vs All Target");
        Long notificationId = sendDirectSync(target.personId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> individualRead = executor.submit(() -> runMarkAsRead(target.accountId(), notificationId, readyLatch, startLatch));
            Future<Exception> markAll = executor.submit(() -> runMarkAllAsRead(target.accountId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            assertNull(individualRead.get(30, TimeUnit.SECONDS));
            assertNull(markAll.get(30, TimeUnit.SECONDS));

            assertNotNull(notificationRecipientRepository
                    .findByNotificationIdAndUserAccountId(notificationId, target.accountId())
                    .orElseThrow()
                    .getReadAt());
            assertEquals(0L, notificationRecipientRepository.countByUserAccountIdAndReadAtIsNull(target.accountId()));
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
        }
    }

    @RepeatedTest(5)
    void shouldEnforceRecipientUniqueConstraintUnderConcurrentInserts() throws Exception {
        Fixture target = createOperator("Concurrency Unique Target");
        Long notificationId = sendDirectSync(target.personId());
        // Remove a linha criada pelo envio para testar a insercao concorrente do zero.
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("DELETE FROM tb_notification_recipient WHERE notification_id = ? AND user_account_id = ?",
                        notificationId, target.accountId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Exception> firstInsert = executor.submit(() ->
                    runInsertRecipient(notificationId, target.accountId(), readyLatch, startLatch));
            Future<Exception> secondInsert = executor.submit(() ->
                    runInsertRecipient(notificationId, target.accountId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            startLatch.countDown();

            Exception firstException = firstInsert.get(30, TimeUnit.SECONDS);
            Exception secondException = secondInsert.get(30, TimeUnit.SECONDS);

            // List.of(...) rejeita elementos nulos; um dos dois e null no caso de sucesso esperado.
            long successes = java.util.stream.Stream.of(firstException, secondException).filter(e -> e == null).count();
            long failures = java.util.stream.Stream.of(firstException, secondException).filter(e -> e != null).count();

            assertEquals(1, successes, "Exatamente uma insercao concorrente deve ter sucesso");
            assertEquals(1, failures, "A outra insercao concorrente deve falhar (unique constraint "
                    + "ou espera de lock transitoria), nunca as duas terem sucesso");

            Exception loserException = firstException != null ? firstException : secondException;
            // A segunda insercao concorrente na mesma chave unica sempre falha, mas o tipo exato
            // depende do timing real do InnoDB: pode ser DataIntegrityViolationException (chave
            // duplicada apos o commit da primeira) ou uma falha transitoria de lock/deadlock
            // (ConcurrencyFailureException e suas subclasses, como CannotAcquireLockException e
            // PessimisticLockingFailureException) enquanto aguarda o lock de insercao da primeira.
            // Qualquer outra excecao indica um defeito nao relacionado a concorrencia ou a
            // unicidade e deve falhar o teste, nao ser aceita como resultado esperado.
            assertExpectedConcurrentInsertFailure(loserException);

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ? AND user_account_id = ?",
                    Integer.class, notificationId, target.accountId()),
                    "Deve existir exatamente um NotificationRecipient para este notificationId/userAccountId, sem duplicidade");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?",
                    Integer.class, notificationId),
                    "Nao deve haver nenhum registro parcial adicional de recipient para esta notificacao");
            assertTrue(notificationRepository.existsById(notificationId),
                    "A Notification principal deve permanecer valida apos a disputa concorrente");
        } finally {
            executor.shutdownNow();
            cleanupNotifications();
            cleanupPerson(target.personId());
        }
    }

    /**
     * Aceita apenas as excecoes esperadas para a insercao concorrente perdedora deste cenario:
     * violacao da unique constraint (DataIntegrityViolationException) ou falha transitoria de
     * lock/deadlock (ConcurrencyFailureException e subclasses, ex. CannotAcquireLockException,
     * PessimisticLockingFailureException). Inspeciona a cadeia de causas para o caso de a
     * excecao vir encapsulada; qualquer outra causa faz o teste falhar explicitamente.
     */
    private void assertExpectedConcurrentInsertFailure(Exception exception) {
        assertNotNull(exception, "A outra insercao concorrente deve falhar (unique constraint "
                + "ou espera de lock transitoria), nunca as duas terem sucesso");
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException || cause instanceof ConcurrencyFailureException) {
                return;
            }
            cause = cause.getCause();
        }
        fail("Excecao inesperada para a insercao concorrente perdedora (fora do escopo de "
                + "unique constraint/lock transitorio deste cenario): " + exception, exception);
    }

    // ------------------------------------------------------------ Thread bodies

    private Outcome runDirectSend(Long personId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            var response = notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.DIRECT, NotificationCategory.GENERAL, "Concorrencia", "Mensagem",
                    null, List.of(personId), null, null, null, null));
            return new Outcome(response.getNotificationId(), null);
        } catch (Exception e) {
            return new Outcome(null, e);
        }
    }

    private Outcome runMinistrySend(List<MinistryType> ministryTypes, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            var response = notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.MINISTRY, NotificationCategory.GENERAL, "Concorrencia Ministerio", "Mensagem",
                    ministryTypes, null, null, null, null, null));
            return new Outcome(response.getNotificationId(), null);
        } catch (Exception e) {
            return new Outcome(null, e);
        }
    }

    private Exception runDisableAccount(Long personId, Long accountId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                // Mesma ordem global Person -> UserAccount do envio, para nao introduzir uma
                // ordem de lock invertida que causaria deadlock apenas por causa deste teste.
                personRepository.findByIdForUpdate(personId).orElseThrow();
                UserAccount account = userAccountRepository.findByIdForUpdate(accountId).orElseThrow();
                account.disable(java.time.LocalDateTime.now().withNano(0));
                userAccountRepository.save(account);
            });
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runDeactivatePerson(Long personId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                Person person = personRepository.findByIdForUpdate(personId).orElseThrow();
                person.deactivate();
                personRepository.save(person);
            });
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runDeactivatePersonMinistry(
            Long personId,
            MinistryType ministryType,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                // Mesma ordem global Person -> PersonMinistry do envio.
                personRepository.findByIdForUpdate(personId).orElseThrow();
                Long ministryId = com.eventoscelebrativos.support.LegacyMinistryTestFactory
                        .persistentMinistry(ministryType, ministryRepository)
                        .getId();
                PersonMinistry personMinistry = personMinistryRepository
                        .findByPersonIdAndMinistryIdInForUpdate(personId, List.of(ministryId))
                        .stream().findFirst().orElseThrow();
                personMinistry.deactivate();
                personMinistryRepository.save(personMinistry);
            });
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runActivatePersonMinistry(
            Long personId,
            MinistryType ministryType,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                Person person = personRepository.findByIdForUpdate(personId).orElseThrow();
                personMinistryRepository.save(personMinistry(person, ministryType, ministryRepository));
            });
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runMarkAsRead(Long accountId, Long notificationId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            notificationInboxService.markAsRead(accountId, notificationId);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runMarkAllAsRead(Long accountId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            notificationInboxService.markAllAsRead(accountId);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private Exception runInsertRecipient(Long notificationId, Long accountId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                var notification = notificationRepository.findById(notificationId).orElseThrow();
                var account = userAccountRepository.findById(accountId).orElseThrow();
                notificationRecipientRepository.saveAndFlush(
                        new com.eventoscelebrativos.model.NotificationRecipient(notification, account, "Concurrent Insert"));
            });
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    // ------------------------------------------------------------ Fixtures

    private Long sendDirectSync(Long personId) {
        return notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                NotificationAudience.DIRECT, NotificationCategory.GENERAL, "Sync", "Mensagem sync",
                null, List.of(personId), null, null, null, null)).getNotificationId();
    }

    private Fixture createOperator(String name) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person(name, uniquePhoneNumber(), java.time.LocalDate.of(1990, 1, 10));
            person.activate();
            Person savedPerson = personRepository.save(person);

            java.time.LocalDateTime now = java.time.LocalDateTime.now().withNano(0);
            UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
            UserAccount savedAccount = userAccountRepository.save(account);
            Role role = roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
            userAccountRoleRepository.save(new UserAccountRole(savedAccount, role));

            return new Fixture(savedPerson.getId(), savedAccount.getId());
        });
    }

    private Fixture createOperatorWithMinistry(String name, MinistryType ministryType) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Person person = new Person(name, uniquePhoneNumber(), java.time.LocalDate.of(1990, 1, 10));
            person.activate();
            Person savedPerson = personRepository.save(person);
            personMinistryRepository.save(personMinistry(savedPerson, ministryType, ministryRepository));

            java.time.LocalDateTime now = java.time.LocalDateTime.now().withNano(0);
            UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
            UserAccount savedAccount = userAccountRepository.save(account);
            Role role = roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow();
            userAccountRoleRepository.save(new UserAccountRole(savedAccount, role));

            return new Fixture(savedPerson.getId(), savedAccount.getId());
        });
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM tb_notification_recipient WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId);
            jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
            jdbcTemplate.update(
                    "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId);
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
        });
    }

    private void cleanupNotifications() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM tb_notification_ministry");
            jdbcTemplate.update("DELETE FROM tb_notification_recipient");
            jdbcTemplate.update("DELETE FROM tb_notification");
        });
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }

    private record Fixture(Long personId, Long accountId) {
    }

    private record Outcome(Long notificationId, Exception error) {
    }
}
