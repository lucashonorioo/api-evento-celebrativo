package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class EventAssignmentParallelReadMigratedDatabaseIntegrationTest {

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentReadService eventAssignmentReadService;

    @Autowired
    private EventAssignmentConsistencyService eventAssignmentConsistencyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAuditMigratedFixturesAsConsistentWithoutWritingData() {
        long eventPeopleBefore = count("tb_event_person");
        long assignmentsBefore = count("tb_event_assignment");
        List<Long> eventIds = eventIds();
        List<CelebrationEvent> legacyEvents = legacyEventsWithPeople(eventIds);

        Map<Long, List<EventAssignmentSnapshot>> parallelAssignments =
                eventAssignmentReadService.findAllByEventIds(eventIds);
        Map<Long, EventAssignmentConsistencyReport> reports =
                eventAssignmentConsistencyService.compareEvents(legacyEvents, parallelAssignments);

        assertFalse(reports.isEmpty());
        assertTrue(reports.values().stream().allMatch(EventAssignmentConsistencyReport::consistent));
        assertEquals(0L, legacyRowsWithoutAssignments());
        assertEquals(0L, assignmentsWithoutLegacyRows());
        assertEquals(0L, assignmentTypeMismatches());
        assertEquals(eventPeopleBefore, count("tb_event_person"));
        assertEquals(assignmentsBefore, count("tb_event_assignment"));
    }

    @Test
    void shouldDetectControlledDatabaseInconsistenciesWithoutFixingData() {
        Long eventId = 1L;
        Long missingPersonId = personIdInEventByType(eventId, "reader");
        Long mismatchedPersonId = personIdInEventByType(eventId, "commentator");
        Long mismatchedAssignmentId = assignmentId(eventId, mismatchedPersonId);
        Long extraPersonId = personIdOutsideEvent(eventId);

        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ? AND person_id = ?", eventId, missingPersonId);
        jdbcTemplate.update(
                "UPDATE tb_event_assignment SET assignment_type = 'READER' WHERE id = ?",
                mismatchedAssignmentId
        );
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment (event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, 'READER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                extraPersonId
        );

        long assignmentsBeforeAudit = count("tb_event_assignment");
        CelebrationEvent legacyEvent = celebrationEventRepository.findByIdWithPeople(eventId).orElseThrow();
        List<EventAssignmentSnapshot> parallelAssignments = eventAssignmentReadService.findAllByEventId(eventId);

        EventAssignmentConsistencyReport report =
                eventAssignmentConsistencyService.compareEvent(legacyEvent, parallelAssignments);

        assertIssue(report, EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT);
        assertIssue(report, EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT);
        assertIssue(report, EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH);
        assertEquals(assignmentsBeforeAudit, count("tb_event_assignment"));
        assertEquals(0, countRows(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                eventId,
                missingPersonId
        ));
        assertEquals("READER", jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE id = ?",
                String.class,
                mismatchedAssignmentId
        ));
        assertEquals(1, countRows(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                eventId,
                extraPersonId
        ));
    }

    private void assertIssue(EventAssignmentConsistencyReport report, EventAssignmentConsistencyIssueType issueType) {
        assertFalse(report.consistent());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.issueType() == issueType));
    }

    private List<Long> eventIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM tb_celebration_event ORDER BY id",
                Long.class
        );
    }

    private List<CelebrationEvent> legacyEventsWithPeople(List<Long> eventIds) {
        return eventIds.stream()
                .map(eventId -> celebrationEventRepository.findByIdWithPeople(eventId).orElseThrow())
                .toList();
    }

    private Long personIdInEventByType(Long eventId, String personType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT p.id
                FROM tb_event_person ep
                INNER JOIN tb_person p ON p.id = ep.person_id
                WHERE ep.event_id = ?
                AND p.person_type = ?
                ORDER BY p.id
                LIMIT 1
                """,
                Long.class,
                eventId,
                personType
        );
    }

    private Long personIdOutsideEvent(Long eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT p.id
                FROM tb_person p
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM tb_event_person ep
                    WHERE ep.event_id = ?
                    AND ep.person_id = p.id
                )
                ORDER BY p.id
                LIMIT 1
                """,
                Long.class,
                eventId
        );
    }

    private Long assignmentId(Long eventId, Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                Long.class,
                eventId,
                personId
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private int countRows(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private long legacyRowsWithoutAssignments() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_person ep
                LEFT JOIN tb_event_assignment ea ON ea.event_id = ep.event_id AND ea.person_id = ep.person_id
                WHERE ea.id IS NULL
                """,
                Long.class
        );
    }

    private long assignmentsWithoutLegacyRows() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment ea
                LEFT JOIN tb_event_person ep ON ep.event_id = ea.event_id AND ep.person_id = ea.person_id
                WHERE ep.event_id IS NULL
                """,
                Long.class
        );
    }

    private long assignmentTypeMismatches() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_person ep
                INNER JOIN tb_person p ON p.id = ep.person_id
                INNER JOIN tb_event_assignment ea ON ea.event_id = ep.event_id AND ea.person_id = ep.person_id
                WHERE ea.assignment_type <> CASE p.person_type
                    WHEN 'priest' THEN 'PRIEST'
                    WHEN 'reader' THEN 'READER'
                    WHEN 'commentator' THEN 'COMMENTATOR'
                    WHEN 'minister_of_the_word' THEN 'MINISTER_OF_THE_WORD'
                    WHEN 'eucharistic_minister' THEN 'EUCHARISTIC_MINISTER'
                END
                """,
                Long.class
        );
    }
}
