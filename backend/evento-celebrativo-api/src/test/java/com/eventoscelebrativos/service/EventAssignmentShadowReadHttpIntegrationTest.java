package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import com.eventoscelebrativos.model.EventAssignmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=DEBUG"
})
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class EventAssignmentShadowReadHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventAssignmentShadowReadProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetFlags() {
        properties.setEventDetailEnabled(false);
        properties.setEventScaleDetailEnabled(false);
        properties.setMonthlyScheduleEnabled(false);
        properties.setEucharistScaleEnabled(false);
    }

    @Test
    void shouldPreserveEventDetailHttpResponseWithShadowDisabledAndEnabled() throws Exception {
        properties.setEventDetailEnabled(false);
        String disabled = getPublicJson("/eventos/1");
        long assignmentsBefore = countAssignments();

        properties.setEventDetailEnabled(true);
        String enabled = getPublicJson("/eventos/1");

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, countAssignments());
    }

    @Test
    void shouldPreserveEventScaleDetailHttpResponseWithShadowDisabledAndEnabled() throws Exception {
        properties.setEventScaleDetailEnabled(false);
        String disabled = getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));
        long assignmentsBefore = countAssignments();

        properties.setEventScaleDetailEnabled(true);
        String enabled = getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, countAssignments());
    }

    @Test
    void shouldPreserveMonthlyScheduleHttpResponseWithShadowDisabledAndEnabled() throws Exception {
        String url = "/eventos/escalas?startDate=2025-07-01&endDate=2025-07-31&type=READER&page=0&size=10";
        properties.setMonthlyScheduleEnabled(false);
        String disabled = getAuthorizedJson(url, user("admin").roles("ADMIN"));
        long assignmentsBefore = countAssignments();

        properties.setMonthlyScheduleEnabled(true);
        String enabled = getAuthorizedJson(url, user("admin").roles("ADMIN"));

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, countAssignments());
    }

    @Test
    void shouldPreserveEucharistScaleHttpResponseWithShadowDisabledAndEnabled() throws Exception {
        String url = "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=0&size=10";
        properties.setEucharistScaleEnabled(false);
        String disabled = getPublicJson(url);
        long assignmentsBefore = countAssignments();

        properties.setEucharistScaleEnabled(true);
        String enabled = getPublicJson(url);

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, countAssignments());
    }

    @Test
    void shouldDetectInconsistentAssignmentsWithoutFixingDatabase(CapturedOutput output) throws Exception {
        Long eventId = 1L;
        Long missingPersonId = personIdInEventByType(eventId, "reader");
        Long mismatchedPersonId = personIdInEventByType(eventId, "commentator");
        Long extraPersonId = personIdOutsideEvent(eventId);

        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ? AND person_id = ?", eventId, missingPersonId);
        jdbcTemplate.update(
                "UPDATE tb_event_assignment SET assignment_type = ? WHERE event_id = ? AND person_id = ?",
                EventAssignmentType.READER.name(),
                eventId,
                mismatchedPersonId
        );
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                extraPersonId,
                EventAssignmentType.READER.name()
        );
        long assignmentsBefore = countAssignments();

        properties.setEventScaleDetailEnabled(true);
        getAuthorizedJson("/eventos/1/escala", user("operator").roles("OPERATOR"));

        assertTrue(output.getOut().contains("MISSING_PARALLEL_ASSIGNMENT"));
        assertTrue(output.getOut().contains("ASSIGNMENT_TYPE_MISMATCH"));
        assertTrue(output.getOut().contains("EXTRA_PARALLEL_ASSIGNMENT"));
        assertEquals(assignmentsBefore, countAssignments());
        assertEquals(0, countAssignment(eventId, missingPersonId));
        assertEquals(EventAssignmentType.READER.name(), assignmentType(eventId, mismatchedPersonId));
        assertEquals(1, countAssignment(eventId, extraPersonId));
    }

    private String getPublicJson(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String getAuthorizedJson(
            String url,
            SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor user
    ) throws Exception {
        return mockMvc.perform(get(url).with(user))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long countAssignments() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_event_assignment", Long.class);
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
}
