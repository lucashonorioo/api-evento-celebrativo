package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.TemporalJsonConfig;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationPersonResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleAssignmentResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleConflictUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.service.ScheduleUnavailabilityConflictService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.LocalDateTime;
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
@Import({CelebrationEventControllerTest.MethodSecurityConfig.class, TemporalJsonConfig.class})
class CelebrationEventControllerTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime EVENT_TIME = LocalTime.of(19, 30);
    private static final LocalDateTime EVENT_START_AT = LocalDateTime.of(2026, 8, 15, 19, 30);
    private static final LocalDateTime EVENT_END_AT = LocalDateTime.of(2026, 8, 15, 20, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CelebrationEventService celebrationEventService;

    @MockitoBean
    private ScheduleUnavailabilityConflictService scheduleUnavailabilityConflictService;

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
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.startAt").value("2026-08-15T19:30:00"))
                .andExpect(jsonPath("$.endAt").value("2026-08-15T20:30:00"))
                .andExpect(jsonPath("$.eventDate").doesNotExist())
                .andExpect(jsonPath("$.eventTime").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidEvent() throws Exception {
        mockMvc.perform(post("/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnStructuredBadRequestForUnsupportedTemporalFractions() throws Exception {
        when(celebrationEventService.createEvent(any()))
                .thenThrow(new TemporalPrecisionNotSupportedException());

        for (String fraction : List.of(".1", ".123", ".123456")) {
            mockMvc.perform(post("/eventos")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fractionalPayload(fraction)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode")
                            .value("TEMPORAL_PRECISION_NOT_SUPPORTED"));
        }
    }

    @Test
    void shouldRejectLegacyEventDateAndEventTimeWriteContract() throws Exception {
        String legacyPayload = """
                {
                  "nameMassOrEvent": "Missa legada",
                  "startAt": "2026-08-15T19:30:00",
                  "endAt": "2026-08-15T20:30:00",
                  "eventDate": "2026-08-15",
                  "eventTime": "19:30:00",
                  "massOrCelebration": true
                }
                """;

        mockMvc.perform(post("/eventos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldRejectOffsetAndUtcEventDateTimes() throws Exception {
        for (String invalidDateTime : List.of(
                "2026-08-15T19:30:00-03:00",
                "2026-08-15T22:30:00Z"
        )) {
            String payload = """
                    {
                      "nameMassOrEvent": "Missa",
                      "startAt": "%s",
                      "endAt": "2026-08-15T23:30:00",
                      "massOrCelebration": true
                    }
                    """.formatted(invalidDateTime);

            mockMvc.perform(post("/eventos")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        }

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnBadRequestWhenCommonEventStartAtIsMissingSeconds() throws Exception {
        String payload = """
                {
                  "nameMassOrEvent": "Missa",
                  "startAt": "2026-08-15T19:30",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true
                }
                """;

        mockMvc.perform(post("/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnBadRequestWhenCommonEventStartAtIsInvalidText() throws Exception {
        String payload = """
                {
                  "nameMassOrEvent": "Missa",
                  "startAt": "horario-invalido",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true
                }
                """;

        mockMvc.perform(post("/eventos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnBadRequestWhenEventWithScaleStartAtIsMissingSeconds() throws Exception {
        String payload = """
                {
                  "nameMassOrEvent": "Missa",
                  "startAt": "2026-08-15T19:30",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true,
                  "locationId": 1,
                  "priestId": 8
                }
                """;

        mockMvc.perform(post("/eventos/com-escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verify(celebrationEventService, never()).createEventWithScale(any());
    }

    @Test
    void shouldReturnStructuredBadRequestForUnsupportedTemporalFractionsOnEventWithScale() throws Exception {
        when(celebrationEventService.createEventWithScale(any()))
                .thenThrow(new TemporalPrecisionNotSupportedException());

        String payload = """
                {
                  "nameMassOrEvent": "Missa",
                  "startAt": "2026-08-15T19:30:00.123",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true,
                  "locationId": 1,
                  "priestId": 8
                }
                """;

        mockMvc.perform(post("/eventos/com-escala").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TEMPORAL_PRECISION_NOT_SUPPORTED"));
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
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorPostsCommonEvent() throws Exception {
        mockMvc.perform(post("/eventos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Missa")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorUpdatesCommonEvent() throws Exception {
        mockMvc.perform(put("/eventos/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Missa")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(celebrationEventService);
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
    void shouldReturnOkWhenAdminGetsEventScaleDetail() throws Exception {
        when(celebrationEventService.findScaleByEventId(1L)).thenReturn(scaleDetailResponse());

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.eventName").value("Missa"))
                .andExpect(jsonPath("$.startAt").value("2026-08-15T19:30:00"))
                .andExpect(jsonPath("$.endAt").value("2026-08-15T20:30:00"))
                .andExpect(jsonPath("$.eventDate").doesNotExist())
                .andExpect(jsonPath("$.eventTime").doesNotExist())
                .andExpect(jsonPath("$.massOrCelebration").value(true))
                .andExpect(jsonPath("$.location.id").value(1))
                .andExpect(jsonPath("$.location.churchName").value("Igreja Matriz"))
                .andExpect(jsonPath("$.priest.id").value(13))
                .andExpect(jsonPath("$.readers[0].id").value(4))
                .andExpect(jsonPath("$.commentators[0].id").value(1))
                .andExpect(jsonPath("$.ministersOfTheWord[0].id").value(7))
                .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(10))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.readers[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.readers[0].birthdayDate").doesNotExist())
                .andExpect(jsonPath("$.readers[0].password").doesNotExist())
                .andExpect(jsonPath("$.readers[0].roles").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnOkWhenOperatorGetsEventScaleDetail() throws Exception {
        when(celebrationEventService.findScaleByEventId(1L)).thenReturn(scaleDetailResponse());

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingEventScaleDetail() throws Exception {
        when(celebrationEventService.findScaleByEventId(99L))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(get("/eventos/99/escala"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldSerializeNullsAndEmptyListsWhenGettingEventScaleDetail() throws Exception {
        CelebrationEventScaleDetailResponseDTO response = scaleDetailResponse();
        response.setLocation(null);
        response.setPriest(null);
        response.setReaders(List.of());
        response.setCommentators(List.of());
        response.setMinistersOfTheWord(List.of());
        response.setEucharisticMinisters(List.of());
        when(celebrationEventService.findScaleByEventId(1L)).thenReturn(response);

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.priest").doesNotExist())
                .andExpect(jsonPath("$.readers").isArray())
                .andExpect(jsonPath("$.readers").isEmpty())
                .andExpect(jsonPath("$.commentators").isEmpty())
                .andExpect(jsonPath("$.ministersOfTheWord").isEmpty())
                .andExpect(jsonPath("$.eucharisticMinisters").isEmpty());
    }

    @Test
    void shouldReturnOkWhenAdminGetsEventScaleParticipation() throws Exception {
        when(celebrationEventService.findScaleParticipationByEventId(1L)).thenReturn(scaleParticipationResponse());

        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.priest.id").value(13))
                .andExpect(jsonPath("$.priest.participationStatus").value("PENDING"))
                .andExpect(jsonPath("$.readers[0].id").value(4))
                .andExpect(jsonPath("$.readers[0].participationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.readers[1].id").value(5))
                .andExpect(jsonPath("$.readers[1].participationStatus").value("DECLINED"))
                .andExpect(jsonPath("$.readers[1].declineReason").value("Viagem"))
                .andExpect(jsonPath("$.readers[1].respondedAt").exists());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorGetsEventScaleParticipation() throws Exception {
        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(celebrationEventService);
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingEventScaleParticipation() throws Exception {
        when(celebrationEventService.findScaleParticipationByEventId(99L))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(get("/eventos/99/escala/participacoes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldShowParticipationDataAcrossDifferentCategories() throws Exception {
        CelebrationEventScaleParticipationDetailResponseDTO response = scaleParticipationResponse();
        response.setCommentators(List.of(new CelebrationEventScaleParticipationPersonResponseDTO(
                6L, "Helena Oliveira", ParticipationStatus.CONFIRMED, null, null
        )));
        when(celebrationEventService.findScaleParticipationByEventId(1L)).thenReturn(response);

        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers[0].participationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.commentators[0].participationStatus").value("CONFIRMED"));
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
                "Missa", EVENT_START_AT, EVENT_END_AT, "Igreja Matriz"
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
                "Missa", EVENT_START_AT, EVENT_END_AT, "Igreja Matriz"
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

    @Test
    void shouldReturnConflictsByEventId() throws Exception {
        when(scheduleUnavailabilityConflictService.findByEventId(1L)).thenReturn(List.of(
                new ScheduleUnavailabilityConflictResponseDTO(
                        1L, "Missa de Domingo", EVENT_START_AT, EVENT_END_AT,
                        4L, "Arthur Costa", "READER",
                        List.of(new ScheduleConflictUnavailabilityResponseDTO(
                                21L, EVENT_START_AT.minusMinutes(30), EVENT_END_AT.plusHours(1)))
                )
        ));

        mockMvc.perform(get("/eventos/1/escala/conflitos-indisponibilidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[0].personId").value(4))
                .andExpect(jsonPath("$[0].personName").value("Arthur Costa"))
                .andExpect(jsonPath("$[0].assignmentType").value("READER"))
                .andExpect(jsonPath("$[0].unavailabilities[0].id").value(21))
                .andExpect(jsonPath("$[0].unavailabilities[0].reason").doesNotExist());
    }

    @Test
    void shouldReturnEmptyListWhenEventHasNoConflicts() throws Exception {
        when(scheduleUnavailabilityConflictService.findByEventId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/eventos/1/escala/conflitos-indisponibilidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenEventDoesNotExistForConflictsByEvent() throws Exception {
        when(scheduleUnavailabilityConflictService.findByEventId(99L))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(get("/eventos/99/escala/conflitos-indisponibilidade"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorRequestsConflictsByEvent() throws Exception {
        mockMvc.perform(get("/eventos/1/escala/conflitos-indisponibilidade"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleUnavailabilityConflictService);
    }

    @Test
    void shouldReturnPaginatedConflictsByRange() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(0), eq(10)
        )).thenReturn(new PageImpl<>(List.of(
                new ScheduleUnavailabilityConflictResponseDTO(
                        1L, "Missa de Domingo", EVENT_START_AT, EVENT_END_AT,
                        4L, "Arthur Costa", "READER", List.of())
        ), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-08-01T00:00:00.123",   // fracao de segundo
            "2026-08-01T00:00:00Z",      // sufixo Z
            "2026-08-01T00:00:00-03:00", // offset
            "2026-08-01T00:00",          // sem segundos
            "horario-invalido"           // texto invalido
    })
    void shouldRejectMalformedStartAtOnPaginatedConflictsQuery(String malformedStartAt) throws Exception {
        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", malformedStartAt)
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(scheduleUnavailabilityConflictService);
    }

    @Test
    void shouldRejectMissingStartAtOnPaginatedConflictsQuery() throws Exception {
        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scheduleUnavailabilityConflictService);
    }

    @Test
    void shouldRejectMissingEndAtOnPaginatedConflictsQuery() throws Exception {
        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scheduleUnavailabilityConflictService);
    }

    @Test
    void shouldRejectEqualStartAtAndEndAtOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(0), eq(10)
        )).thenThrow(new BadRequestException("startAt deve ser anterior a endAt"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectInvertedRangeOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(0), eq(10)
        )).thenThrow(new BadRequestException("startAt deve ser anterior a endAt"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-31T00:00:00")
                        .param("endAt", "2026-08-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectNegativePageOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(-1), eq(10)
        )).thenThrow(new BadRequestException("O número da página deve ser maior ou igual a zero"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectZeroSizeOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(0), eq(0)
        )).thenThrow(new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectNegativeSizeOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(0), eq(-5)
        )).thenThrow(new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00")
                        .param("size", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectSizeAboveOneHundredOnPaginatedConflictsQuery() throws Exception {
        when(scheduleUnavailabilityConflictService.findByRange(
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)), eq(LocalDateTime.of(2026, 8, 31, 0, 0)), eq(0), eq(101)
        )).thenThrow(new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorRequestsPaginatedConflicts() throws Exception {
        mockMvc.perform(get("/eventos/escala/conflitos-indisponibilidade")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleUnavailabilityConflictService);
    }

    private CelebrationEventResponseDTO response(String nameMassOrEvent) {
        return new CelebrationEventResponseDTO(1L, nameMassOrEvent, EVENT_START_AT, EVENT_END_AT, true);
    }

    private CelebrationEventScaleResponseDTO scaleResponse() {
        CelebrationEventScaleResponseDTO response = new CelebrationEventScaleResponseDTO();
        response.setEventId(1L);
        response.setNameMassOrEvent("Missa");
        response.setStartAt(EVENT_START_AT);
        response.setEndAt(EVENT_END_AT);
        response.setMassOrCelebration(true);
        response.setLocation(new CelebrationEventScaleLocationResponseDTO(1L, "Igreja Matriz"));
        response.setPriest(new CelebrationEventScalePersonResponseDTO(8L, "Padre"));
        response.setReaders(List.of(new CelebrationEventScalePersonResponseDTO(2L, "Leitor")));
        return response;
    }

    private CelebrationEventScaleDetailResponseDTO scaleDetailResponse() {
        CelebrationEventScaleDetailResponseDTO response = new CelebrationEventScaleDetailResponseDTO();
        response.setEventId(1L);
        response.setEventName("Missa");
        response.setStartAt(EVENT_START_AT);
        response.setEndAt(EVENT_END_AT);
        response.setMassOrCelebration(true);
        response.setLocation(new CelebrationEventScaleLocationResponseDTO(1L, "Igreja Matriz"));
        response.setPriest(new CelebrationEventScalePersonResponseDTO(13L, "Padre Miguel"));
        response.setReaders(List.of(
                new CelebrationEventScalePersonResponseDTO(4L, "Alice Lima"),
                new CelebrationEventScalePersonResponseDTO(5L, "Arthur Costa")
        ));
        response.setCommentators(List.of(new CelebrationEventScalePersonResponseDTO(1L, "Luana Odinson")));
        response.setMinistersOfTheWord(List.of(new CelebrationEventScalePersonResponseDTO(7L, "Davi Gomes")));
        response.setEucharisticMinisters(List.of(
                new CelebrationEventScalePersonResponseDTO(10L, "Mariana Ferraz"),
                new CelebrationEventScalePersonResponseDTO(11L, "Carlos Silva")
        ));
        return response;
    }

    private CelebrationEventScaleParticipationDetailResponseDTO scaleParticipationResponse() {
        CelebrationEventScaleParticipationDetailResponseDTO response = new CelebrationEventScaleParticipationDetailResponseDTO();
        response.setEventId(1L);
        response.setEventName("Missa");
        response.setStartAt(EVENT_START_AT);
        response.setEndAt(EVENT_END_AT);
        response.setMassOrCelebration(true);
        response.setLocation(new CelebrationEventScaleLocationResponseDTO(1L, "Igreja Matriz"));
        response.setPriest(new CelebrationEventScaleParticipationPersonResponseDTO(
                13L, "Padre Miguel", ParticipationStatus.PENDING, null, null
        ));
        response.setReaders(List.of(
                new CelebrationEventScaleParticipationPersonResponseDTO(
                        4L, "Alice Lima", ParticipationStatus.CONFIRMED, null, LocalDateTime.of(2026, 7, 30, 18, 20)
                ),
                new CelebrationEventScaleParticipationPersonResponseDTO(
                        5L, "Arthur Costa", ParticipationStatus.DECLINED, "Viagem", LocalDateTime.of(2026, 7, 30, 18, 21)
                )
        ));
        response.setCommentators(List.of());
        response.setMinistersOfTheWord(List.of());
        response.setEucharisticMinisters(List.of());
        return response;
    }

    private EventScheduleQueryResponseDTO scheduleResponse(EventScheduleType type) {
        EventScheduleQueryResponseDTO response = new EventScheduleQueryResponseDTO();
        response.setEventId(1L);
        response.setEventName("Missa");
        response.setStartAt(EVENT_START_AT);
        response.setEndAt(EVENT_END_AT);
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
                  "startAt": "2026-08-15T19:30:00",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true
                }
                """.formatted(nameMassOrEvent);
    }

    private String invalidPayload() {
        return """
                {
                  "nameMassOrEvent": "",
                  "startAt": null,
                  "endAt": null,
                  "massOrCelebration": null
                }
                """;
    }

    private String fractionalPayload(String fraction) {
        return """
                {
                  "nameMassOrEvent": "Missa",
                  "startAt": "2026-08-15T19:30:00%s",
                  "endAt": "2026-08-15T20:30:00",
                  "massOrCelebration": true
                }
                """.formatted(fraction);
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
                  "startAt": "2026-08-15T19:30:00",
                  "endAt": "2026-08-15T20:30:00",
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
                  "startAt": null,
                  "endAt": null,
                  "massOrCelebration": null,
                  "locationId": null
                }
                """;
    }
}
