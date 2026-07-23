package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EventAssignmentType;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EventAssignmentOperationalAuditHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldReturnConsistentAuditWithFiltersPaginationNoPersonalDataNoWritesAndExpectedQueries() throws Exception {
        DatabaseSnapshot before = snapshotDatabase();
        Statistics statistics = statistics();
        statistics.clear();

        mockMvc.perform(adminGet(
                        "/admin/event-assignments/consistency?startDate=2025-07-01&endDate=2025-07-31&page=0&size=2&includeDetails=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(2))
                .andExpect(jsonPath("$.summary.consistentEvents").value(2))
                .andExpect(jsonPath("$.summary.inconsistentEvents").value(0))
                .andExpect(jsonPath("$.summary.legacyParticipants").value(14))
                .andExpect(jsonPath("$.summary.parallelAssignments").value(14))
                .andExpect(jsonPath("$.summary.totalIssues").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andExpect(jsonPath("$.events").isEmpty())
                .andExpect(jsonPath("$..personName").doesNotExist())
                .andExpect(jsonPath("$..phoneNumber").doesNotExist())
                .andExpect(jsonPath("$..birthdayDate").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..name").doesNotExist());

        assertEquals(4L, statistics.getPrepareStatementCount());
        assertEquals(before, snapshotDatabase());
    }

    @Test
    void shouldOmitDetailsWhenIncludeDetailsIsFalseButKeepSummary() throws Exception {
        mockMvc.perform(adminGet(
                        "/admin/event-assignments/consistency?startDate=2025-07-01&endDate=2025-07-31&page=0&size=2&includeDetails=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(2))
                .andExpect(jsonPath("$.summary.totalIssues").value(0))
                .andExpect(jsonPath("$.events").doesNotExist());
    }

    @Test
    void shouldAuditSpecificEventOnly() throws Exception {
        mockMvc.perform(adminGet("/admin/event-assignments/consistency?eventId=1&page=9&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(1))
                .andExpect(jsonPath("$.summary.legacyParticipants").value(7))
                .andExpect(jsonPath("$.summary.parallelAssignments").value(7))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnEmptyPageForPeriodWithoutEvents() throws Exception {
        mockMvc.perform(adminGet(
                        "/admin/event-assignments/consistency?startDate=2030-01-01&endDate=2030-01-31&page=3&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(0))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.events").isEmpty());
    }

    @Test
    void shouldReturnBusinessErrorForInvalidInterval() throws Exception {
        mockMvc.perform(adminGet(
                        "/admin/event-assignments/consistency?startDate=2025-08-01&endDate=2025-07-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(adminGet("/admin/event-assignments/consistency?eventId=99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldDetectMissingParallelAssignmentWithoutFixingData() throws Exception {
        Long eventId = 1L;
        Long personId = personIdInEventByType(eventId, "reader");
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ? AND person_id = ?", eventId, personId);
        DatabaseSnapshot before = snapshotDatabase();

        mockMvc.perform(adminGet("/admin/event-assignments/consistency?eventId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.missingParallelAssignments").value(1))
                .andExpect(jsonPath("$.events[0].issues[*].issueType", hasItem("MISSING_PARALLEL_ASSIGNMENT")))
                .andExpect(jsonPath("$.events[0].issues[0].personId").value(personId));

        assertEquals(before, snapshotDatabase());
        assertEquals(0, countAssignment(eventId, personId));
    }

    @Test
    void shouldDetectExtraParallelAssignmentWithoutFixingData() throws Exception {
        Long eventId = 1L;
        Long personId = personIdOutsideEvent(eventId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.READER.name()
        );
        DatabaseSnapshot before = snapshotDatabase();

        mockMvc.perform(adminGet("/admin/event-assignments/consistency?eventId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.extraParallelAssignments").value(1))
                .andExpect(jsonPath("$.events[0].issues[*].issueType", hasItem("EXTRA_PARALLEL_ASSIGNMENT")))
                .andExpect(jsonPath("$.events[0].issues[0].personId").value(personId));

        assertEquals(before, snapshotDatabase());
        assertEquals(1, countAssignment(eventId, personId));
    }

    @Test
    void shouldDetectAssignmentTypeMismatchWithoutFixingData() throws Exception {
        Long eventId = 1L;
        Long personId = personIdInEventByType(eventId, "commentator");
        jdbcTemplate.update(
                "UPDATE tb_event_assignment SET assignment_type = ? WHERE event_id = ? AND person_id = ?",
                EventAssignmentType.READER.name(),
                eventId,
                personId
        );
        DatabaseSnapshot before = snapshotDatabase();

        mockMvc.perform(adminGet("/admin/event-assignments/consistency?eventId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.assignmentTypeMismatches").value(1))
                .andExpect(jsonPath("$.events[0].issues[*].issueType", hasItem("ASSIGNMENT_TYPE_MISMATCH")))
                .andExpect(jsonPath("$.events[0].issues[0].legacyType").value("COMMENTATOR"))
                .andExpect(jsonPath("$.events[0].issues[0].parallelType").value("READER"));

        assertEquals(before, snapshotDatabase());
        assertEquals(EventAssignmentType.READER.name(), assignmentType(eventId, personId));
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(String url) {
        return get(url).with(admin());
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

    private int countAssignment(Long eventId, Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                Integer.class,
                eventId,
                personId
        );
        return count == null ? 0 : count;
    }

    private String assignmentType(Long eventId, Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                String.class,
                eventId,
                personId
        );
    }

    private DatabaseSnapshot snapshotDatabase() {
        return new DatabaseSnapshot(
                jdbcTemplate.queryForList("""
                        SELECT event_id, person_id
                        FROM tb_event_person
                        ORDER BY event_id, person_id
                        """),
                jdbcTemplate.queryForList("""
                        SELECT id, event_id, person_id, assignment_type, created_at, updated_at
                        FROM tb_event_assignment
                        ORDER BY id
                        """),
                jdbcTemplate.queryForList("""
                        SELECT id, person_type, created_at, updated_at
                        FROM tb_person
                        ORDER BY id
                        """),
                jdbcTemplate.queryForList("""
                        SELECT id, event_date, event_time, mass_or_celebration
                        FROM tb_celebration_event
                        ORDER BY id
                        """)
        );
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private record DatabaseSnapshot(
            List<Map<String, Object>> eventPeople,
            List<Map<String, Object>> eventAssignments,
            List<Map<String, Object>> people,
            List<Map<String, Object>> events
    ) {
    }
}
