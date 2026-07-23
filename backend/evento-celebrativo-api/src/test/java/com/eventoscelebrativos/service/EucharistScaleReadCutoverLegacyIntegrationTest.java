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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.event-assignment.read-source.eucharist-scale=LEGACY",
        "app.event-assignment.shadow-read.eucharist-scale-enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EucharistScaleReadCutoverLegacyIntegrationTest {

    private static final String URL =
            "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=0&size=10";

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
        eventAssignmentReadSourceProperties.setEucharistScale(EventAssignmentReadSource.LEGACY);
        eventAssignmentShadowReadProperties.setEucharistScaleEnabled(false);
    }

    @Test
    void shouldKeepLegacyEucharistScaleAsOfficialSourceWhenParallelAssignmentsDiverge() throws Exception {
        String expected = getPublicJson(URL);
        Long eucharisticMinisterId = personIdInEventByType(1L, "eucharistic_minister");
        jdbcTemplate.update(
                "UPDATE tb_event_assignment SET assignment_type = ? WHERE event_id = ? AND person_id = ?",
                EventAssignmentType.READER.name(),
                1L,
                eucharisticMinisterId
        );
        List<Map<String, Object>> assignmentsBefore = assignmentRows();

        String actual = getPublicJson(URL);

        assertEquals(expected, actual);
        assertEquals(EventAssignmentType.READER.name(), assignmentType(1L, eucharisticMinisterId));
        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldKeepLegacyEucharistScaleResponseWhenShadowReadIsEnabled() throws Exception {
        eventAssignmentShadowReadProperties.setEucharistScaleEnabled(false);
        String disabled = getPublicJson(URL);
        List<Map<String, Object>> assignmentsBefore = assignmentRows();

        eventAssignmentShadowReadProperties.setEucharistScaleEnabled(true);
        String enabled = getPublicJson(URL);

        assertEquals(disabled, enabled);
        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldPreservePublicAccessInLegacySource() throws Exception {
        getPublicJson(URL);
    }

    private String getPublicJson(String url) throws Exception {
        return mockMvc.perform(get(url))
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
