package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPersonSchemaRemovalMigrationIntegrationTest {

    @Test
    void shouldRemoveLegacyEventPersonTableWhenEveryLinkHasAnOfficialAssignment() {
        DataSource dataSource = createDataSource("event_person_removal_valid");
        migrateUntil(dataSource, "6");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Removal Valid Reader", "reader");
        long eventId = insertEvent(jdbcTemplate, "Removal Valid Event");
        linkEventPerson(jdbcTemplate, eventId, readerId);
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "7");
        assertFalse(tableExists(jdbcTemplate, "tb_event_person"));
        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals("READER", jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                String.class,
                eventId,
                readerId
        ));
    }

    @Test
    void shouldFailAndKeepTableWhenLegacyLinkHasNoOfficialAssignment() {
        DataSource dataSource = createDataSource("event_person_removal_divergent");
        migrateUntil(dataSource, "6");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Removal Divergent Reader", "reader");
        long eventId = insertEvent(jdbcTemplate, "Removal Divergent Event");
        linkEventPerson(jdbcTemplate, eventId, readerId);

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "1 vinculo"));
        assertTrue(hasMessageContaining(exception, "event_id=" + eventId));
        assertTrue(hasMessageContaining(exception, "person_id=" + readerId));
        assertFailedMigrationRecorded(jdbcTemplate, "7");
        assertTrue(tableExists(jdbcTemplate, "tb_event_person"));
        assertEquals(1, countRows(jdbcTemplate, "tb_event_person"));
        assertEquals(0, countRows(jdbcTemplate, "tb_event_assignment"));
    }

    @Test
    void shouldRemoveLegacyEventPersonTableWhenLegacyLinksAlreadyCleared() {
        DataSource dataSource = createDataSource("event_person_removal_empty");
        migrateUntil(dataSource, "6");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long priestId = insertPerson(jdbcTemplate, "Removal Empty Priest", "priest");
        long eventId = insertEvent(jdbcTemplate, "Removal Empty Event");
        insertAssignment(jdbcTemplate, eventId, priestId, "PRIEST");

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "7");
        assertFalse(tableExists(jdbcTemplate, "tb_event_person"));
        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment"));
    }

    @Test
    void shouldReachV7FromNewDatabaseWithoutLegacyEventPersonTable() {
        DataSource dataSource = createDataSource("event_person_removal_new_database");

        MigrateResult result = migrateAll(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertEquals(7, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "7");
        assertFalse(tableExists(jdbcTemplate, "tb_event_person"));
        assertTrue(tableExists(jdbcTemplate, "tb_event_assignment"));
        assertTrue(tableExists(jdbcTemplate, "tb_celebration_event"));
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

    private MigrateResult migrateAll(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name, String personType) {
        String phoneNumber = "3495" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-01', 'encoded-password', ?)
                """,
                name,
                phoneNumber,
                personType
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private long insertEvent(JdbcTemplate jdbcTemplate, String name) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, '2026-08-01', '19:00:00', TRUE)
                """,
                eventName
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private void linkEventPerson(JdbcTemplate jdbcTemplate, long eventId, long personId) {
        jdbcTemplate.update(
                "INSERT INTO tb_event_person(event_id, person_id) VALUES (?, ?)",
                eventId,
                personId
        );
    }

    private void insertAssignment(JdbcTemplate jdbcTemplate, long eventId, long personId, String assignmentType) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                assignmentType
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
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private int countFailedMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = FALSE",
                Integer.class,
                version
        );
        return count == null ? 0 : count;
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = LOWER(?)",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
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
