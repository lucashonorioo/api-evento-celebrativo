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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, contra MySQL 8.4 real (nao H2), que a migration V19 cria {@code tb_parish_profile} como
 * singleton real com as mesmas garantias do teste H2 equivalente ({@link V19CreateParishProfileIntegrationTest}):
 * linha id=1 pre-existente e unconfigured, constraint que impede segunda linha, constraint que
 * impede configured=true sem name/diocese, e upgrade a partir de V18 sem alterar migrations
 * anteriores. Ignorado automaticamente quando MySQL 8.4 nao estiver acessivel (mesmas propriedades
 * documentadas em V13V14MigrationMySqlIntegrationTest).
 */
class V19CreateParishProfileMySqlIntegrationTest {

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
    void shouldCreateSingletonRowUnconfiguredWhenUpgradingFromV18OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v19my_singleton");
        migrateUntil(dataSource, "18");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource, "19");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(1, countRows(jdbcTemplate));
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT id, configured, name, diocese FROM tb_parish_profile");
        assertEquals(1L, ((Number) row.get("id")).longValue());
        assertFalse((Boolean) row.get("configured"));
        assertEquals(null, row.get("name"));
        assertEquals(null, row.get("diocese"));
    }

    @Test
    void shouldRejectSecondRowOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v19my_second_row");
        migrateAll(dataSource, "19");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_parish_profile (id, configured) VALUES (2, FALSE)"));
        assertEquals(1, countRows(jdbcTemplate));
    }

    @Test
    void shouldRejectConfiguredTrueWithoutIdentityOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v19my_configured_guard");
        migrateAll(dataSource, "19");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "UPDATE tb_parish_profile SET configured = TRUE WHERE id = 1"));
    }

    @Test
    void shouldNotAlterAnyPreviousMigrationOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v19my_no_prior_change");
        MigrateResult result = migrateAll(dataSource, "19");

        assertEquals(19, result.migrationsExecuted);
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

    private int countRows(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
        return count == null ? 0 : count;
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
