package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura dedicada da migration V14: validacao pre-backfill, copia exata de telefone/senha/roles
 * legados para tb_user_account/tb_user_account_role e timestamp unico e deterministico por execucao.
 */
class V14BackfillUserAccountsMigrationIntegrationTest {

    @Test
    void shouldBackfillAccountOnCleanDatabaseWithNoPeople() {
        DataSource dataSource = createDataSource("v14_clean_database");

        MigrateResult result = migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertTrue(result.migrationsExecuted >= 2);
        assertSuccessfulMigration(jdbcTemplate, "14");
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account_role"));
    }

    @Test
    void shouldBackfillPreservingExactHashAndRolesForEveryExistingPerson() {
        DataSource dataSource = createDataSource("v14_backfill_parity");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long adminId = insertPerson(jdbcTemplate, "34900001001", "$2a$10$adminHash");
        long operatorId = insertPerson(jdbcTemplate, "34900001002", "$2a$10$operatorHash");
        long operatorRoleId = existingRoleId(jdbcTemplate, "ROLE_OPERATOR");
        long adminRoleId = existingRoleId(jdbcTemplate, "ROLE_ADMIN");
        insertPersonRole(jdbcTemplate, adminId, operatorRoleId);
        insertPersonRole(jdbcTemplate, adminId, adminRoleId);
        insertPersonRole(jdbcTemplate, operatorId, operatorRoleId);

        migrateAll(dataSource);

        assertEquals(2, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(3, countRows(jdbcTemplate, "tb_user_account_role"));
        assertEquals("34900001001", usernameOf(jdbcTemplate, adminId));
        assertEquals("$2a$10$adminHash", passwordHashOf(jdbcTemplate, adminId));
        assertEquals("34900001002", usernameOf(jdbcTemplate, operatorId));
        assertEquals("$2a$10$operatorHash", passwordHashOf(jdbcTemplate, operatorId));
        assertEquals(List.of("ROLE_ADMIN", "ROLE_OPERATOR"), roleAuthoritiesOf(jdbcTemplate, adminId));
        assertEquals(List.of("ROLE_OPERATOR"), roleAuthoritiesOf(jdbcTemplate, operatorId));
        assertTrue(enabledOf(jdbcTemplate, adminId));
        assertTrue(enabledOf(jdbcTemplate, operatorId));
    }

    @Test
    void shouldUseSingleDeterministicTimestampForEveryAccountInTheSameExecution() {
        DataSource dataSource = createDataSource("v14_deterministic_timestamp");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long firstId = insertPerson(jdbcTemplate, "34900002001", "hash-1");
        long secondId = insertPerson(jdbcTemplate, "34900002002", "hash-2");

        migrateAll(dataSource);

        Timestamp firstCreatedAt = createdAtOf(jdbcTemplate, firstId);
        Timestamp secondCreatedAt = createdAtOf(jdbcTemplate, secondId);
        assertEquals(firstCreatedAt, secondCreatedAt);
        assertEquals(firstCreatedAt, updatedAtOf(jdbcTemplate, firstId));
        assertEquals(0, firstCreatedAt.getNanos());
    }

    @Test
    void shouldFailAndRollBackWhenPhoneNumberIsNullOrEmpty() {
        DataSource dataSource = createDataSource("v14_invalid_phone");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long nullPhoneId = insertPersonWithRawPhone(jdbcTemplate, null, "hash-1");
        long blankPhoneId = insertPersonWithRawPhone(jdbcTemplate, "", "hash-2");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "telefone ou senha ausente"));
        assertTrue(hasMessageContaining(exception, "id=" + nullPhoneId) || hasMessageContaining(exception, String.valueOf(nullPhoneId)));
        assertTrue(hasMessageContaining(exception, String.valueOf(blankPhoneId)));
        assertFailedMigrationRecorded(jdbcTemplate, "14");
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
    }

    @Test
    void shouldFailAndRollBackWhenPasswordIsNullOrEmpty() {
        DataSource dataSource = createDataSource("v14_invalid_password");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long nullPasswordId = insertPersonWithRawPassword(jdbcTemplate, "34900003001", null);
        long blankPasswordId = insertPersonWithRawPassword(jdbcTemplate, "34900003002", "");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "telefone ou senha ausente"));
        assertTrue(hasMessageContaining(exception, String.valueOf(nullPasswordId)));
        assertTrue(hasMessageContaining(exception, String.valueOf(blankPasswordId)));
        assertFailedMigrationRecorded(jdbcTemplate, "14");
        assertEquals(0, countRows(jdbcTemplate, "tb_user_account"));
    }

    @Test
    void shouldNotExposePasswordValueInFailureMessage() {
        DataSource dataSource = createDataSource("v14_no_password_leak");
        migrateUntil(dataSource, "13");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertPersonWithRawPhone(jdbcTemplate, null, "$2a$10$secretHashValueThatMustNotLeak");

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(!hasMessageContaining(exception, "secretHashValueThatMustNotLeak"));
    }

    private DataSource createDataSource(String namePrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + namePrefix + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("14")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                "Person " + phoneNumber,
                phoneNumber,
                password
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private long insertPersonWithRawPhone(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        String uniqueMarker = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                uniqueMarker,
                phoneNumber,
                password
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE name = ?", Long.class, uniqueMarker);
    }

    private long insertPersonWithRawPassword(JdbcTemplate jdbcTemplate, String phoneNumber, String password) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password) VALUES (?, ?, '1990-01-01', ?)",
                "Person " + phoneNumber,
                phoneNumber,
                password
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private void insertPersonRole(JdbcTemplate jdbcTemplate, long personId, long roleId) {
        jdbcTemplate.update("INSERT INTO tb_person_role(person_id, role_id) VALUES (?, ?)", personId, roleId);
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

    private boolean enabledOf(JdbcTemplate jdbcTemplate, long personId) {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM tb_user_account WHERE person_id = ?", Boolean.class, personId);
        return Boolean.TRUE.equals(enabled);
    }

    private Timestamp createdAtOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForObject("SELECT created_at FROM tb_user_account WHERE person_id = ?", Timestamp.class, personId);
    }

    private Timestamp updatedAtOf(JdbcTemplate jdbcTemplate, long personId) {
        return jdbcTemplate.queryForObject("SELECT updated_at FROM tb_user_account WHERE person_id = ?", Timestamp.class, personId);
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
                String.class,
                personId
        );
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        assertEquals(1, countSuccessfulMigration(jdbcTemplate, version));
    }

    private void assertFailedMigrationRecorded(JdbcTemplate jdbcTemplate, String version) {
        assertEquals(0, countSuccessfulMigration(jdbcTemplate, version));
        assertEquals(1, countFailedMigration(jdbcTemplate, version));
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
}
