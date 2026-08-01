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
import java.sql.Timestamp;
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
 * Prova, contra MySQL 8.4 real (nao H2), os cenarios exigidos pela validacao desta branch que os
 * testes de migration existentes (H2-only, por construcao: {@code jdbc:h2:mem:...}) nao cobrem:
 * V13 nao destrutiva com tabelas legadas ausentes/vazias/preenchidas, tipos temporais DATETIME(0)
 * no MySQL, upgrade completo V12->V14 com paridade de backfill, e rejeicao de dados legados
 * invalidos. Usa uma database MySQL isolada por metodo (nome unico), no mesmo container/rede
 * descartaveis desta validacao, e e' automaticamente ignorado quando MySQL nao estiver acessivel
 * (mesmas propriedades documentadas em UserAccountRoleSyncConcurrencyMySqlIntegrationTest).
 */
class V13V14MigrationMySqlIntegrationTest {

    private static String host;
    private static String port;
    private static String username;
    private static String password;
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
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password)) {
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

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    // ---------------------------------------------------------------- V13: tabelas ausentes/vazias

    @Test
    void shouldRecreateSchemaWhenLegacyTablesDoNotExist() throws SQLException {
        DataSource dataSource = createDatabase("v13my_missing_tables");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE tb_user_account_role");
        jdbcTemplate.execute("DROP TABLE tb_user_account");

        MigrateResult result = migrateAll(dataSource, "13");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
        long personId = insertPerson(jdbcTemplate, "MySQL Recreated Schema Person");
        insertUserAccount(jdbcTemplate, personId, "34900010001", "hash");
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
    }

    @Test
    void shouldRecreateSchemaWhenLegacyTablesExistAndAreEmpty() throws SQLException {
        DataSource dataSource = createDatabase("v13my_empty_tables");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));

        MigrateResult result = migrateAll(dataSource, "13");

        assertEquals(1, result.migrationsExecuted);
        long personId = insertPerson(jdbcTemplate, "MySQL Empty Legacy Person");
        insertUserAccount(jdbcTemplate, personId, "34900010002", "hash");
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
    }

    // ---------------------------------------------------------------- V13: dados legados presentes

    @Test
    void shouldFailBeforeDroppingAndBlockV14WhenLegacyUserAccountHasRows() throws SQLException {
        DataSource dataSource = createDatabase("v13my_account_filled");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long personId = insertPerson(jdbcTemplate, "MySQL Legacy Filled Person");
        long legacyAccountId = insertUserAccount(jdbcTemplate, personId, "34900010003", "legacy-hash-must-not-leak");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "tb_user_account"));
        assertFalse(hasMessageContaining(exception, "legacy-hash-must-not-leak"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(
                "legacy-hash-must-not-leak",
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM tb_user_account WHERE id = ?", String.class, legacyAccountId));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "13"));
        assertEquals(1, countFailedMigration(jdbcTemplate, "13"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "14"));
    }

    @Test
    void shouldFailBeforeDroppingAndBlockV14WhenLegacyUserAccountRoleHasRows() throws SQLException {
        DataSource dataSource = createDatabase("v13my_role_filled");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long personId = insertPerson(jdbcTemplate, "MySQL Legacy Role Filled Person");
        long legacyAccountId = insertUserAccount(jdbcTemplate, personId, "34900010004", "hash");
        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        jdbcTemplate.update(
                "INSERT INTO tb_user_account_role (user_account_id, role_id) VALUES (?, ?)", legacyAccountId, roleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "13"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "14"));
    }

    @Test
    void shouldFailBeforeDroppingAndBlockV14WhenBothLegacyTablesHaveRows() throws SQLException {
        DataSource dataSource = createDatabase("v13my_both_filled");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long firstPersonId = insertPerson(jdbcTemplate, "MySQL Legacy Both A");
        long secondPersonId = insertPerson(jdbcTemplate, "MySQL Legacy Both B");
        long firstAccountId = insertUserAccount(jdbcTemplate, firstPersonId, "34900010005", "hash-a");
        insertUserAccount(jdbcTemplate, secondPersonId, "34900010006", "hash-b");
        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        jdbcTemplate.update(
                "INSERT INTO tb_user_account_role (user_account_id, role_id) VALUES (?, ?)", firstAccountId, roleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "tb_user_account"));
        assertEquals(2, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "13"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "14"));
    }

    // ---------------------------------------------------------------- Tipos temporais (MySQL real)

    @Test
    void shouldUseDatetimeZeroPrecisionColumnTypeOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v13my_column_type");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        for (String column : List.of("created_at", "updated_at")) {
            Map<String, Object> columnMeta = jdbcTemplate.queryForMap(
                    "SELECT DATA_TYPE, DATETIME_PRECISION, IS_NULLABLE FROM information_schema.columns "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_account' AND COLUMN_NAME = ?",
                    column);
            assertEquals("datetime", String.valueOf(columnMeta.get("DATA_TYPE")));
            assertEquals(0, ((Number) columnMeta.get("DATETIME_PRECISION")).intValue());
            assertEquals("NO", String.valueOf(columnMeta.get("IS_NULLABLE")));
        }
    }

    // ---------------------------------------------------------------- Upgrade completo V12->V14

    @Test
    void shouldUpgradeV12ToV14WithFullBackfillParityOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v13v14my_upgrade_parity");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long adminId = insertPerson(jdbcTemplate, "34900011001", "$2a$10$mysqlAdminHash");
        long operatorId = insertPerson(jdbcTemplate, "34900011002", "$2a$10$mysqlOperatorHash");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        long adminRoleId = existingRoleId(jdbcTemplate, "ROLE_ADMIN");
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", adminId, operatorRoleId);
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", adminId, adminRoleId);
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", operatorId, operatorRoleId);

        MigrateResult result = migrateAll(dataSource, "14");

        assertEquals(2, result.migrationsExecuted);
        assertEquals(2, countRows(jdbcTemplate, "tb_person"));
        assertEquals(2, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(3, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals("34900011001", usernameOf(jdbcTemplate, adminId));
        assertEquals("$2a$10$mysqlAdminHash", passwordHashOf(jdbcTemplate, adminId));
        assertEquals(List.of("ROLE_ADMIN", "ROLE_OPERATOR"), roleAuthoritiesOf(jdbcTemplate, adminId));
        assertEquals(List.of("ROLE_OPERATOR"), roleAuthoritiesOf(jdbcTemplate, operatorId));
        Integer activeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person WHERE active = TRUE", Integer.class);
        assertEquals(2, activeCount);
        Integer enabledCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_user_account WHERE enabled = TRUE", Integer.class);
        assertEquals(2, enabledCount);
        Timestamp adminCreatedAt = createdAtOf(jdbcTemplate, adminId);
        Timestamp operatorCreatedAt = createdAtOf(jdbcTemplate, operatorId);
        assertEquals(adminCreatedAt, operatorCreatedAt);
        assertEquals(0, adminCreatedAt.getNanos());
        Integer orphanAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user_account ua LEFT JOIN tb_person p ON p.id = ua.person_id WHERE p.id IS NULL",
                Integer.class);
        assertEquals(0, orphanAccounts);
    }

    // ---------------------------------------------------------------- Constraints e indices (MySQL real)

    @Test
    void shouldHaveAllConstraintsAndCascadesAndIndexesOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v13my_constraints");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // MySQL sempre reporta o nome da constraint de PRIMARY KEY como "PRIMARY", independente do
        // nome custom dado na DDL (pk_tb_user_account / pk_tb_user_account_role) - diferente do H2,
        // que preserva o nome original. Comportamento documentado do MySQL, nao um defeito.
        assertConstraintExists(jdbcTemplate, "tb_user_account", "PRIMARY");
        assertConstraintExists(jdbcTemplate, "tb_user_account", "uk_tb_user_account_person_id");
        assertConstraintExists(jdbcTemplate, "tb_user_account", "uk_tb_user_account_username");
        assertConstraintExists(jdbcTemplate, "tb_user_account", "fk_tb_user_account_person");
        assertConstraintExists(jdbcTemplate, "tb_user_account_role", "PRIMARY");
        assertConstraintExists(jdbcTemplate, "tb_user_account_role", "fk_tb_user_account_role_user_account");
        assertConstraintExists(jdbcTemplate, "tb_user_account_role", "fk_tb_user_account_role_role");

        assertEquals("CASCADE", deleteRuleOf(jdbcTemplate, "fk_tb_user_account_person"));
        assertEquals("CASCADE", deleteRuleOf(jdbcTemplate, "fk_tb_user_account_role_user_account"));

        Integer roleIdIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'tb_user_account_role' AND COLUMN_NAME = 'role_id' AND SEQ_IN_INDEX = 1",
                Integer.class);
        assertTrue(roleIdIndexCount >= 1);

        Integer redundantIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.statistics WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'tb_user_account_role'",
                Integer.class);
        assertEquals(2, redundantIndexes); // PRIMARY (user_account_id, role_id) + idx_tb_user_account_role_role_id
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?",
                Integer.class, tableName, constraintName);
        assertEquals(1, count);
    }

    private String deleteRuleOf(JdbcTemplate jdbcTemplate, String constraintName) {
        return jdbcTemplate.queryForObject(
                "SELECT DELETE_RULE FROM information_schema.referential_constraints "
                        + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = ?",
                String.class, constraintName);
    }

    // ---------------------------------------------------------------- Dados legados invalidos (backfill)

    @Test
    void shouldFailAndRollBackBackfillWhenPhoneOrPasswordIsMissingOnRealMySql() throws SQLException {
        DataSource dataSource = createDatabase("v14my_invalid_legacy_data");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long nullPhoneId = insertPersonWithRawPhone(jdbcTemplate, null, "hash-1");
        long blankPasswordId = insertPersonWithRawPhone(jdbcTemplate, "34900012000", "");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "telefone ou senha ausente"));
        assertTrue(hasMessageContaining(exception, String.valueOf(nullPhoneId)));
        assertTrue(hasMessageContaining(exception, String.valueOf(blankPasswordId)));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "14"));
    }

    // ---------------------------------------------------------------- Helpers

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

    private long insertPerson(JdbcTemplate jdbcTemplate, String name) {
        String phoneNumber = "3495" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', 'encoded-password')",
                name, phoneNumber);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                "Person " + phoneNumber, phoneNumber, password);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPersonWithRawPhone(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        String uniqueMarker = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                uniqueMarker, phoneNumber, password);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE name = ?", Long.class, uniqueMarker);
    }

    private long insertUserAccount(JdbcTemplate jdbcTemplate, long personId, String username, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId, username, passwordHash);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE username = ?", Long.class, username);
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private String usernameOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForObject("SELECT username FROM tb_user_account WHERE person_id = ?", String.class, personId);
    }

    private String passwordHashOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM tb_user_account WHERE person_id = ?", String.class, personId);
    }

    private Timestamp createdAtOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForObject("SELECT created_at FROM tb_user_account WHERE person_id = ?", Timestamp.class, personId);
    }

    private List<String> roleAuthoritiesOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.authority
                FROM tb_user_account ua
                JOIN tb_user_account_role uar ON uar.user_account_id = ua.id
                JOIN tb_role r ON r.id = uar.role_id
                WHERE ua.person_id = ?
                ORDER BY r.authority
                """,
                String.class, personId);
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

    private int countSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE", Integer.class, version);
        return count == null ? 0 : count;
    }

    private int countFailedMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = FALSE", Integer.class, version);
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
