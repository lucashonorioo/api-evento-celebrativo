package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, contra MySQL 8.4 real (nao H2), que a migration V22 adiciona a coluna {@code status} com as
 * mesmas garantias do teste H2 equivalente ({@link V22AddCelebrationEventStatusIntegrationTest}),
 * incluindo a CHECK real aplicada pelo MySQL. Ignorado automaticamente quando MySQL 8.4 nao estiver
 * acessivel (mesmas propriedades documentadas em V13V14MigrationMySqlIntegrationTest).
 */
class V22AddCelebrationEventStatusMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String mysqlVersion;
    private static boolean mysqlAvailable;
    private static final List<String> CREATED_DATABASES = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void checkAvailability() {
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
            mysqlAvailable = connection.isValid(3);
        } catch (SQLException e) {
            mysqlAvailable = false;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @AfterAll
    static void dropCreatedDatabases() {
        if (!mysqlAvailable) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            for (String db : CREATED_DATABASES) {
                statement.execute("DROP DATABASE IF EXISTS `" + db + "`");
            }
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @Test
    void shouldReportMySql84Version() {
        assertTrue(mysqlVersion.startsWith("8.4."), "Versao inesperada: " + mysqlVersion);
    }

    @Test
    void shouldBackfillExistingRowsWithStatusActiveWhenUpgradingFromV21OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_backfill");
        migrateUntil(dataSource, "21");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEvent(jdbcTemplate, "Missa Preexistente MySQL");

        MigrateResult result = migrateAll(dataSource, "22");

        assertEquals(1, result.migrationsExecuted);
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldAllowStatusCancelledOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_cancelled");
        migrateAll(dataSource, "22");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEventWithStatus(jdbcTemplate, "Missa Cancelada MySQL", "CANCELLED");

        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldRejectInvalidStatusValueOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_check");
        migrateAll(dataSource, "22");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Mesmo achado empirico documentado em V21AddPersonMinistryCoordinatorMySqlIntegrationTest:
        // violacao de CHECK (erro 3819) vira UncategorizedSQLException, nao DataIntegrityViolationException.
        assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration, status)
                VALUES (?, ?, ?, TRUE, 'DELETED')
                """,
                "Missa Status Invalido MySQL", LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0)));
    }

    @Test
    void shouldRejectNullStatusOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_null");
        migrateAll(dataSource, "22");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration, status)
                VALUES (?, ?, ?, TRUE, NULL)
                """,
                "Missa Status Nulo MySQL", LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0)));
    }

    @Test
    void shouldDefaultStatusToActiveOnNewRowWithoutExplicitValueOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_default");
        migrateAll(dataSource, "22");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long eventId = insertEvent(jdbcTemplate, "Missa Nova MySQL");

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM tb_celebration_event WHERE id = ?", String.class, eventId));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v22my_no_prior_change");
        MigrateResult result = migrateAll(dataSource, "22");

        assertEquals(22, result.migrationsExecuted);
    }

    private Long insertEvent(JdbcTemplate jdbcTemplate, String name) {
        jdbcTemplate.update(
                "INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration) VALUES (?, ?, ?, TRUE)",
                name, LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?", Long.class, name);
    }

    private Long insertEventWithStatus(JdbcTemplate jdbcTemplate, String name, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration, status)
                VALUES (?, ?, ?, TRUE, ?)
                """,
                name, LocalDateTime.of(2027, 1, 1, 10, 0), LocalDateTime.of(2027, 1, 1, 11, 0), status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?", Long.class, name);
    }

    private DataSource createDatabase(String namePrefix) throws SQLException {
        String dbName = namePrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + dbName + "`");
        }
        CREATED_DATABASES.add(dbName);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private void migrateUntil(DataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private MigrateResult migrateAll(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (java.sql.ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
