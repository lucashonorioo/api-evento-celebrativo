package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura dedicada da migration V16: schema de notificacoes (tb_notification,
 * tb_notification_recipient, tb_notification_ministry), constraints, FKs, indices e precisao de
 * timestamp no H2. V1-V15 permanecem intactas (verificado indiretamente pelo upgrade a partir de
 * V15 preservando dados existentes).
 */
class V16CreateNotificationManagementMigrationIntegrationTest {

    @Test
    void shouldUpgradeFromV15ToV16WithoutAffectingExistingData() {
        DataSource dataSource = createDataSource("v16_upgrade_from_v15");
        migrateUntil(dataSource, "15");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Upgrade Person V16");

        MigrateResult result = migrateAll(dataSource, "16");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(
                "Upgrade Person V16",
                jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId)
        );
        assertEquals(0, countRows(jdbcTemplate, "tb_notification"));
        assertEquals(0, countRows(jdbcTemplate, "tb_notification_recipient"));
        assertEquals(0, countRows(jdbcTemplate, "tb_notification_ministry"));
    }

    @Test
    void shouldCreateNotificationSchemaOnCleanDatabase() {
        DataSource dataSource = createDataSource("v16_clean_database");
        MigrateResult result = migrateAll(dataSource, "16");

        assertEquals(16, result.migrationsExecuted);
    }

    @Test
    void shouldEnforceOriginSenderCheckConstraint() {
        DataSource dataSource = createDataSource("v16_origin_sender_check");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // ADMIN exige sender preenchido.
        assertThrows(DataIntegrityViolationException.class, () -> insertNotification(
                jdbcTemplate, "ADMIN", "GLOBAL", null, "Sistema"));

        // SYSTEM exige sender nulo.
        long personId = insertPerson(jdbcTemplate, "Sender Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34992220001");
        assertThrows(DataIntegrityViolationException.class, () -> insertNotification(
                jdbcTemplate, "SYSTEM", "GLOBAL", accountId, "Sistema"));

        // Combinacoes validas nao lancam.
        insertNotification(jdbcTemplate, "ADMIN", "GLOBAL", accountId, "Admin Nome");
        insertNotification(jdbcTemplate, "SYSTEM", "GLOBAL", null, "Sistema");
        assertEquals(2, countRows(jdbcTemplate, "tb_notification"));
    }

    @Test
    void shouldEnforceReferenceAndSourcePairConstraints() {
        DataSource dataSource = createDataSource("v16_reference_source_pair");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id,
                     sender_name_snapshot, reference_type, reference_id, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', 'T', 'M', NULL, 'Sistema', 'CELEBRATION_EVENT', NULL, CURRENT_TIMESTAMP(0))
                """));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id,
                     sender_name_snapshot, source_type, source_key, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', 'T', 'M', NULL, 'Sistema', NULL, 'ev1:pe1', CURRENT_TIMESTAMP(0))
                """));

        jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id,
                     sender_name_snapshot, reference_type, reference_id, source_type, source_key, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', 'T', 'M', NULL, 'Sistema',
                        'CELEBRATION_EVENT', 42, 'SCHEDULE_UNAVAILABILITY_CONFLICT', 'ev1:pe1', CURRENT_TIMESTAMP(0))
                """);
        assertEquals(1, countRows(jdbcTemplate, "tb_notification"));
    }

    @Test
    void shouldEnforceRecipientUniqueConstraint() {
        DataSource dataSource = createDataSource("v16_recipient_unique");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Recipient Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34992220002");
        long notificationId = insertNotification(jdbcTemplate, "SYSTEM", "GLOBAL", null, "Sistema");

        insertRecipient(jdbcTemplate, notificationId, accountId, "Recipient Person");
        assertThrows(DataIntegrityViolationException.class, () ->
                insertRecipient(jdbcTemplate, notificationId, accountId, "Recipient Person"));
    }

    @Test
    void shouldCascadeDeleteRecipientsAndMinistriesWhenNotificationIsDeletedButRestrictAccountDeletion() {
        DataSource dataSource = createDataSource("v16_cascade_and_restrict");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Cascade Person");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34992220003");
        long notificationId = insertNotification(jdbcTemplate, "SYSTEM", "MINISTRY", null, "Sistema");
        insertRecipient(jdbcTemplate, notificationId, accountId, "Cascade Person");
        jdbcTemplate.update(
                "INSERT INTO tb_notification_ministry (notification_id, ministry_type) VALUES (?, 'READER')",
                notificationId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "DELETE FROM tb_user_account WHERE id = ?", accountId));

        jdbcTemplate.update("DELETE FROM tb_notification WHERE id = ?", notificationId);
        assertEquals(0, countRows(jdbcTemplate, "tb_notification_recipient"));
        assertEquals(0, countRows(jdbcTemplate, "tb_notification_ministry"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
    }

    @Test
    void shouldRollBackPersonDeletionWhenCascadeToUserAccountIsRestrictedByNotificationHistory() {
        DataSource dataSource = createDataSource("v16_person_cascade_restrict");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long personId = insertPerson(jdbcTemplate, "Person Cascade Restrict");
        long accountId = insertUserAccount(jdbcTemplate, personId, "34992220004");
        long notificationId = insertNotification(jdbcTemplate, "SYSTEM", "GLOBAL", null, "Sistema");
        insertRecipient(jdbcTemplate, notificationId, accountId, "Person Cascade Restrict");

        // tb_user_account tem ON DELETE CASCADE a partir de tb_person (V13), mas tb_notification_recipient
        // tem ON DELETE RESTRICT a partir de tb_user_account; a checagem de FK vale mesmo quando a exclusao
        // da conta e apenas um efeito cascata da exclusao da pessoa, entao a transacao inteira deve reverter.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "DELETE FROM tb_person WHERE id = ?", personId));

        assertEquals(1, countRows(jdbcTemplate, "tb_person"));
        assertEquals(1, countRows(jdbcTemplate, "tb_user_account"));
        assertEquals(1, countRows(jdbcTemplate, "tb_notification_recipient"));
    }

    @Test
    void shouldExposeExpectedConstraintsFksAndIndexes() {
        DataSource dataSource = createDataSource("v16_metadata");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertConstraintExists(jdbcTemplate, "tb_notification", "ck_tb_notification_origin_sender");
        assertConstraintExists(jdbcTemplate, "tb_notification", "ck_tb_notification_reference_pair");
        assertConstraintExists(jdbcTemplate, "tb_notification", "ck_tb_notification_source_pair");
        assertConstraintExists(jdbcTemplate, "tb_notification_recipient", "uk_tb_notification_recipient_notification_user_account");
        assertConstraintExists(jdbcTemplate, "tb_notification_ministry", "pk_tb_notification_ministry");

        assertEquals("CASCADE", deleteRuleOf(jdbcTemplate, "fk_tb_notification_recipient_notification"));
        assertEquals("RESTRICT", deleteRuleOf(jdbcTemplate, "fk_tb_notification_recipient_user_account"));
        assertEquals("RESTRICT", deleteRuleOf(jdbcTemplate, "fk_tb_notification_sender"));
        assertEquals("CASCADE", deleteRuleOf(jdbcTemplate, "fk_tb_notification_ministry_notification"));

        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_created_at_id"));
        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_sender_created_at_id"));
        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_source"));
        assertTrue(indexExists(jdbcTemplate, "tb_notification_recipient", "idx_tb_notification_recipient_account_read_notification"));
        assertTrue(indexExists(jdbcTemplate, "tb_notification_recipient", "idx_tb_notification_recipient_notification_read_name_id"));
    }

    @Test
    void shouldUseTimestampWithZeroPrecisionColumnTypeOnH2() {
        DataSource dataSource = createDataSource("v16_h2_column_type");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Map<String, Object> createdAtColumn = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'tb_notification' AND LOWER(column_name) = 'created_at'");
        assertEquals("timestamp", String.valueOf(createdAtColumn.get("DATA_TYPE")).toLowerCase(Locale.ROOT));
        assertEquals(0, ((Number) createdAtColumn.get("DATETIME_PRECISION")).intValue());

        Map<String, Object> readAtColumn = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'tb_notification_recipient' AND LOWER(column_name) = 'read_at'");
        assertEquals("timestamp", String.valueOf(readAtColumn.get("DATA_TYPE")).toLowerCase(Locale.ROOT));
        assertEquals(0, ((Number) readAtColumn.get("DATETIME_PRECISION")).intValue());
    }

    @Test
    void shouldRejectTitleAndMessageLongerThanColumnLength() {
        DataSource dataSource = createDataSource("v16_column_lengths");
        migrateAll(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        String title121 = "T".repeat(121);
        String message2001 = "M".repeat(2001);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', ?, 'M', NULL, 'Sistema', CURRENT_TIMESTAMP(0))
                """, title121));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', 'T', ?, NULL, 'Sistema', CURRENT_TIMESTAMP(0))
                """, message2001));
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class, tableName, constraintName);
        assertEquals(1, count == null ? 0 : count);
    }

    private String deleteRuleOf(JdbcTemplate jdbcTemplate, String constraintName) {
        return jdbcTemplate.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints WHERE LOWER(constraint_name) = LOWER(?)",
                String.class, constraintName);
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(index_name) = LOWER(?)",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, birthday_date, password, active) VALUES (?, ?, '1990-01-01', NULL, TRUE)",
                name, "3499" + Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000)
        );
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE name = ?", Long.class, name);
    }

    private long insertUserAccount(JdbcTemplate jdbcTemplate, long personId, String username) {
        jdbcTemplate.update("""
                INSERT INTO tb_user_account (person_id, username, password_hash, enabled, token_version, created_at, updated_at)
                VALUES (?, ?, 'hash', TRUE, 0, CURRENT_TIMESTAMP(0), CURRENT_TIMESTAMP(0))
                """, personId, username);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_user_account WHERE person_id = ?", Long.class, personId);
    }

    private long insertNotification(JdbcTemplate jdbcTemplate, String origin, String audience, Long senderAccountId, String senderName) {
        jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot, created_at)
                VALUES (?, ?, 'GENERAL', 'Titulo', 'Mensagem', ?, ?, CURRENT_TIMESTAMP(0))
                """, origin, audience, senderAccountId, senderName);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM tb_notification", Long.class);
    }

    private void insertRecipient(JdbcTemplate jdbcTemplate, long notificationId, long accountId, String nameSnapshot) {
        jdbcTemplate.update("""
                INSERT INTO tb_notification_recipient (notification_id, user_account_id, recipient_name_snapshot)
                VALUES (?, ?, ?)
                """, notificationId, accountId, nameSnapshot);
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
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

    private MigrateResult migrateAll(DataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
