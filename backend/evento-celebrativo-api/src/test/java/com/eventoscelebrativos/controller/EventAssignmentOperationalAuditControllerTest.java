package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.EventAssignmentAuditEventDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditIssueDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditResponseDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditSummaryDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssueType;
import com.eventoscelebrativos.service.EventAssignmentOperationalAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventAssignmentOperationalAuditController.class)
@Import(EventAssignmentOperationalAuditControllerTest.MethodSecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class EventAssignmentOperationalAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventAssignmentOperationalAuditService eventAssignmentOperationalAuditService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Test
    void shouldReturnAuditContractAndForwardFilters() throws Exception {
        when(eventAssignmentOperationalAuditService.audit(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31)),
                eq(7L),
                eq(0),
                eq(20),
                eq(true)
        )).thenReturn(responseWithDetails());

        mockMvc.perform(get("/admin/event-assignments/consistency")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .param("eventId", "7")
                        .param("page", "0")
                        .param("size", "20")
                        .param("includeDetails", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(1))
                .andExpect(jsonPath("$.summary.inconsistentEvents").value(1))
                .andExpect(jsonPath("$.summary.missingParallelAssignments").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.events[0].eventId").value(7))
                .andExpect(jsonPath("$.events[0].consistent").value(false))
                .andExpect(jsonPath("$.events[0].legacyParticipantCount").value(1))
                .andExpect(jsonPath("$.events[0].parallelAssignmentCount").value(0))
                .andExpect(jsonPath("$.events[0].issues[0].issueType").value("MISSING_PARALLEL_ASSIGNMENT"))
                .andExpect(jsonPath("$.events[0].issues[0].personId").value(10))
                .andExpect(jsonPath("$.events[0].issues[0].legacyType").value("READER"))
                .andExpect(jsonPath("$.events[0].issues[0].parallelType").doesNotExist())
                .andExpect(jsonPath("$.events[0].issues[0].assignmentId").doesNotExist())
                .andExpect(jsonPath("$.events[0].issues[0].legacyPersonType").doesNotExist())
                .andExpect(jsonPath("$..personName").doesNotExist())
                .andExpect(jsonPath("$..phoneNumber").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist());

        verify(eventAssignmentOperationalAuditService).audit(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                7L,
                0,
                20,
                true
        );
    }

    @Test
    void shouldOmitEventsWhenDetailsAreDisabled() throws Exception {
        when(eventAssignmentOperationalAuditService.audit(eq(null), eq(null), eq(null), eq(1), eq(10), eq(false)))
                .thenReturn(responseWithoutDetails());

        mockMvc.perform(get("/admin/event-assignments/consistency")
                        .param("page", "1")
                        .param("size", "10")
                        .param("includeDetails", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eventsChecked").value(1))
                .andExpect(jsonPath("$.events").doesNotExist());
    }

    @Test
    void shouldReturnBusinessErrorForInvalidInterval() throws Exception {
        when(eventAssignmentOperationalAuditService.audit(
                eq(LocalDate.of(2026, 2, 1)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(null),
                eq(0),
                eq(20),
                eq(true)
        )).thenThrow(new BusinessException("As datas estão inválidas"));

        mockMvc.perform(get("/admin/event-assignments/consistency")
                        .param("startDate", "2026-02-01")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        when(eventAssignmentOperationalAuditService.audit(eq(null), eq(null), eq(99L), eq(0), eq(20), eq(true)))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(get("/admin/event-assignments/consistency")
                        .param("eventId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private EventAssignmentAuditResponseDTO responseWithDetails() {
        EventAssignmentAuditIssueDTO issue = new EventAssignmentAuditIssueDTO(
                EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT,
                7L,
                10L,
                EventAssignmentType.READER,
                null
        );
        EventAssignmentAuditEventDTO event = new EventAssignmentAuditEventDTO(
                7L,
                false,
                1,
                0,
                1,
                List.of(issue)
        );
        return new EventAssignmentAuditResponseDTO(
                summary(1),
                0,
                20,
                1,
                1,
                1,
                false,
                List.of(event)
        );
    }

    private EventAssignmentAuditResponseDTO responseWithoutDetails() {
        return new EventAssignmentAuditResponseDTO(
                summary(0),
                1,
                10,
                1,
                1,
                1,
                false,
                null
        );
    }

    private EventAssignmentAuditSummaryDTO summary(int missingParallelAssignments) {
        return new EventAssignmentAuditSummaryDTO(
                1,
                missingParallelAssignments == 0 ? 1 : 0,
                missingParallelAssignments == 0 ? 0 : 1,
                1,
                missingParallelAssignments == 0 ? 1 : 0,
                missingParallelAssignments,
                missingParallelAssignments,
                0,
                0,
                0,
                0,
                0
        );
    }
}
