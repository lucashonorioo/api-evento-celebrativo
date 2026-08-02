package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
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

class V15UserAccountLifecycleMySqlIntegrationTest {

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
    void shouldApplyLifecycleSchemaOnCleanMySqlDatabase() throws SQLException {
        DataSource dataSource = createDatabase("v15my_clean");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(15, result.migrationsExecuted);
        assertEquals("YES", columnValue(jdbcTemplate, "tb_person", "password", "IS_NULLABLE"));
        assertEquals("NO", columnValue(jdbcTemplate, "tb_user_account", "token_version", "IS_NULLABLE"));
        assertEquals("0", columnValue(jdbcTemplate, "tb_user_account", "token_version", "COLUMN_DEFAULT"));
        assertEquals(1, countConstraint(jdbcTemplate, "tb_user_account", "ck_tb_user_account_token_version_non_negative"));

        long personId = insertPersonWithoutPassword(jdbcTemplate, "34991110001");
        insertAccountWithoutTokenVersion(jdbcTemplate, personId, "34991110001");

        assertEquals(0L, tokenVersionByPersonId(jdbcTemplate, personId));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE tb_user_account SET token_version = -1 WHERE person_id = ?", personId));
    }

    @Test
    void shouldUpgradeV14ToV15OnRealMySqlPreservingExistingAccounts() throws SQLException {
        DataSource dataSource = createDatabase("v15my_upgrade");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateUntil(dataSource, "13");

        long personId = insertPersonWithPassword(jdbcTemplate, "34991110002", "$2a$10$existingMysqlHash");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", personId, operatorRoleId);

        migrateUntil(dataSource, "14");
        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertEquals(0L, tokenVersionByPersonId(jdbcTemplate, personId));
        jdbcTemplate.update("UPDATE tb_person SET password = NULL WHERE id = ?", personId);
        assertEquals(0, countRows(jdbcTemplate, "tb_person", "id", personId, "password IS NOT NULL"));
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

    private long insertPersonWithoutPassword(JdbcTemplate jdbcTemplate, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', NULL)",
                "Person " + phoneNumber,
                phoneNumber
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPersonWithPassword(JdbcTemplate jdbcTemplate, String phoneNumber, String encodedPassword) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                "Person " + phoneNumber,
                phoneNumber,
                encodedPassword
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private void insertAccountWithoutTokenVersion(JdbcTemplate jdbcTemplate, long personId, String username) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, 'hash', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId,
                username
        );
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private long tokenVersionByPersonId(JdbcTemplate jdbcTemplate, long personId) {
        Long tokenVersion = jdbcTemplate.queryForObject(
                "SELECT token_version FROM tb_user_account WHERE person_id = ?", Long.class, personId);
        return tokenVersion == null ? -1L : tokenVersion;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName, String idColumn, long id, String extraCondition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + idColumn + " = ? AND " + extraCondition,
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }
}
