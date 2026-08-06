package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cobertura da migration V18 especificamente contra MySQL 8.4 real (nao apenas H2): confirma que o
 * ALTER TABLE ... DROP FOREIGN KEY / ADD CONSTRAINT ... ON DELETE RESTRICT e a UNIQUE de
 * tb_user_account_role sao validos para o dialeto MySQL, que as quatro categorias de inconsistencia
 * legada tambem sao detectadas com dados reais em MySQL antes de qualquer DDL, e que nenhuma remocao
 * parcial ocorre quando a validacao falha. Compatibilidade com Hibernate ddl-auto=validate sobre o
 * schema pos-V18 e confirmada indiretamente por qualquer teste MySQL-gated que suba o contexto Spring
 * completo sobre este mesmo schema (mesmo padrao ja adotado por V15/V17).
 */
class V18RemoveLegacyPersonAuthenticationMySqlIntegrationTest {

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
    void shouldUpgradeFromV17ToV18OnRealMySqlPreservingDataAndReplacingConstraints() throws SQLException {
        DataSource dataSource = createDatabase("v18my_upgrade_valid");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateUntil(dataSource, "17");

        long personId = insertPerson(jdbcTemplate, "Pessoa Com Conta MySQL", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34988880001", "encoded-hash");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertUserAccountRole(jdbcTemplate, accountId, operatorRoleId);
        insertPersonRole(jdbcTemplate, personId, operatorRoleId);

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertFalse(tableExists(jdbcTemplate, "tb_person_role"));
        assertEquals(0, countColumn(jdbcTemplate, "tb_person", "password"));
        assertTrue(tableExists(jdbcTemplate, "tb_role"));
        assertTrue(tableExists(jdbcTemplate, "tb_user_account"));
        assertTrue(tableExists(jdbcTemplate, "tb_user_account_role"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(
                "Pessoa Com Conta MySQL",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId));
        assertEquals(1, countConstraint(jdbcTemplate, "tb_user_account_role", "uk_tb_user_account_role_user_account"));

        // FK UserAccount->Person com ON DELETE RESTRICT (era CASCADE desde V13).
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId));

        // UNIQUE(user_account_id): uma segunda role para a mesma conta e rejeitada.
        long adminRoleId = existingRoleId(jdbcTemplate, "ROLE_ADMIN");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertUserAccountRole(jdbcTemplate, accountId, adminRoleId));
    }

    @Test
    void shouldRejectUpgradeWhenLegacyRoleExistsWithoutAccountOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v18my_role_without_account");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateUntil(dataSource, "17");

        long personId = insertPersonWithoutPassword(jdbcTemplate, "Role Orfa MySQL", "34988880002");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertPersonRole(jdbcTemplate, personId, operatorRoleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "LEGACY_CREDENTIAL_WITHOUT_ACCOUNT"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    @Test
    void shouldRejectUpgradeWhenAccountRoleAuthorityIsUnknownOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v18my_unknown_authority");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateUntil(dataSource, "17");

        long personId = insertPerson(jdbcTemplate, "Authority Desconhecida MySQL", "encoded-hash");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34988880003", "encoded-hash");
        long unknownRoleId = insertRole(jdbcTemplate, "ROLE_GUEST");
        insertUserAccountRole(jdbcTemplate, accountId, unknownRoleId);
        insertPersonRole(jdbcTemplate, personId, unknownRoleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "USER_ACCOUNT_ROLE_INVALID"));
        assertFalse(hasMessageContaining(exception, "LEGACY_ROLE_MISMATCH"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    @Test
    void shouldRejectUpgradeWhenLegacyPasswordDivergesFromAccountHashOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v18my_password_mismatch");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateUntil(dataSource, "17");

        long personId = insertPerson(jdbcTemplate, "Hash Divergente MySQL", "legacy-hash-real-mysql");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34988880004", "different-hash-real-mysql");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertUserAccountRole(jdbcTemplate, accountId, operatorRoleId);
        insertPersonRole(jdbcTemplate, personId, operatorRoleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "LEGACY_PASSWORD_MISMATCH"));
        assertTrue(hasMessageContaining(exception, "personIds=[" + personId + "]"));
        assertFalse(hasMessageContaining(exception, "legacy-hash-real-mysql"));
        assertFalse(hasMessageContaining(exception, "different-hash-real-mysql"));
        assertNoDestructiveChangeApplied(jdbcTemplate);
    }

    private void assertNoDestructiveChangeApplied(JdbcTemplate jdbcTemplate) {
        assertTrue(tableExists(jdbcTemplate, "tb_person_role"));
        assertEquals(1, countColumn(jdbcTemplate, "tb_person", "password"));
        assertEquals(0, countConstraint(jdbcTemplate, "tb_user_account_role", "uk_tb_user_account_role_user_account"));
    }

    private boolean hasMessageContaining(Throwable throwable, String expectedText) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedText)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    // Alvo fixo em "18": este teste cobre especificamente a migration V18 e nao deve ser afetado por
    // migrations futuras adicionadas depois dela.
    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("18")
                .load()
                .migrate();
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private int countColumn(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return count == null ? 0 : count;
    }

    private int countConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class, tableName, constraintName);
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String legacyPassword) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                name, uniquePhone(), legacyPassword);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class, name);
    }

    private long insertPersonWithoutPassword(JdbcTemplate jdbcTemplate, String name, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', NULL)",
                name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertUserAccount(JdbcTemplate jdbcTemplate, long personId, String username, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account(person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId, username, passwordHash);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE person_id = ?", Long.class, personId);
    }

    private void insertUserAccountRole(JdbcTemplate jdbcTemplate, long accountId, long roleId) {
        jdbcTemplate.update(
                "INSERT INTO tb_user_account_role(user_account_id, role_id) VALUES (?, ?)", accountId, roleId);
    }

    private void insertPersonRole(JdbcTemplate jdbcTemplate, long personId, long roleId) {
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", personId, roleId);
    }

    private long insertRole(JdbcTemplate jdbcTemplate, String authority) {
        jdbcTemplate.update("INSERT INTO tb_role(authority) VALUES (?)", authority);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3499" + String.format("%07d", suffix);
    }
}
