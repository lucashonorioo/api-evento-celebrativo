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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cobertura da migration V17 especificamente contra MySQL 8.4 real (nao apenas H2): confirma que o
 * schema resultante (coluna active_source_key, UNIQUE, indices) e valido para o dialeto MySQL e que
 * o Hibernate consegue validar essa mesma migration via ddl-auto=validate (indiretamente, atraves
 * de qualquer teste MySQL-gated que suba o contexto Spring completo sobre este mesmo schema).
 */
class V17SupportActiveScheduleConflictNotificationsMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static boolean mysqlAvailable;
    private static final List<String> CREATED_DATABASES = new ArrayList<>();

    @BeforeAll
    static void checkMySql84() {
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
        } catch (SQLException exception) {
            mysqlAvailable = false;
        }
    }

    @AfterAll
    static void dropCreatedDatabases() {
        if (!mysqlAvailable) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            for (String database : CREATED_DATABASES) {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @BeforeEach
    void requireMySql84() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @Test
    void shouldApplyActiveScheduleConflictSchemaOnCleanMySqlDatabase() throws SQLException {
        DataSource dataSource = createDatabase("v17my_clean");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(17, result.migrationsExecuted);
        assertEquals("YES", columnValue(jdbcTemplate, "tb_notification", "active_source_key", "IS_NULLABLE"));
        assertEquals(1, countConstraint(jdbcTemplate, "tb_notification", "uk_tb_notification_active_source_key"));
    }

    @Test
    void shouldEnforceActiveSourceKeyUniqueConstraintOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v17my_unique");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateAll(dataSource);

        insertScheduleConflictNotification(jdbcTemplate, null);
        insertScheduleConflictNotification(jdbcTemplate, null);
        insertScheduleConflictNotification(jdbcTemplate, "SCHEDULE_UNAVAILABILITY_CONFLICT:1:2");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertScheduleConflictNotification(jdbcTemplate, "SCHEDULE_UNAVAILABILITY_CONFLICT:1:2"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_notification", Integer.class);
        assertEquals(3, count);
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (var resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private DataSource createDatabase(String namePrefix) throws SQLException {
        String database = namePrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "`");
        }
        CREATED_DATABASES.add(database);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    // Alvo fixo em "17": este teste cobre especificamente a migration V17 e nao deve ser afetado por
    // migrations futuras adicionadas depois dela.
    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("17")
                .load()
                .migrate();
    }

    private String columnValue(JdbcTemplate jdbcTemplate, String tableName, String columnName, String selectedColumn) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT " + selectedColumn + " FROM information_schema.columns "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                tableName,
                columnName
        );
        return String.valueOf(row.get(selectedColumn));
    }

    private int countConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class,
                tableName,
                constraintName
        );
        return count == null ? 0 : count;
    }

    private void insertScheduleConflictNotification(JdbcTemplate jdbcTemplate, String activeSourceKey) {
        jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot,
                     active_source_key, created_at)
                VALUES ('SYSTEM', 'ADMIN', 'SCHEDULE_CONFLICT', 'Titulo', 'Mensagem', NULL, 'Sistema', ?, CURRENT_TIMESTAMP(0))
                """, activeSourceKey);
    }
}
