package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class V15UserAccountLifecycleMigrationIntegrationTest {

    @Test
    void shouldApplyLifecycleSchemaOnCleanDatabase() {
        DataSource dataSource = createDataSource("v15_clean_database");

        MigrateResult result = migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(15, result.migrationsExecuted);
        assertSuccessfulMigration(dataSource, "15");
        assertColumnNullable(jdbcTemplate, "tb_person", "password");
        assertColumnNotNullable(jdbcTemplate, "tb_user_account", "token_version");
        assertCheckConstraintExists(jdbcTemplate, "tb_user_account", "ck_tb_user_account_token_version_non_negative");

        long personId = insertPersonWithoutPassword(jdbcTemplate, "34990000001");
        long accountId = insertAccountWithoutTokenVersion(jdbcTemplate, personId, "34990000001");

        assertEquals(0L, tokenVersionOf(jdbcTemplate, accountId));
        assertThrows(DataIntegrityViolationException.class, () -> insertAccountWithNegativeTokenVersion(
                jdbcTemplate,
                insertPersonWithoutPassword(jdbcTemplate, "34990000002"),
                "34990000002"
        ));
    }

    @Test
    void shouldUpgradeFromV14ToV15PreservingExistingAccountsWithTokenVersionZero() {
        DataSource dataSource = createDataSource("v15_upgrade_from_v14");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPersonWithPassword(jdbcTemplate, "34990000003", "$2a$10$existingHash");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", personId, operatorRoleId);

        migrateUntil(dataSource, "14");
        assertColumnDoesNotExist(jdbcTemplate, "tb_user_account", "token_version");

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(dataSource, "15");
        assertEquals(0L, tokenVersionByPersonId(jdbcTemplate, personId));
        jdbcTemplate.update("UPDATE tb_person SET password = NULL WHERE id = ?", personId);
        assertEquals(0, countRows(jdbcTemplate, "tb_person", "id", personId, "password IS NOT NULL"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE tb_user_account SET token_version = -1 WHERE person_id = ?", personId));
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

    // Alvo fixo em "15": este teste cobre especificamente a migration V15 e nao deve ser afetado por
    // migrations futuras (ex.: V16) adicionadas depois dela.
    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("15")
                .load()
                .migrate();
    }

    private void assertSuccessfulMigration(DataSource dataSource, String version) {
        MigrationInfo migrationInfo = Arrays.stream(Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load()
                        .info()
                        .applied())
                .filter(info -> info.getVersion() != null)
                .filter(info -> version.equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();

        assertEquals(MigrationState.SUCCESS, migrationInfo.getState());
    }

    private void assertColumnNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertColumnNullability(jdbcTemplate, tableName, columnName, "YES");
    }

    private void assertColumnNotNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertColumnNullability(jdbcTemplate, tableName, columnName, "NO");
    }

    private void assertColumnNullability(JdbcTemplate jdbcTemplate, String tableName, String columnName, String expected) {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                String.class,
                tableName,
                columnName
        );
        assertEquals(expected, nullable);
    }

    private void assertColumnDoesNotExist(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)",
                Integer.class,
                tableName,
                columnName
        );
        assertEquals(0, count == null ? 0 : count);
    }

    private void assertCheckConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class,
                tableName,
                constraintName
        );
        assertEquals(1, count == null ? 0 : count);
    }

    private long insertPersonWithoutPassword(JdbcTemplate jdbcTemplate, String phoneNumber) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', NULL)",
                "Person " + phoneNumber,
                phoneNumber
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPersonWithPassword(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                "Person " + phoneNumber,
                phoneNumber,
                password
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertAccountWithoutTokenVersion(JdbcTemplate jdbcTemplate, long personId, String username) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, 'hash', TRUE, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId,
                username
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE person_id = ?", Long.class, personId);
    }

    private void insertAccountWithNegativeTokenVersion(JdbcTemplate jdbcTemplate, long personId, String username) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, token_version, created_at, updated_at)
                VALUES (?, ?, 'hash', TRUE, -1, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """,
                personId,
                username
        );
    }

    private long existingRoleId(JdbcTemplate jdbcTemplate, String authority) {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_role WHERE authority = ?", Long.class, authority);
    }

    private long tokenVersionOf(JdbcTemplate jdbcTemplate, long accountId) {
        Long tokenVersion = jdbcTemplate.queryForObject(
                "SELECT token_version FROM tb_user_account WHERE id = ?", Long.class, accountId);
        return tokenVersion == null ? -1L : tokenVersion;
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
