package com.eventoscelebrativos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventAssignmentBackfillMigrationIntegrationTest {

    private static final String PASSWORD_HASH = "$2a$10$BZEayVp6X1Ry93e44/Rnze0hpK5J3ThbAdUm2OzH.GSWjA4zmtGHW";

    @Test
    void shouldBackfillEventAssignmentsFromLegacyEventPeople() {
        DataSource dataSource = createDataSource("event_assignment_backfill");
        migrateUntil(dataSource, "4");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long priestId = insertPerson(jdbcTemplate, "Backfill Priest", "priest");
        long readerId = insertPerson(jdbcTemplate, "Backfill Reader", "reader");
        long commentatorId = insertPerson(jdbcTemplate, "Backfill Commentator", "commentator");
        long ministerOfTheWordId = insertPerson(jdbcTemplate, "Backfill Word Minister", "minister_of_the_word");
        long eucharisticMinisterId = insertPerson(jdbcTemplate, "Backfill Eucharistic Minister", "eucharistic_minister");
        long firstEventId = insertEvent(jdbcTemplate, "Backfill First Event");
        long secondEventId = insertEvent(jdbcTemplate, "Backfill Second Event");

        linkEventPerson(jdbcTemplate, firstEventId, priestId);
        linkEventPerson(jdbcTemplate, firstEventId, readerId);
        linkEventPerson(jdbcTemplate, firstEventId, commentatorId);
        linkEventPerson(jdbcTemplate, firstEventId, ministerOfTheWordId);
        linkEventPerson(jdbcTemplate, firstEventId, eucharisticMinisterId);
        linkEventPerson(jdbcTemplate, secondEventId, readerId);
        linkEventPerson(jdbcTemplate, secondEventId, priestId);

        LocalDateTime correctCreatedAt = LocalDateTime.of(2026, 1, 10, 8, 0);
        LocalDateTime correctUpdatedAt = LocalDateTime.of(2026, 1, 10, 8, 30);
        long correctReaderAssignmentId = insertAssignment(
                jdbcTemplate,
                firstEventId,
                readerId,
                "READER",
                correctCreatedAt,
                correctUpdatedAt
        );

        LocalDateTime incorrectCreatedAt = LocalDateTime.of(2026, 1, 11, 8, 0);
        LocalDateTime incorrectUpdatedAt = LocalDateTime.of(2026, 1, 11, 8, 30);
        long incorrectCommentatorAssignmentId = insertAssignment(
                jdbcTemplate,
                firstEventId,
                commentatorId,
                "READER",
                incorrectCreatedAt,
                incorrectUpdatedAt
        );

        LocalDateTime secondEventPriestCreatedAt = LocalDateTime.of(2026, 1, 12, 8, 0);
        LocalDateTime secondEventPriestUpdatedAt = LocalDateTime.of(2026, 1, 12, 8, 30);
        long secondEventPriestAssignmentId = insertAssignment(
                jdbcTemplate,
                secondEventId,
                priestId,
                "PRIEST",
                secondEventPriestCreatedAt,
                secondEventPriestUpdatedAt
        );

        MigrateResult result = migrateAll(dataSource);

        assertEquals(1, result.migrationsExecuted);
        assertSuccessfulMigration(jdbcTemplate, "5");
        assertEquals(countRows(jdbcTemplate, "tb_event_person"), countRows(jdbcTemplate, "tb_event_assignment"));
        assertAssignmentAuditIsClean(jdbcTemplate);

        assertAssignmentType(jdbcTemplate, firstEventId, priestId, "PRIEST");
        assertAssignmentType(jdbcTemplate, firstEventId, readerId, "READER");
        assertAssignmentType(jdbcTemplate, firstEventId, commentatorId, "COMMENTATOR");
        assertAssignmentType(jdbcTemplate, firstEventId, ministerOfTheWordId, "MINISTER_OF_THE_WORD");
        assertAssignmentType(jdbcTemplate, firstEventId, eucharisticMinisterId, "EUCHARISTIC_MINISTER");
        assertAssignmentType(jdbcTemplate, secondEventId, readerId, "READER");
        assertAssignmentType(jdbcTemplate, secondEventId, priestId, "PRIEST");

        assertEquals(correctReaderAssignmentId, queryAssignmentId(jdbcTemplate, firstEventId, readerId));
        assertEquals(Timestamp.valueOf(correctCreatedAt), queryTimestamp(jdbcTemplate, correctReaderAssignmentId, "created_at"));
        assertEquals(Timestamp.valueOf(correctUpdatedAt), queryTimestamp(jdbcTemplate, correctReaderAssignmentId, "updated_at"));

        assertEquals(incorrectCommentatorAssignmentId, queryAssignmentId(jdbcTemplate, firstEventId, commentatorId));
        assertEquals(Timestamp.valueOf(incorrectCreatedAt), queryTimestamp(jdbcTemplate, incorrectCommentatorAssignmentId, "created_at"));
        assertNotEquals(
                Timestamp.valueOf(incorrectUpdatedAt),
                queryTimestamp(jdbcTemplate, incorrectCommentatorAssignmentId, "updated_at")
        );

        assertEquals(secondEventPriestAssignmentId, queryAssignmentId(jdbcTemplate, secondEventId, priestId));
        assertEquals(
                Timestamp.valueOf(secondEventPriestCreatedAt),
                queryTimestamp(jdbcTemplate, secondEventPriestAssignmentId, "created_at")
        );
        assertEquals(
                Timestamp.valueOf(secondEventPriestUpdatedAt),
                queryTimestamp(jdbcTemplate, secondEventPriestAssignmentId, "updated_at")
        );
        assertNotEquals(
                correctReaderAssignmentId,
                queryAssignmentId(jdbcTemplate, secondEventId, readerId)
        );
    }

    @Test
    void shouldFailBeforeWritingWhenPersonTypeIsInvalid() {
        DataSource dataSource = createDataSource("event_assignment_invalid_type");
        migrateUntil(dataSource, "4");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long validReaderId = insertPerson(jdbcTemplate, "Backfill Valid Reader", "reader");
        long invalidPersonId = insertPerson(jdbcTemplate, "Backfill Invalid Person", "invalid_type");
        long eventId = insertEvent(jdbcTemplate, "Backfill Invalid Type Event");
        linkEventPerson(jdbcTemplate, eventId, validReaderId);
        linkEventPerson(jdbcTemplate, eventId, invalidPersonId);

        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 2, 1, 8, 0);
        LocalDateTime existingUpdatedAt = LocalDateTime.of(2026, 2, 1, 8, 30);
        long existingAssignmentId = insertAssignment(
                jdbcTemplate,
                eventId,
                validReaderId,
                "PRIEST",
                existingCreatedAt,
                existingUpdatedAt
        );

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "invalid_type"));
        assertTrue(hasMessageContaining(exception, "event_id=" + eventId));
        assertTrue(hasMessageContaining(exception, "person_id=" + invalidPersonId));
        assertFailedMigrationRecorded(jdbcTemplate, "5");
        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals("PRIEST", queryAssignmentType(jdbcTemplate, existingAssignmentId));
        assertEquals(Timestamp.valueOf(existingCreatedAt), queryTimestamp(jdbcTemplate, existingAssignmentId, "created_at"));
        assertEquals(Timestamp.valueOf(existingUpdatedAt), queryTimestamp(jdbcTemplate, existingAssignmentId, "updated_at"));
    }

    @Test
    void shouldFailBeforeWritingWhenAssignmentHasNoLegacyEventPerson() {
        DataSource dataSource = createDataSource("event_assignment_extra");
        migrateUntil(dataSource, "4");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        long linkedReaderId = insertPerson(jdbcTemplate, "Backfill Linked Reader", "reader");
        long extraPriestId = insertPerson(jdbcTemplate, "Backfill Extra Priest", "priest");
        long linkedEventId = insertEvent(jdbcTemplate, "Backfill Linked Event");
        long extraEventId = insertEvent(jdbcTemplate, "Backfill Extra Event");
        linkEventPerson(jdbcTemplate, linkedEventId, linkedReaderId);

        long extraAssignmentId = insertAssignment(
                jdbcTemplate,
                extraEventId,
                extraPriestId,
                "PRIEST",
                LocalDateTime.of(2026, 3, 1, 8, 0),
                LocalDateTime.of(2026, 3, 1, 8, 30)
        );

        FlywayException exception = assertThrows(FlywayException.class, () -> migrateAll(dataSource));

        assertTrue(hasMessageContaining(exception, "Assignments sem vinculo correspondente"));
        assertTrue(hasMessageContaining(exception, "event_id=" + extraEventId));
        assertTrue(hasMessageContaining(exception, "person_id=" + extraPriestId));
        assertFailedMigrationRecorded(jdbcTemplate, "5");
        assertEquals(1, countRows(jdbcTemplate, "tb_event_assignment"));
        assertEquals(extraAssignmentId, queryAssignmentId(jdbcTemplate, extraEventId, extraPriestId));
        assertEquals(0, countAssignments(jdbcTemplate, linkedEventId, linkedReaderId));
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
        String phoneNumber = "3499" + Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-01', ?, ?)
                """,
                name,
                phoneNumber,
                PASSWORD_HASH,
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

    private long insertAssignment(
            JdbcTemplate jdbcTemplate,
            long eventId,
            long personId,
            String assignmentType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                eventId,
                personId,
                assignmentType,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(updatedAt)
        );
        return queryAssignmentId(jdbcTemplate, eventId, personId);
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

    private int countAssignments(JdbcTemplate jdbcTemplate, long eventId, long personId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                Integer.class,
                eventId,
                personId
        );
        return count == null ? 0 : count;
    }

    private long queryAssignmentId(JdbcTemplate jdbcTemplate, long eventId, long personId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                Long.class,
                eventId,
                personId
        );
    }

    private String queryAssignmentType(JdbcTemplate jdbcTemplate, long assignmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE id = ?",
                String.class,
                assignmentId
        );
    }

    private Timestamp queryTimestamp(JdbcTemplate jdbcTemplate, long assignmentId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM tb_event_assignment WHERE id = ?",
                Timestamp.class,
                assignmentId
        );
    }

    private void assertAssignmentType(JdbcTemplate jdbcTemplate, long eventId, long personId, String assignmentType) {
        String actual = jdbcTemplate.queryForObject(
                """
                SELECT assignment_type
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                String.class,
                eventId,
                personId
        );
        assertEquals(assignmentType, actual);
    }

    private void assertAssignmentAuditIsClean(JdbcTemplate jdbcTemplate) {
        assertEquals(0, countEventPeopleWithoutAssignment(jdbcTemplate));
        assertEquals(0, countAssignmentsWithoutLegacyEventPerson(jdbcTemplate));
        assertEquals(0, countDuplicatedEventAssignments(jdbcTemplate));
        assertEquals(0, countAssignmentsWithUnexpectedType(jdbcTemplate));
    }

    private int countEventPeopleWithoutAssignment(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_person ep
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_assignment ea
                    WHERE ea.event_id = ep.event_id
                      AND ea.person_id = ep.person_id
                )
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAssignmentsWithoutLegacyEventPerson(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment ea
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_person ep
                    WHERE ep.event_id = ea.event_id
                      AND ep.person_id = ea.person_id
                )
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countDuplicatedEventAssignments(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT event_id, person_id
                    FROM tb_event_assignment
                    GROUP BY event_id, person_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countAssignmentsWithUnexpectedType(JdbcTemplate jdbcTemplate) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment ea
                INNER JOIN tb_person p ON p.id = ea.person_id
                WHERE ea.assignment_type <> CASE p.person_type
                    WHEN 'priest' THEN 'PRIEST'
                    WHEN 'reader' THEN 'READER'
                    WHEN 'commentator' THEN 'COMMENTATOR'
                    WHEN 'minister_of_the_word' THEN 'MINISTER_OF_THE_WORD'
                    WHEN 'eucharistic_minister' THEN 'EUCHARISTIC_MINISTER'
                END
                """,
                Integer.class
        );
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
