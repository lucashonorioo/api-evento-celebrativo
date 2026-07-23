package com.eventoscelebrativos.service;

import com.eventoscelebrativos.projection.EventScheduleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "app.event-assignment.read-source.monthly-schedule=PARALLEL",
        "app.event-assignment.shadow-read.monthly-schedule-enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class MonthlyScheduleReadCutoverParallelFailureIntegrationTest {

    private static final String URL =
            "/eventos/escalas?startDate=2025-07-01&endDate=2025-07-31&type=READER&page=0&size=10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CelebrationEventRepository celebrationEventRepository;

    @Test
    void shouldPropagateParallelPageFailureWithoutLegacyFallbackOrWrites() {
        long assignmentsBefore = countAssignments();
        when(celebrationEventRepository.findEventScheduleEventsByAssignments(
                any(Pageable.class),
                any(),
                any(),
                anyString(),
                anyBoolean()
        )).thenThrow(new IllegalStateException("controlled parallel page failure"));

        assertThrows(Exception.class, () -> mockMvc.perform(get(URL)
                .with(user("operator").roles("OPERATOR"))).andReturn());

        verify(celebrationEventRepository).findEventScheduleEventsByAssignments(
                any(Pageable.class),
                any(),
                any(),
                anyString(),
                anyBoolean()
        );
        verify(celebrationEventRepository, never()).findEventScheduleEvents(any(), any(), any(), anyString(), anyBoolean());
        verify(celebrationEventRepository, never()).findEventScheduleAssignmentsByAssignmentType(any(), anyString());
        verify(celebrationEventRepository, never()).findEventScheduleAssignments(any(), anyString());
        assertEquals(assignmentsBefore, countAssignments());
    }

    @Test
    void shouldPropagateParallelAssignmentBatchFailureWithoutLegacyFallbackOrWrites() {
        long assignmentsBefore = countAssignments();
        when(celebrationEventRepository.findEventScheduleEventsByAssignments(
                any(Pageable.class),
                any(),
                any(),
                anyString(),
                anyBoolean()
        )).thenReturn(new PageImpl<>(List.of(projection()), Pageable.ofSize(10), 1));
        when(celebrationEventRepository.findEventScheduleAssignmentsByAssignmentType(List.of(1L), "READER"))
                .thenThrow(new IllegalStateException("controlled parallel batch failure"));

        assertThrows(Exception.class, () -> mockMvc.perform(get(URL)
                .with(user("operator").roles("OPERATOR"))).andReturn());

        verify(celebrationEventRepository).findEventScheduleEventsByAssignments(
                any(Pageable.class),
                any(),
                any(),
                anyString(),
                anyBoolean()
        );
        verify(celebrationEventRepository).findEventScheduleAssignmentsByAssignmentType(List.of(1L), "READER");
        verify(celebrationEventRepository, never()).findEventScheduleEvents(any(), any(), any(), anyString(), anyBoolean());
        verify(celebrationEventRepository, never()).findEventScheduleAssignments(any(), anyString());
        assertEquals(assignmentsBefore, countAssignments());
    }

    private long countAssignments() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_event_assignment", Long.class);
        return count == null ? 0 : count;
    }

    private EventScheduleEventProjection projection() {
        return new EventScheduleEventProjection() {
            @Override
            public Long getEventId() {
                return 1L;
            }

            @Override
            public String getEventName() {
                return "Missa";
            }

            @Override
            public LocalDate getEventDate() {
                return LocalDate.of(2025, 7, 13);
            }

            @Override
            public LocalTime getEventTime() {
                return LocalTime.of(19, 30);
            }

            @Override
            public Boolean getMassOrCelebration() {
                return true;
            }

            @Override
            public Long getLocationId() {
                return 1L;
            }

            @Override
            public String getChurchName() {
                return "Igreja Matriz";
            }
        };
    }
}
