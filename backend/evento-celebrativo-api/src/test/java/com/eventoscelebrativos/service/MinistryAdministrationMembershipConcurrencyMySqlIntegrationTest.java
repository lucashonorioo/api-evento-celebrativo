package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.MinistryInactiveException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.repository.MinistryRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, em MySQL 8.4 real, que a desativacao administrativa de Ministry e as mutacoes que podem
 * deixar PersonMinistry ativo serializam pelo lock da propria linha de Ministry.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class MinistryAdministrationMembershipConcurrencyMySqlIntegrationTest {

    private static final String READER_NORMALIZED_NAME = "LEITORES";
    private static final String TEST_PHONE_PREFIX = "3495";

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static String mysqlVersion;
    private static boolean mysqlAvailable;
    private static boolean mysqlProvisioningAttempted;

    @Autowired
    private MinistryAdministrationService ministryAdministrationService;

    @Autowired
    private PersonMinistryCommandService personMinistryCommandService;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void provisionIsolatedMySqlDatabase() {
        ensureMySqlProvisioned();
    }

    private static synchronized void ensureMySqlProvisioned() {
        if (mysqlProvisioningAttempted) {
            return;
        }
        mysqlProvisioningAttempted = true;

        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty("mysql.validation.password", System.getenv("MYSQL_VALIDATION_PASSWORD"));

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }

        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            mysqlVersion = queryVersion(statement);
            mysqlAvailable = connection.isValid(3) && mysqlVersion.startsWith("8.4.");
            if (!mysqlAvailable) {
                return;
            }
            databaseName = "min_adm_pm_conc_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        } catch (SQLException exception) {
            mysqlAvailable = false;
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        ensureMySqlProvisioned();
        if (!mysqlAvailable) {
            return;
        }
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void dropIsolatedMySqlDatabase() {
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
    void requireMySql84() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
        cleanMutableRows();
    }

    @AfterEach
    void cleanUp() {
        if (mysqlAvailable) {
            cleanMutableRows();
        }
    }

    @Test
    void deactivationShouldWinAgainstConcurrentAdd() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Deactivate Wins Add");

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> deactivateMinistry(ministryId),
                () -> addOrReactivate(personId, ministryId)
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(MinistryInactiveException.class, outcome.secondFailure());
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void addShouldWinAgainstConcurrentDeactivation() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Add Wins Deactivate");

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> addOrReactivate(personId, ministryId),
                () -> deactivateMinistry(ministryId)
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(LifecycleConflictException.class, outcome.secondFailure());
        assertTrue(isMinistryActive(ministryId));
        assertEquals(1, activeMembershipCount(ministryId));
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void deactivationShouldWinAgainstConcurrentReactivate() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Deactivate Wins Reactivate");
        insertInactiveMembership(personId, ministryId);

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> deactivateMinistry(ministryId),
                () -> addOrReactivate(personId, ministryId)
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(MinistryInactiveException.class, outcome.secondFailure());
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void reactivateShouldWinAgainstConcurrentDeactivation() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Reactivate Wins Deactivate");
        insertInactiveMembership(personId, ministryId);

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> addOrReactivate(personId, ministryId),
                () -> deactivateMinistry(ministryId)
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(LifecycleConflictException.class, outcome.secondFailure());
        assertTrue(isMinistryActive(ministryId));
        assertEquals(1, activeMembershipCount(ministryId));
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void deactivationShouldWinAgainstConcurrentSync() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Deactivate Wins Sync");

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> deactivateMinistry(ministryId),
                () -> personMinistryCommandService.syncMinistriesById(personId, List.of(ministryId))
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(MinistryInactiveException.class, outcome.secondFailure());
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void syncShouldWinAgainstConcurrentDeactivation() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Sync Wins Deactivate");

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> personMinistryCommandService.syncMinistriesById(personId, List.of(ministryId)),
                () -> deactivateMinistry(ministryId)
        );

        assertNull(outcome.firstFailure());
        assertInstanceOf(LifecycleConflictException.class, outcome.secondFailure());
        assertTrue(isMinistryActive(ministryId));
        assertEquals(1, activeMembershipCount(ministryId));
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void addShouldRejectAfterPreloadedMinistryIsConcurrentlyDeactivated() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Stale Add");

        Throwable failure = runAfterPreloadingMinistryAndConcurrentDeactivation(
                ministryId,
                () -> addOrReactivate(personId, ministryId)
        );

        assertInstanceOf(MinistryInactiveException.class, failure);
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void reactivateShouldRejectAfterPreloadedMinistryIsConcurrentlyDeactivated() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Stale Reactivate");
        insertInactiveMembership(personId, ministryId);

        Throwable failure = runAfterPreloadingMinistryAndConcurrentDeactivation(
                ministryId,
                () -> addOrReactivate(personId, ministryId)
        );

        assertInstanceOf(MinistryInactiveException.class, failure);
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void syncShouldRejectAfterPreloadedMinistryIsConcurrentlyDeactivated() throws Exception {
        Long ministryId = readerMinistryId();
        Long personId = insertPerson("Stale Sync");

        Throwable failure = runAfterPreloadingMinistryAndConcurrentDeactivation(
                ministryId,
                () -> personMinistryCommandService.syncMinistriesById(personId, List.of(ministryId))
        );

        assertInstanceOf(MinistryInactiveException.class, failure);
        assertFalse(isMinistryActive(ministryId));
        assertNoActiveMemberships(ministryId);
        assertNoInactiveMinistryWithActiveMembership();
    }

    @Test
    void arbitraryMinistryDuplicateMembershipRaceShouldBeRejectedByCanonicalUniqueConstraint() throws Exception {
        Long ministryId = insertArbitraryMinistry("Acolitos Duplicate Race");
        Long personId = insertPerson("Duplicate Arbitrary Membership Race");

        RaceOutcome outcome = runFirstOperationHoldingCommit(
                () -> insertActiveMembership(personId, ministryId),
                () -> insertActiveMembership(personId, ministryId)
        );

        assertEquals(1, countSucceeded(outcome));
        assertInstanceOf(DataIntegrityViolationException.class, failed(outcome));
        assertEquals(1, membershipCount(personId, ministryId));
        assertFalse(hasLegacyMapping(ministryId));
    }

    private RaceOutcome runFirstOperationHoldingCommit(Operation firstOperation, Operation secondOperation)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstExecutedBeforeCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCommit = new CountDownLatch(1);
        try {
            Future<Throwable> first = executor.submit(() -> runInTransactionHoldingCommit(
                    firstOperation,
                    firstExecutedBeforeCommit,
                    releaseFirstCommit
            ));
            assertTrue(firstExecutedBeforeCommit.await(10, TimeUnit.SECONDS),
                    "Primeira operacao nao chegou ao ponto pre-commit a tempo");

            Future<Throwable> second = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    secondOperation.run();
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS),
                    "Segunda operacao nao iniciou a tempo");

            releaseFirstCommit.countDown();
            return new RaceOutcome(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            releaseFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Throwable runAfterPreloadingMinistryAndConcurrentDeactivation(Long ministryId, Operation membershipOperation)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> membershipFailure = new AtomicReference<>();
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    ministryRepository.findById(ministryId).orElseThrow();
                    Throwable deactivationFailure = awaitResult(executor.submit(() -> capture(
                            () -> deactivateMinistry(ministryId)
                    )));
                    if (deactivationFailure != null) {
                        throw new AssertionError("Desativacao concorrente falhou antes da mutacao de membership",
                                deactivationFailure);
                    }
                    membershipFailure.set(capture(membershipOperation));
                });
            } catch (UnexpectedRollbackException exception) {
                if (membershipFailure.get() == null) {
                    throw exception;
                }
            }
            return membershipFailure.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable runInTransactionHoldingCommit(
            Operation operation,
            CountDownLatch operationExecuted,
            CountDownLatch releaseCommit
    ) {
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                operation.run();
                operationExecuted.countDown();
                await(releaseCommit);
            });
            return null;
        } catch (Throwable throwable) {
            operationExecuted.countDown();
            return throwable;
        }
    }

    private Throwable capture(Operation operation) {
        try {
            operation.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private Throwable awaitResult(Future<Throwable> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Operacao concorrente nao concluiu a tempo", exception);
        }
    }

    private void deactivateMinistry(Long ministryId) {
        ministryAdministrationService.updateStatus(ministryId, new MinistryStatusUpdateRequestDTO(false));
    }

    private void addOrReactivate(Long personId, Long ministryId) {
        personMinistryCommandService.addOrReactivateMinistry(personId, ministryReference(ministryId));
    }

    private Ministry ministryReference(Long ministryId) {
        return ministryRepository.findById(ministryId).orElseThrow();
    }

    private Long readerMinistryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_ministry WHERE normalized_name = ?",
                Long.class,
                READER_NORMALIZED_NAME
        );
    }

    private Long insertPerson(String label) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number, active) VALUES (?, ?, ?, TRUE)",
                newPublicId(),
                label + " " + UUID.randomUUID(),
                phoneNumber
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private Long insertArbitraryMinistry(String label) {
        String name = label + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_ministry(name, normalized_name, active, created_at, updated_at)
                VALUES (?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                name,
                name.toUpperCase()
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_ministry WHERE name = ?", Long.class, name);
    }

    private void insertActiveMembership(Long personId, Long ministryId) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(
                    person_id, ministry_id, active, coordinator, created_at, updated_at
                )
                VALUES (?, ?, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                ministryId
        );
    }

    private void insertInactiveMembership(Long personId, Long ministryId) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(
                    person_id, ministry_id, active, coordinator, created_at, updated_at
                )
                VALUES (?, ?, FALSE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                ministryId
        );
    }

    private boolean isMinistryActive(Long ministryId) {
        Object value = jdbcTemplate.queryForObject(
                "SELECT active FROM tb_ministry WHERE id = ?",
                Object.class,
                ministryId
        );
        return isTrue(value);
    }

    private int activeMembershipCount(Long ministryId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE ministry_id = ? AND active = TRUE",
                Integer.class,
                ministryId
        );
    }

    private int membershipCount(Long personId, Long ministryId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ? AND ministry_id = ?",
                Integer.class,
                personId,
                ministryId
        );
    }

    private boolean hasLegacyMapping(Long ministryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping WHERE ministry_id = ?",
                Integer.class,
                ministryId
        );
        return count != null && count > 0;
    }

    private void assertNoActiveMemberships(Long ministryId) {
        assertEquals(0, activeMembershipCount(ministryId));
    }

    private void assertNoInactiveMinistryWithActiveMembership() {
        assertEquals(0, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                JOIN tb_ministry m ON m.id = pm.ministry_id
                WHERE m.active = FALSE AND pm.active = TRUE
                """,
                Integer.class
        ));
    }

    private void cleanMutableRows() {
        jdbcTemplate.update(
                "DELETE FROM tb_person_ministry WHERE person_id IN "
                        + "(SELECT id FROM tb_person WHERE phone_number LIKE ?)",
                TEST_PHONE_PREFIX + "%"
        );
        jdbcTemplate.update("DELETE FROM tb_person WHERE phone_number LIKE ?", TEST_PHONE_PREFIX + "%");
        jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    coordinator = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE ministry_id = (
                    SELECT id FROM tb_ministry WHERE normalized_name = ?
                )
                """,
                READER_NORMALIZED_NAME
        );
        jdbcTemplate.update(
                "UPDATE tb_ministry SET active = TRUE, updated_at = CURRENT_TIMESTAMP(6) WHERE normalized_name = ?",
                READER_NORMALIZED_NAME
        );
    }

    private UUID newPublicId() {
        return UUID.randomUUID();
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return TEST_PHONE_PREFIX + String.format("%07d", suffix);
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return false;
    }

    private long countSucceeded(RaceOutcome outcome) {
        return java.util.stream.Stream.of(outcome.firstFailure(), outcome.secondFailure())
                .filter(java.util.Objects::isNull)
                .count();
    }

    private Throwable failed(RaceOutcome outcome) {
        return java.util.stream.Stream.of(outcome.firstFailure(), outcome.secondFailure())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch nao liberado a tempo");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run();
    }

    private record RaceOutcome(Throwable firstFailure, Throwable secondFailure) {
    }
}
