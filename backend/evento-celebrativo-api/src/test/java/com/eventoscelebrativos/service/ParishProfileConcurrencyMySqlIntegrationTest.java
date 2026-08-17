package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParishProfileContactUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes contra MySQL 8.4 real, que duas atualizacoes
 * concorrentes de {@code ParishProfile} nunca criam uma segunda linha nem corrompem o singleton: a
 * linha id=1 ja existe (criada por V19), entao as duas transacoes disputam o mesmo lock pessimista
 * ({@code findByIdForUpdate}) e serializam - nunca ha INSERT concorrente. Ignorado automaticamente
 * quando MySQL 8.4 nao estiver acessivel.
 */
@SpringBootTest
class ParishProfileConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @BeforeAll
    static void provisionIsolatedMySqlDatabase() throws SQLException {
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
            String version = queryVersion(statement);
            mysqlAvailable = connection.isValid(3) && version.startsWith("8.4.");
            if (!mysqlAvailable) {
                return;
            }
            databaseName = "parish_profile_concurrency_" + UUID.randomUUID().toString().replace("-", "");
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
        Assumptions.assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @Autowired
    private ParishProfileService parishProfileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSerializeConcurrentUpdatesWithoutDuplicatingTheSingletonRow() throws Exception {
        ParishProfileUpdateRequestDTO requestA = new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrente A", "Diocese A", null, null, null, null);
        ParishProfileUpdateRequestDTO requestB = new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrente B", "Diocese B", null, null, null, null);

        AtomicInteger successes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable taskA = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.update(requestA);
                successes.incrementAndGet();
            };
            Runnable taskB = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.update(requestB);
                successes.incrementAndGet();
            };

            var futureA = executor.submit(taskA);
            var futureB = executor.submit(taskB);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureA.get(15, TimeUnit.SECONDS);
            futureB.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, successes.get(), "As duas atualizacoes concorrentes devem serializar e concluir com sucesso");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
        assertEquals(1, rowCount, "Nunca deve existir mais de uma linha na tabela singleton");
        Long id = jdbcTemplate.queryForObject("SELECT id FROM tb_parish_profile", Long.class);
        assertEquals(1L, id);

        String finalName = jdbcTemplate.queryForObject("SELECT name FROM tb_parish_profile WHERE id = 1", String.class);
        String finalDiocese = jdbcTemplate.queryForObject("SELECT diocese FROM tb_parish_profile WHERE id = 1", String.class);
        boolean matchesA = "Paróquia Concorrente A".equals(finalName) && "Diocese A".equals(finalDiocese);
        boolean matchesB = "Paróquia Concorrente B".equals(finalName) && "Diocese B".equals(finalDiocese);
        assertTrue(matchesA || matchesB,
                "O estado final deve corresponder integralmente a uma das duas atualizacoes concorrentes, nunca a uma mistura dos dois requests");
    }

    /**
     * Prova, contra MySQL 8.4 real, que o segundo caminho de escrita introduzido por esta feature
     * (updateContact, usado pela secretaria) disputa o MESMO lock pessimista de findByIdForUpdate
     * que o update administrativo completo, sem nunca produzir lost update: o estado final sempre
     * corresponde a uma ordem serial valida (admin depois secretaria, ou secretaria depois admin),
     * e name/diocese - campos que updateContact nunca toca - permanecem sempre os valores definidos
     * pelo update administrativo.
     */
    @Test
    void shouldSerializeAdminFullUpdateWithSecretaryContactUpdateWithoutLostUpdate() throws Exception {
        parishProfileService.update(new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrência Mista", "Diocese Mista", null, null, null, null));

        ParishProfileUpdateRequestDTO adminRequest = new ParishProfileUpdateRequestDTO(
                "Paróquia Concorrência Mista", "Diocese Mista", "34955550000", null, null, null);
        ParishProfileContactUpdateRequestDTO secretaryRequest = new ParishProfileContactUpdateRequestDTO(
                "34966660000", null, null, null);

        AtomicInteger successes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable adminTask = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.update(adminRequest);
                successes.incrementAndGet();
            };
            Runnable secretaryTask = () -> {
                ready.countDown();
                await(ready, start);
                parishProfileService.updateContact(secretaryRequest);
                successes.incrementAndGet();
            };

            var futureAdmin = executor.submit(adminTask);
            var futureSecretary = executor.submit(secretaryTask);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureAdmin.get(15, TimeUnit.SECONDS);
            futureSecretary.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, successes.get(), "As duas atualizacoes concorrentes devem serializar e concluir com sucesso");

        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
        assertEquals(1, rowCount, "Nunca deve existir mais de uma linha na tabela singleton");

        String finalName = jdbcTemplate.queryForObject("SELECT name FROM tb_parish_profile WHERE id = 1", String.class);
        String finalDiocese = jdbcTemplate.queryForObject("SELECT diocese FROM tb_parish_profile WHERE id = 1", String.class);
        assertEquals("Paróquia Concorrência Mista", finalName,
                "updateContact nunca deve alterar o nome, mesmo sob corrida com o update administrativo");
        assertEquals("Diocese Mista", finalDiocese,
                "updateContact nunca deve alterar a diocese, mesmo sob corrida com o update administrativo");

        String finalPhone = jdbcTemplate.queryForObject(
                "SELECT institutional_phone FROM tb_parish_profile WHERE id = 1", String.class);
        boolean matchesAdminLast = "34955550000".equals(finalPhone);
        boolean matchesSecretaryLast = "34966660000".equals(finalPhone);
        assertTrue(matchesAdminLast || matchesSecretaryLast,
                "O telefone final deve corresponder integralmente a uma das duas atualizacoes concorrentes, "
                        + "nunca a um estado misto ou corrompido");
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
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
