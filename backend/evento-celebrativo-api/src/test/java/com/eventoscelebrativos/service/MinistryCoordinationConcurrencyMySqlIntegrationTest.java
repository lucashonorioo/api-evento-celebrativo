package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.MinistryCoordinationRequiresActiveMinistryException;
import com.eventoscelebrativos.model.MinistryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.normalizedName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes contra MySQL 8.4 real, os invariantes criticos
 * de coordenacao ministerial: nunca {@code coordinator=true} com {@code active=false} na mesma
 * PersonMinistry, e ausencia de deadlock/lost-update quando duas Persons diferentes ou dois comandos
 * concorrentes disputam o mesmo lock de Person. Ignorado automaticamente quando MySQL 8.4 nao
 * estiver acessivel (mesmo padrao de propriedades dos demais testes MySQL do projeto).
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class MinistryCoordinationConcurrencyMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private MinistryCoordinationService ministryCoordinationService;

    @Autowired
    private PersonMinistryCommandService personMinistryCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            databaseName = "ministry_coordination_" + UUID.randomUUID().toString().replace("-", "");
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

    @AfterEach
    void cleanUpBetweenTests() {
        if (!mysqlAvailable) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_person_ministry");
    }

    @Test
    void shouldNeverEndWithCoordinatorTrueAndMinistryInactive() throws Exception {
        long personId = insertPerson("Leitor Concorrente A");
        insertActiveMinistry(personId, MinistryType.READER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> grantResult = executor.submit(grantTask(personId, ready, start));
            Future<Exception> deactivateResult = executor.submit(deactivateTask(personId, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception grantException = grantResult.get(20, TimeUnit.SECONDS);
            Exception deactivateException = deactivateResult.get(20, TimeUnit.SECONDS);

            // Desativar nunca deveria falhar neste cenario (sem EventAssignment, sem guarda PASTOR
            // para READER); grant pode falhar se a desativacao venceu a corrida primeiro.
            assertNull(deactivateException, "A desativacao nao deveria falhar: " + deactivateException);
            if (grantException != null) {
                assertInstanceOf(MinistryCoordinationRequiresActiveMinistryException.class, grantException);
            }

            Boolean active = jdbcTemplate.queryForObject(
                    "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'READER'",
                    Boolean.class, personId);
            Boolean coordinator = jdbcTemplate.queryForObject(
                    "SELECT coordinator FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'READER'",
                    Boolean.class, personId);

            boolean invariantViolated = Boolean.TRUE.equals(coordinator) && Boolean.FALSE.equals(active);
            assertTrue(!invariantViolated, "Nunca pode existir coordinator=true com active=false. "
                    + "active=" + active + " coordinator=" + coordinator);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotDeadlockOrLoseUpdateWhenGrantingAndRevokingConcurrentlyOnSamePersonMinistry() throws Exception {
        long personId = insertPerson("Leitor Concorrente B");
        insertActiveMinistry(personId, MinistryType.READER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> grantResult = executor.submit(grantTask(personId, ready, start));
            Future<Exception> revokeResult = executor.submit(revokeTask(personId, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception grantException = grantResult.get(20, TimeUnit.SECONDS);
            Exception revokeException = revokeResult.get(20, TimeUnit.SECONDS);

            assertNull(grantException, "grantCoordinator nao deveria falhar: " + grantException);
            assertNull(revokeException, "revokeCoordinator nao deveria falhar: " + revokeException);

            Boolean active = jdbcTemplate.queryForObject(
                    "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'READER'",
                    Boolean.class, personId);
            assertTrue(active, "O ministerio nunca foi tocado por este teste; deve continuar ativo");
            // Estado final de coordinator depende de qual operacao venceu a serializacao pelo lock
            // da Person - ambos true e false sao validos; o que importa e nao ter havido deadlock,
            // timeout, nem excecao inesperada (ja verificado acima).
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowDifferentPersonsToCoordinateTheSameMinistryConcurrently() throws Exception {
        long personA = insertPerson("Leitor Concorrente C");
        long personB = insertPerson("Leitor Concorrente D");
        insertActiveMinistry(personA, MinistryType.READER);
        insertActiveMinistry(personB, MinistryType.READER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Exception> resultA = executor.submit(grantTask(personA, ready, start));
            Future<Exception> resultB = executor.submit(grantTask(personB, ready, start));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            Exception exceptionA = resultA.get(20, TimeUnit.SECONDS);
            Exception exceptionB = resultB.get(20, TimeUnit.SECONDS);

            assertNull(exceptionA, "Rodada: grant de A deveria ter sucesso, mas lancou " + exceptionA);
            assertNull(exceptionB, "Rodada: grant de B deveria ter sucesso, mas lancou " + exceptionB);

            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT coordinator FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'READER'",
                    Boolean.class, personA));
            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT coordinator FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'READER'",
                    Boolean.class, personB));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Exception> grantTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                ministryCoordinationService.grantCoordinator(personId, "READER");
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private Callable<Exception> revokeTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                ministryCoordinationService.revokeCoordinator(personId, "READER");
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private Callable<Exception> deactivateTask(long personId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                personMinistryCommandService.removeMinistry(personId, MinistryType.READER, "Leitor");
                return null;
            } catch (Exception exception) {
                return exception;
            }
        };
    }

    private long insertPerson(String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                "INSERT INTO tb_person(public_id, name, phone_number, active) VALUES (?, ?, ?, TRUE)",
                newPublicId(), name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private byte[] newPublicId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private void insertActiveMinistry(long personId, MinistryType ministryType) {
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, ministry_id, active, coordinator, created_at, updated_at) "
                        + "VALUES (?, ?, (SELECT id FROM tb_ministry WHERE normalized_name = ?), "
                        + "TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                personId, ministryType.name(), normalizedName(ministryType));
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
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
