package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura dedicada da migration V13: schema paralelo (tb_user_account/tb_user_account_role)
 * com ON DELETE CASCADE, unicidade e precisao de timestamp definitivas desta etapa. As checagens
 * gerais de existencia de tabela/constraint (banco limpo e "migrate novamente e idempotente") ja
 * sao cobertas por FlywayMigrationIntegrationTest; aqui o foco e comportamento (cascade,
 * unicidade, upgrade V12->V13 sem afetar dados existentes).
 */
class V13AddUserAccountParallelSchemaMigrationIntegrationTest {

    @Test
    void shouldUpgradeFromV12ToV13WithoutAffectingExistingPersonData() {
        DataSource dataSource = createDataSource("v13_upgrade_from_v12");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Upgrade Person");

        MigrateResult result = migrateAll(dataSource, "13");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(
                "Upgrade Person",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId)
        );
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
    }

    @Test
    void shouldCascadeDeleteUserAccountAndRolesWhenPersonIsDeleted() {
        DataSource dataSource = createDataSource("v13_cascade_person_delete");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Cascade Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34900000001", "hash");
        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertUserAccountRole(jdbcTemplate, accountId, roleId);

        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);

        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
    }

    @Test
    void shouldCascadeDeleteUserAccountRoleWhenUserAccountIsDeleted() {
        DataSource dataSource = createDataSource("v13_cascade_account_delete");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Cascade Account Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34900000002", "hash");
        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertUserAccountRole(jdbcTemplate, accountId, roleId);

        jdbcTemplate.update("DELETE FROM tb_user_account WHERE id = ?", accountId);

        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
    }

    @Test
    void shouldEnforceOnePersonOneAccountConstraint() {
        DataSource dataSource = createDataSource("v13_unique_person");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Duplicate Account Person");
        insertUserAccount(jdbcTemplate, personId, "34900000003", "hash-1");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertUserAccount(jdbcTemplate, personId, "34900000004", "hash-2"));
    }

    @Test
    void shouldEnforceUniqueUsernameConstraint() {
        DataSource dataSource = createDataSource("v13_unique_username");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long firstPersonId = insertPerson(jdbcTemplate, "Username Owner");
        long secondPersonId = insertPerson(jdbcTemplate, "Username Duplicator");
        insertUserAccount(jdbcTemplate, firstPersonId, "34900000005", "hash-1");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertUserAccount(jdbcTemplate, secondPersonId, "34900000005", "hash-2"));
    }

    @Test
    void shouldRejectUserAccountRoleReferencingMissingRole() {
        DataSource dataSource = createDataSource("v13_fk_missing_role");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Missing Role Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34900000006", "hash");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertUserAccountRole(jdbcTemplate, accountId, 999_999L));
    }

    @Test
    void shouldPersistTimestampsWithSecondPrecision() {
        DataSource dataSource = createDataSource("v13_second_precision");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Precision Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34900000007", "hash");

        java.sql.Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM tb_user_account WHERE id = ?", java.sql.Timestamp.class, accountId);
        assertEquals(0, createdAt.getNanos());
    }

    @Test
    void shouldRecreateSchemaWhenLegacyTablesDoNotExist() {
        DataSource dataSource = createDataSource("v13_missing_legacy_tables");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE tb_user_account_role");
        jdbcTemplate.execute("DROP TABLE tb_user_account");

        MigrateResult result = migrateAll(dataSource, "13");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
        long personId = insertPerson(jdbcTemplate, "Recreated Schema Person");
        insertUserAccount(jdbcTemplate, personId, "34900000100", "hash");
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
    }

    @Test
    void shouldFailBeforeDroppingAndBlockV14WhenLegacyUserAccountHasRows() {
        DataSource dataSource = createDataSource("v13_legacy_user_account_filled");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long personId = insertPerson(jdbcTemplate, "Legacy Filled Person");
        long legacyAccountId = insertUserAccount(jdbcTemplate, personId, "34900000101", "legacy-hash-must-not-leak");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "tb_user_account"));
        assertTrue(hasMessageContaining(exception, "1"));
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
    void shouldFailBeforeDroppingAndBlockV14WhenLegacyUserAccountRoleHasRows() {
        // tb_user_account_role tem FK para tb_user_account (ja na V3): nao ha como uma role legada
        // existir sem uma conta legada associada, entao este cenario tambem exercita a checagem de
        // tb_user_account (verificada primeiro) - o importante e que ambas as tabelas permanecem
        // intactas e a migracao nao prossiga para V14.
        DataSource dataSource = createDataSource("v13_legacy_user_account_role_filled");
        migrateUntil(dataSource, "12");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long personId = insertPerson(jdbcTemplate, "Legacy Role Filled Person");
        long legacyAccountId = insertUserAccount(jdbcTemplate, personId, "34900000102", "hash");
        long roleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        insertUserAccountRole(jdbcTemplate, legacyAccountId, roleId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource, "14"));

        assertTrue(hasMessageContaining(exception, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "13"));
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, "14"));
    }

    @Test
    void shouldUseTimestampWithZeroPrecisionColumnTypeOnH2() {
        DataSource dataSource = createDataSource("v13_h2_column_type");
        migrateAll(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        java.util.Map<String, Object> createdAtColumn = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'tb_user_account' AND LOWER(column_name) = 'created_at'");
        assertEquals("timestamp", String.valueOf(createdAtColumn.get("DATA_TYPE")).toLowerCase(java.util.Locale.ROOT));
        assertEquals(0, ((Number) createdAtColumn.get("DATETIME_PRECISION")).intValue());
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

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
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
                name,
                phoneNumber
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertUserAccount(JdbcTemplate jdbcTemplate, long personId, String username, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId,
                username,
                passwordHash
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE username = ?", Long.class, username);
    }

    private void insertUserAccountRole(JdbcTemplate jdbcTemplate, long accountId, long roleId) {
        jdbcTemplate.update(
                "INSERT INTO tb_user_account_role (user_account_id, role_id) VALUES (?, ?)",
                accountId,
                roleId
        );
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
