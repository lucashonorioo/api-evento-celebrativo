package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import com.eventoscelebrativos.model.EventAssignmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.event-assignment.read-source.event-scale-detail=LEGACY",
        "app.event-assignment.shadow-read.event-scale-detail-enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EventScaleDetailReadCutoverLegacyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;

    @Autowired
    private EventAssignmentShadowReadProperties eventAssignmentShadowReadProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetProperties() {
        eventAssignmentReadSourceProperties.setEventScaleDetail(EventAssignmentReadSource.LEGACY);
        eventAssignmentShadowReadProperties.setEventScaleDetailEnabled(false);
    }

    @Test
    void shouldKeepLegacyScaleDetailAsOfficialSourceWhenParallelAssignmentsDiverge() throws Exception {
        String expected = getAuthorizedJson(1L);
        Long readerId = personIdInEventByType(1L, "reader");
        jdbcTemplate.update(
                "UPDATE tb_event_assignment SET assignment_type = ? WHERE event_id = ? AND person_id = ?",
                EventAssignmentType.EUCHARISTIC_MINISTER.name(),
                1L,
                readerId
        );
        List<Map<String, Object>> assignmentsBefore = assignmentRows();

        String actual = getAuthorizedJson(1L);

        assertEquals(expected, actual);
        assertEquals(EventAssignmentType.EUCHARISTIC_MINISTER.name(), assignmentType(1L, readerId));
        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldKeepLegacyScaleDetailResponseWhenShadowReadIsEnabled() throws Exception {
        eventAssignmentShadowReadProperties.setEventScaleDetailEnabled(false);
        String disabled = getAuthorizedJson(1L);
        List<Map<String, Object>> assignmentsBefore = assignmentRows();

        eventAssignmentShadowReadProperties.setEventScaleDetailEnabled(true);
        String enabled = getAuthorizedJson(1L);

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldPreserveLegacyNotFoundAndSecurityBehavior() throws Exception {
        mockMvc.perform(get("/eventos/999999/escala")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isUnauthorized());
    }

    private String getAuthorizedJson(Long eventId) throws Exception {
        return mockMvc.perform(get("/eventos/{id}/escala", eventId)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
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

    private String assignmentType(Long eventId, Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                String.class,
                eventId,
                personId
        );
    }

    private List<Map<String, Object>> assignmentRows() {
        return jdbcTemplate.queryForList(
                """
                SELECT id, event_id, person_id, assignment_type, created_at, updated_at
                FROM tb_event_assignment
                ORDER BY id
                """
        );
    }
}
