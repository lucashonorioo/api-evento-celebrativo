package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.Ministry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com JPA e transacoes reais contra MySQL 8.4, que a unique constraint de
 * {@code tb_ministry.normalized_name} e a protecao final contra duas criacoes concorrentes
 * semanticamente equivalentes.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class MinistryRepositoryConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static String mysqlVersion;
    private static boolean mysqlAvailable;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void provisionIsolatedMySqlDatabase() {
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
            databaseName = "ministry_catalog_concurrency_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        } catch (SQLException exception) {
            mysqlAvailable = false;
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
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
    }

    @Test
    void shouldAllowOnlyOneConcurrentInsertForEquivalentNormalizedName() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> first = executor.submit(insertTask("Acólitos", ready, start));
            Future<Exception> second = executor.submit(insertTask("Acolitos", ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Threads nao ficaram prontas a tempo");
            start.countDown();

            Exception firstException = first.get(30, TimeUnit.SECONDS);
            Exception secondException = second.get(30, TimeUnit.SECONDS);

            long successes = java.util.stream.Stream.of(firstException, secondException).filter(e -> e == null).count();
            long failures = java.util.stream.Stream.of(firstException, secondException).filter(e -> e != null).count();

            assertEquals(1, successes, "Exatamente uma insercao concorrente deve persistir");
            assertEquals(1, failures, "A outra insercao concorrente deve falhar pela constraint unica");
            assertExpectedConcurrentInsertFailure(firstException != null ? firstException : secondException);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_ministry WHERE normalized_name = 'ACOLITOS'",
                    Integer.class
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Exception> insertTask(String name, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status ->
                        ministryRepository.saveAndFlush(new Ministry(name))
                );
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private void assertExpectedConcurrentInsertFailure(Exception exception) {
        assertNotNull(exception, "A insercao concorrente perdedora deve falhar");
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException || cause instanceof ConcurrencyFailureException) {
                return;
            }
            cause = cause.getCause();
        }
        fail("Excecao inesperada para insercao concorrente perdedora: " + exception, exception);
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
}
