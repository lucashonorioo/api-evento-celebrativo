package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, contra MySQL 8.4 real (nao H2), que a migration V26 cria o catalogo persistente de
 * ministerios com PK, NOT NULL, UNIQUE(normalized_name) e seed legado inicial.
 */
class V26CreateMinistryCatalogMySqlIntegrationTest {

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
            mysqlAvailable = connection.isValid(3) && mysqlVersion.startsWith("8.4.");
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
    void shouldCreateTableWhenUpgradingFromV25OnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v26my_upgrade");
        migrateUntil(dataSource, "25");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(4, result.migrationsExecuted);
        assertEquals(1, tableCount(jdbcTemplate, "tb_ministry"));
    }

    @Test
    void shouldCreateExpectedColumnsAndConstraintsOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v26my_schema");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertColumn(jdbcTemplate, "id", "bigint", "NO", "auto_increment");
        assertColumn(jdbcTemplate, "name", "varchar", "NO");
        assertColumn(jdbcTemplate, "normalized_name", "varchar", "NO");
        assertColumn(jdbcTemplate, "active", "tinyint", "NO");
        assertColumn(jdbcTemplate, "created_at", "timestamp", "NO");
        assertColumn(jdbcTemplate, "updated_at", "timestamp", "NO");
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tb_ministry'
                  AND CONSTRAINT_NAME = 'uk_tb_ministry_normalized_name'
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """,
                Integer.class
        ));
    }

    @Test
    void shouldSeedFiveLegacyMinistriesAsActiveOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v26my_seed");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT name, normalized_name, active FROM tb_ministry ORDER BY id"
        );

        assertEquals(5, rows.size());
        assertSeedRow(rows.get(0), "Presbíteros", "PRESBITEROS");
        assertSeedRow(rows.get(1), "Leitores", "LEITORES");
        assertSeedRow(rows.get(2), "Comentaristas", "COMENTARISTAS");
        assertSeedRow(rows.get(3), "Ministros da Palavra", "MINISTROS DA PALAVRA");
        assertSeedRow(rows.get(4), "Ministros da Eucaristia", "MINISTROS DA EUCARISTIA");
    }

    @Test
    void shouldRejectDuplicateNormalizedNameOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v26my_unique");
        migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tb_ministry(name, normalized_name, active) VALUES (' leitores ', 'LEITORES', TRUE)"
        ));
    }

    @Test
    void shouldNotDuplicateSeedWhenMigrateRunsAgainOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v26my_repeat");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult first = migrateAll(dataSource);
        MigrateResult second = migrateAll(dataSource);

        assertEquals(29, first.migrationsExecuted);
        assertTrue(second.migrations.isEmpty());
        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_ministry", Integer.class));
    }

    private void assertColumn(JdbcTemplate jdbcTemplate, String columnName, String expectedDataType, String expectedNullable) {
        Map<String, Object> column = jdbcTemplate.queryForMap(
                """
                SELECT DATA_TYPE, IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tb_ministry'
                  AND COLUMN_NAME = ?
                """,
                columnName
        );
        assertEquals(expectedDataType, column.get("DATA_TYPE"));
        assertEquals(expectedNullable, column.get("IS_NULLABLE"));
    }

    private void assertColumn(
            JdbcTemplate jdbcTemplate,
            String columnName,
            String expectedDataType,
            String expectedNullable,
            String expectedExtra
    ) {
        Map<String, Object> column = jdbcTemplate.queryForMap(
                """
                SELECT DATA_TYPE, IS_NULLABLE, EXTRA
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tb_ministry'
                  AND COLUMN_NAME = ?
                """,
                columnName
        );
        assertEquals(expectedDataType, column.get("DATA_TYPE"));
        assertEquals(expectedNullable, column.get("IS_NULLABLE"));
        assertEquals(expectedExtra, column.get("EXTRA"));
    }

    private void assertSeedRow(Map<String, Object> row, String expectedName, String expectedNormalizedName) {
        assertEquals(expectedName, row.get("name"));
        assertEquals(expectedNormalizedName, row.get("normalized_name"));
        assertTrue(isTrue(row.get("active")));
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

    private int tableCount(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );
        return count == null ? 0 : count;
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

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
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
