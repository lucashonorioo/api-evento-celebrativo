package com.eventoscelebrativos.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "app.event-assignment.read-source.event-scale-detail=PARALLEL",
        "app.event-assignment.shadow-read.event-scale-detail-enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class EventScaleDetailReadCutoverParallelFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EventAssignmentReadService eventAssignmentReadService;

    @Test
    void shouldPropagateOfficialParallelFailureWithoutUsingLegacyFallback() {
        long assignmentsBefore = countAssignments();
        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("controlled parallel failure"));

        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/eventos/1/escala")
                        .with(user("operator").roles("OPERATOR"))).andReturn()
        );

        verify(eventAssignmentReadService).findAllByEventId(1L);
        assertEquals(assignmentsBefore, countAssignments());
    }

    private long countAssignments() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_event_assignment", Long.class);
        return count == null ? 0 : count;
    }
}
