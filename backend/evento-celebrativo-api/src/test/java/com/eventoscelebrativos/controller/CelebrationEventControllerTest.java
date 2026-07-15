package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleAssignmentResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.service.CelebrationEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CelebrationEventController.class)
@WithMockUser(roles = "ADMIN")
@Import(CelebrationEventControllerTest.MethodSecurityConfig.class)
class CelebrationEventControllerTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime EVENT_TIME = LocalTime.of(19, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CelebrationEventService celebrationEventService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Test
    void shouldReturnCreatedWhenPostingValidEvent() throws Exception {
        when(celebrationEventService.createEvent(any())).thenReturn(response("Missa"));

        mockMvc.perform(post("/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Missa")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/eventos/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidEvent() throws Exception {
        mockMvc.perform(post("/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnOkWhenGettingEventByExistingId() throws Exception {
        when(celebrationEventService.findEventById(1L)).thenReturn(response("Missa"));

        mockMvc.perform(get("/eventos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingEvent() throws Exception {
        when(celebrationEventService.findEventById(99L)).thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(get("/eventos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingEvents() throws Exception {
        when(celebrationEventService.findAllEvents()).thenReturn(List.of(response("Missa")));

        mockMvc.perform(get("/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidEvent() throws Exception {
        when(celebrationEventService.updateEvent(eq(1L), any())).thenReturn(response("Missa Atualizada"));

        mockMvc.perform(put("/eventos/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Missa Atualizada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameMassOrEvent").value("Missa Atualizada"));
    }

    @Test
    void shouldReturnOkWhenPuttingValidEventScale() throws Exception {
        when(celebrationEventService.updateEventScale(eq(1L), any())).thenReturn(scaleResponse());

        mockMvc.perform(put("/eventos/1/escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validScalePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.location.id").value(1))
                .andExpect(jsonPath("$.priest.id").value(8));
    }

    @Test
    void shouldReturnCreatedWhenPostingValidEventWithScale() throws Exception {
        when(celebrationEventService.createEventWithScale(any())).thenReturn(scaleResponse());

        mockMvc.perform(post("/eventos/com-escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validEventWithScalePayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/eventos/1")))
                .andExpect(jsonPath("$.eventId").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidEventWithScale() throws Exception {
        mockMvc.perform(post("/eventos/com-escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidEventWithScalePayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(celebrationEventService, never()).createEventWithScale(any());
    }

    @Test
    void shouldReturnBadRequestWhenPuttingInvalidEventScale() throws Exception {
        mockMvc.perform(put("/eventos/1/escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidScalePayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(celebrationEventService, never()).updateEventScale(anyLong(), any());
    }

    @Test
    void shouldReturnNotFoundWhenPuttingScaleForMissingEvent() throws Exception {
        when(celebrationEventService.updateEventScale(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(put("/eventos/99/escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validScalePayload()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnBusinessErrorWhenPuttingScaleWithWrongPersonType() throws Exception {
        when(celebrationEventService.updateEventScale(eq(1L), any()))
                .thenThrow(new BusinessException("A pessoa informada para padre não possui o tipo correto"));

        mockMvc.perform(put("/eventos/1/escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validScalePayload()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorPutsEventScale() throws Exception {
        mockMvc.perform(put("/eventos/1/escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validScalePayload()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorPostsEventWithScale() throws Exception {
        mockMvc.perform(post("/eventos/com-escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validEventWithScalePayload()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingEvent() throws Exception {
        when(celebrationEventService.updateEvent(eq(99L), any())).thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(put("/eventos/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Missa")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingEvent() throws Exception {
        mockMvc.perform(delete("/eventos/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(celebrationEventService).deleteEventById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingEvent() throws Exception {
        doThrow(new ResourceNotFoundException("Evento celebrativo", 99L)).when(celebrationEventService).deleteEventById(99L);

        mockMvc.perform(delete("/eventos/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictWhenDeletingReferencedEvent() throws Exception {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(celebrationEventService).deleteEventById(1L);

        mockMvc.perform(delete("/eventos/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));
    }

    @Test
    void shouldReturnOkWhenFindingEucharistScaleByPeriod() throws Exception {
        EucharistScaleEventResponseDTO response = new EucharistScaleEventResponseDTO(
                "Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz"
        );
        response.getNameMinisters().add("Ana");
        response.getNameMinisters().add("Bruno");
        when(celebrationEventService.findEucharistScale(any(), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escala/eucaristia")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nameMassOrEvent").value("Missa"))
                .andExpect(jsonPath("$.content[0].nameMinisters[0]").value("Ana"));

        verify(celebrationEventService).findEucharistScale(any(), eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void shouldIgnoreInvalidSortWhenFindingEucharistScaleByPeriod() throws Exception {
        EucharistScaleEventResponseDTO response = new EucharistScaleEventResponseDTO(
                "Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz"
        );
        when(celebrationEventService.findEucharistScale(any(), eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2026, 12, 31))))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escala/eucaristia")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2026-12-31")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "[\"string\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nameMassOrEvent").value("Missa"));

        verify(celebrationEventService).findEucharistScale(
                argThat((Pageable pageable) -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().isUnsorted()),
                eq(LocalDate.of(2025, 7, 1)),
                eq(LocalDate.of(2026, 12, 31))
        );
    }

    @Test
    void shouldReturnOkWhenAdminFindsEventSchedules() throws Exception {
        when(celebrationEventService.findEventSchedules(
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 31)),
                eq(EventScheduleType.READER),
                eq(0),
                eq(10),
                eq(false)
        )).thenReturn(new PageImpl<>(List.of(scheduleResponse(EventScheduleType.READER)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("type", "READER")
                        .param("page", "0")
                        .param("size", "10")
                        .param("includeUnassigned", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(1))
                .andExpect(jsonPath("$.content[0].assignmentType").value("READER"))
                .andExpect(jsonPath("$.content[0].assignments[0].personId").value(10))
                .andExpect(jsonPath("$.content[0].assignments[0].personName").value("Maria"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnOkWhenOperatorFindsEventSchedules() throws Exception {
        when(celebrationEventService.findEventSchedules(any(), any(), eq(EventScheduleType.PRIEST), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(scheduleResponse(EventScheduleType.PRIEST)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("type", "PRIEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assignmentType").value("PRIEST"));
    }

    @Test
    void shouldReturnBadRequestWhenEventScheduleTypeIsInvalid() throws Exception {
        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("type", "INVALID"))
                .andExpect(status().isBadRequest());

        verify(celebrationEventService, never()).findEventSchedules(any(), any(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void shouldReturnBadRequestWhenRequiredEventScheduleParameterIsMissing() throws Exception {
        mockMvc.perform(get("/eventos/escalas")
                        .param("endDate", "2026-08-31")
                        .param("type", "READER"))
                .andExpect(status().isBadRequest());

        verify(celebrationEventService, never()).findEventSchedules(any(), any(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void shouldReturnBusinessErrorWhenEventSchedulePeriodIsInvalid() throws Exception {
        when(celebrationEventService.findEventSchedules(
                eq(LocalDate.of(2026, 8, 31)),
                eq(LocalDate.of(2026, 8, 1)),
                eq(EventScheduleType.READER),
                eq(0),
                eq(10),
                eq(false)
        )).thenThrow(new BusinessException("As datas estão inválidas"));

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-31")
                        .param("endDate", "2026-08-01")
                        .param("type", "READER"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void shouldUseDefaultPaginationWhenFindingEventSchedules() throws Exception {
        when(celebrationEventService.findEventSchedules(any(), any(), eq(EventScheduleType.READER), eq(0), eq(10), eq(false)))
                .thenReturn(new PageImpl<>(List.of(scheduleResponse(EventScheduleType.READER)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("type", "READER")
                        .param("sort", "[\"string\"]"))
                .andExpect(status().isOk());

        verify(celebrationEventService).findEventSchedules(
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 31)),
                eq(EventScheduleType.READER),
                eq(0),
                eq(10),
                eq(false)
        );
    }

    @Test
    void shouldApplyIncludeUnassignedWhenFindingEventSchedules() throws Exception {
        when(celebrationEventService.findEventSchedules(any(), any(), eq(EventScheduleType.PRIEST), eq(0), eq(10), eq(true)))
                .thenReturn(new PageImpl<>(List.of(scheduleResponse(EventScheduleType.PRIEST)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("type", "PRIEST")
                        .param("includeUnassigned", "true"))
                .andExpect(status().isOk());

        verify(celebrationEventService).findEventSchedules(
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 31)),
                eq(EventScheduleType.PRIEST),
                eq(0),
                eq(10),
                eq(true)
        );
    }

    private CelebrationEventResponseDTO response(String nameMassOrEvent) {
        return new CelebrationEventResponseDTO(1L, nameMassOrEvent, EVENT_DATE, EVENT_TIME, true);
    }

    private CelebrationEventScaleResponseDTO scaleResponse() {
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();
        response.setEventId(1L);
        response.setNameMassOrEvent("Missa");
        response.setEventDate(EVENT_DATE);
        response.setEventTime(EVENT_TIME);
        response.setMassOrCelebration(true);
        response.setLocation(new CelebrationEventScaleLocationResponseDTO(1L, "Igreja Matriz"));
        response.setPriest(new CelebrationEventScalePersonResponseDTO(8L, "Padre"));
        response.setReaders(List.of(new CelebrationEventScalePersonResponseDTO(2L, "Leitor")));
        return response;
    }

    private EventScheduleQueryResponseDTO scheduleResponse(EventScheduleType type) {
        EventScheduleQueryResponseDTO response = new EventScheduleQueryResponseDTO();
        response.setEventId(1L);
        response.setEventName("Missa");
        response.setEventDate(EVENT_DATE);
        response.setEventTime(EVENT_TIME);
        response.setMassOrCelebration(true);
        response.setLocationId(1L);
        response.setChurchName("Igreja Matriz");
        response.setAssignmentType(type);
        response.setAssignments(List.of(new EventScheduleAssignmentResponseDTO(10L, "Maria")));
        return response;
    }

    private String validPayload(String nameMassOrEvent) {
        return """
                {
                  "nameMassOrEvent": "%s",
                  "eventDate": "2026-08-15",
                  "eventTime": "19:30:00",
                  "massOrCelebration": true
                }
                """.formatted(nameMassOrEvent);
    }

    private String invalidPayload() {
        return """
                {
                  "nameMassOrEvent": "",
                  "eventDate": null,
                  "eventTime": null,
                  "massOrCelebration": null
                }
                """;
    }

    private String validScalePayload() {
        return """
                {
                  "locationId": 1,
                  "priestId": 8,
                  "readerIds": [2],
                  "commentatorIds": [4],
                  "ministerOfTheWordIds": [5],
                  "eucharisticMinisterIds": [6]
                }
                """;
    }

    private String invalidScalePayload() {
        return """
                {
                  "locationId": null,
                  "readerIds": [0]
                }
                """;
    }

    private String validEventWithScalePayload() {
        return """
                {
                  "nameMassOrEvent": "Missa",
                  "eventDate": "2026-08-15",
                  "eventTime": "19:30:00",
                  "massOrCelebration": true,
                  "locationId": 1,
                  "priestId": 8,
                  "readerIds": [2],
                  "commentatorIds": [4],
                  "ministerOfTheWordIds": [5],
                  "eucharisticMinisterIds": [6]
                }
                """;
    }

    private String invalidEventWithScalePayload() {
        return """
                {
                  "nameMassOrEvent": "",
                  "eventDate": null,
                  "eventTime": null,
                  "massOrCelebration": null,
                  "locationId": null
                }
                """;
    }
}
