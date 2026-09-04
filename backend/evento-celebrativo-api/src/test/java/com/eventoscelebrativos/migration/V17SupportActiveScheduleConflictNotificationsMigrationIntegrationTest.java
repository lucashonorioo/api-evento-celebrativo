package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura dedicada da migration V17: coluna active_source_key em tb_notification, sua UNIQUE
 * (permitindo multiplos NULL) e os dois novos indices (category+resolved_at,
 * reference_type+reference_id). V1-V16 permanecem intactas (verificado indiretamente pelo upgrade
 * a partir de V16 preservando dados existentes).
 */
class V17SupportActiveScheduleConflictNotificationsMigrationIntegrationTest {

    @Test
    void shouldUpgradeFromV16ToV17WithoutAffectingExistingData() {
        DataSource dataSource = createDataSource("v17_upgrade_from_v16");
        migrateUntil(dataSource, "16");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long notificationId = insertNotificationWithoutActiveSourceKeyColumn(jdbcTemplate);

        MigrateResult result = migrateAll(dataSource, "17");

        assertEquals(1, result.migrationsExecuted);
        assertEquals(
                "Titulo",
                jdbcTemplate.queryForObject("SELECT title FROM tb_notification WHERE id = ?", String.class, notificationId)
        );
        assertEquals(1, countRows(jdbcTemplate, "tb_notification"));
    }

    @Test
    void shouldCreateActiveScheduleConflictSchemaOnCleanDatabase() {
        DataSource dataSource = createDataSource("v17_clean_database");
        MigrateResult result = migrateAll(dataSource, "17");

        assertEquals(17, result.migrationsExecuted);
    }

    @Test
    void shouldAllowMultipleNullActiveSourceKeysButRejectDuplicateNonNullValues() {
        DataSource dataSource = createDataSource("v17_unique_active_source_key");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        insertNotification(jdbcTemplate, null);
        insertNotification(jdbcTemplate, null);
        insertNotification(jdbcTemplate, "SCHEDULE_UNAVAILABILITY_CONFLICT:1:2");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertNotification(jdbcTemplate, "SCHEDULE_UNAVAILABILITY_CONFLICT:1:2"));

        assertEquals(3, countRows(jdbcTemplate, "tb_notification"));
    }

    @Test
    void shouldExposeExpectedConstraintAndIndexes() {
        DataSource dataSource = createDataSource("v17_metadata");
        migrateAll(dataSource, "17");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertConstraintExists(jdbcTemplate, "tb_notification", "uk_tb_notification_active_source_key");
        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_category_resolved_at"));
        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_reference"));
        // indice pre-existente da V16 nao deve ter sido recriado/duplicado
        assertTrue(indexExists(jdbcTemplate, "tb_notification", "idx_tb_notification_source"));
    }

    private void assertConstraintExists(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)",
                Integer.class, tableName, constraintName);
        assertEquals(1, count == null ? 0 : count);
    }

    private boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(index_name) = LOWER(?)",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private long insertNotification(JdbcTemplate jdbcTemplate) {
        return insertNotification(jdbcTemplate, null);
    }

    private long insertNotificationWithoutActiveSourceKeyColumn(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'GENERAL', 'Titulo', 'Mensagem', NULL, 'Sistema', CURRENT_TIMESTAMP(0))
                """);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM tb_notification", Long.class);
    }

    /**
     * category=SCHEDULE_CONFLICT (nao GENERAL): reproduz o uso real de producao, onde
     * activeSourceKey so e preenchida por Notification.scheduleConflict(...) e prova, no nivel de
     * schema, que a nova categoria e persistida sem CHECK que a restrinja (V16 ja deixa category
     * livre de CHECK).
     */
    private long insertNotification(JdbcTemplate jdbcTemplate, String activeSourceKey) {
        jdbcTemplate.update("""
                INSERT INTO tb_notification
                    (origin, audience, category, title, message, sender_user_account_id, sender_name_snapshot,
                     active_source_key, created_at)
                VALUES ('SYSTEM', 'GLOBAL', 'SCHEDULE_CONFLICT', 'Titulo', 'Mensagem', NULL, 'Sistema', ?, CURRENT_TIMESTAMP(0))
                """, activeSourceKey);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM tb_notification", Long.class);
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
