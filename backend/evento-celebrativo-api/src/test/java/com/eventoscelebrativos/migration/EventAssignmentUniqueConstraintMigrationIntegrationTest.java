package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventAssignmentUniqueConstraintMigrationIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

    @Test
    void shouldReplaceUniqueConstraintAndAllowMultipleAssignmentTypesAfterMigratingExistingDatabase() {
        DataSource dataSource = createDataSource("event_assignment_unique_constraint_upgrade");
        migrateUntil(dataSource, "5");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Constraint Upgrade Reader");
        long eventId = insertEvent(jdbcTemplate, "Constraint Upgrade Event");
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");

        assertThrows(DataIntegrityViolationException.class, () ->
                insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR"));

        MigrateResult result = migrateAll(dataSource);
        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "6");

        insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR");
        assertEquals(2, countRows(jdbcTemplate, "tb_event_assignment", eventId, readerId));

        assertThrows(DataIntegrityViolationException.class, () ->
                insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR"));
    }

    @Test
    void shouldAllowMultipleAssignmentTypesOnFreshDatabase() {
        DataSource dataSource = createDataSource("event_assignment_unique_constraint_fresh");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateAll(dataSource);

        long priestId = insertPerson(jdbcTemplate, "Constraint Fresh Priest");
        long eventId = insertEvent(jdbcTemplate, "Constraint Fresh Event");

        insertAssignment(jdbcTemplate, eventId, priestId, "PRIEST");
        insertAssignment(jdbcTemplate, eventId, priestId, "READER");

        assertEquals(2, countRows(jdbcTemplate, "tb_event_assignment", eventId, priestId));

        assertThrows(DataIntegrityViolationException.class, () ->
                insertAssignment(jdbcTemplate, eventId, priestId, "READER"));
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
        // Historical regression: stops at V6, the migration under test here.
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name) {
        String phoneNumber = "3495" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-01', ?, 'reader')
                """,
                name,
                phoneNumber,
                PASSWORD_HASH
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

    private void insertAssignment(JdbcTemplate jdbcTemplate, long eventId, long personId, String assignmentType) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                eventId,
                personId,
                assignmentType,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
    }

    private int countRows(JdbcTemplate jdbcTemplate, String table, long eventId, long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_id = ? AND person_id = ?",
                Integer.class,
                eventId,
                personId
        );
        return count == null ? 0 : count;
    }

    private void assertSuccessfulMigration(JdbcTemplate jdbcTemplate, String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = TRUE",
                Integer.class,
                version
        );
        assertEquals(1, count);
    }
}
