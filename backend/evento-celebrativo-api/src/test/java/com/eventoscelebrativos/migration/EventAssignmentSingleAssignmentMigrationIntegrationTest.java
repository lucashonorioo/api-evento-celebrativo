package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova em H2 a migracao V11 -> V12, que restaura a unicidade de tb_event_assignment para
 * (event_id, person_id), revertendo a V6. Espelha o padrao de
 * EventAssignmentUniqueConstraintMigrationIntegrationTest (que prova a V5 -> V6 original).
 */
class EventAssignmentSingleAssignmentMigrationIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

    @Test
    void shouldRestoreSingleAssignmentConstraintAfterMigratingExistingDatabaseWithoutDuplicates() {
        DataSource dataSource = createDataSource("event_assignment_single_assignment_upgrade");
        migrateUntil(dataSource, "11");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Single Assignment Upgrade Reader");
        long eventId = insertEvent(jdbcTemplate, "Single Assignment Upgrade Event");
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");

        MigrateResult result = migrateAll(dataSource);
        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "12");

        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment", eventId, readerId));
        assertThrows(DataIntegrityViolationException.class, () ->
                insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR"));
    }

    @Test
    void shouldFailMigrationWithoutDeletingDataWhenDuplicatePersonAssignmentsExist() {
        DataSource dataSource = createDataSource("event_assignment_single_assignment_conflict");
        migrateUntil(dataSource, "11");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long readerId = insertPerson(jdbcTemplate, "Single Assignment Conflict Reader");
        long eventId = insertEvent(jdbcTemplate, "Single Assignment Conflict Event");
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");
        insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR");
        assertEquals(2, countRows(jdbcTemplate, "tb_event_assignment", eventId, readerId));

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));
        String diagnostic = rootCauseMessage(exception);
        String expectedEntry = "event_id=" + eventId
                + ", person_id=" + readerId
                + ", assignment_count=2"
                + ", assignment_types=[COMMENTATOR, READER]";
        assertTrue(diagnostic.contains(expectedEntry),
                "Esperava diagnostico completo (event_id, person_id, assignment_count e assignment_types "
                        + "ordenados) \"" + expectedEntry + "\" em: " + diagnostic);

        // Nenhum dado deve ter sido apagado ou alterado automaticamente pela migration com falha.
        assertEquals(2, countRows(jdbcTemplate, "tb_event_assignment", eventId, readerId));
        Integer v12Applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '12' AND success = TRUE", Integer.class);
        assertEquals(0, v12Applied);
    }

    @Test
    void shouldApplyConstraintOnFreshDatabase() {
        DataSource dataSource = createDataSource("event_assignment_single_assignment_fresh");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        migrateAll(dataSource);
        assertSuccessfulMigration(jdbcTemplate, "12");

        long readerId = insertPerson(jdbcTemplate, "Single Assignment Fresh Reader");
        long eventId = insertEvent(jdbcTemplate, "Single Assignment Fresh Event");
        insertAssignment(jdbcTemplate, eventId, readerId, "READER");

        assertThrows(DataIntegrityViolationException.class, () ->
                insertAssignment(jdbcTemplate, eventId, readerId, "COMMENTATOR"));
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
                .target("12")
                .load()
                .migrate();
    }

    private long insertPerson(JdbcTemplate jdbcTemplate, String name) {
        String phoneNumber = "3496" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password)
                VALUES (?, ?, '1990-01-01', ?)
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
        LocalDateTime startAt = LocalDateTime.of(2026, 8, 1, 19, 0);
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                Timestamp.valueOf(startAt),
                Timestamp.valueOf(startAt.plusHours(1))
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

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
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
