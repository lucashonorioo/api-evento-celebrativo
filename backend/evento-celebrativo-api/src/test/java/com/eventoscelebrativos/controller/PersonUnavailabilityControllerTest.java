package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.TemporalJsonConfig;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityRangeDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityAssignmentConflictException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException;
import com.eventoscelebrativos.service.PersonUnavailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonUnavailabilityController.class)
@Import({PersonUnavailabilityControllerTest.MethodSecurityTestConfig.class, TemporalJsonConfig.class})
class PersonUnavailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonUnavailabilityService personUnavailabilityService;

    @Test
    @WithMockUser(username = "34970000001", roles = "OPERATOR")
    void shouldListMyUnavailabilitiesWhenOperator() throws Exception {
        when(personUnavailabilityService.findMine(eq("34970000001"), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(
                        List.of(new PersonUnavailabilityResponseDTO(1L, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 12, 0, 0), "Viagem")),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas/me/indisponibilidades")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].startAt").value("2026-08-10T00:00:00"))
                .andExpect(jsonPath("$.content[0].endAt").value("2026-08-12T00:00:00"))
                .andExpect(jsonPath("$.content[0].reason").value("Viagem"));
    }

    @Test
    @WithMockUser(username = "34970000001", roles = "OPERATOR")
    void shouldRejectNonCanonicalQueryDateTimes() throws Exception {
        for (String invalidStartAt : List.of(
                "2026-08-01T00:00:00.1",
                "2026-08-01T00:00:00-03:00",
                "2026-08-01T03:00:00Z"
        )) {
            mockMvc.perform(get("/pessoas/me/indisponibilidades")
                            .param("startAt", invalidStartAt)
                            .param("endAt", "2026-08-31T00:00:00"))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000001", roles = "OPERATOR")
    void shouldReturnStructuredBadRequestForFractionalUnavailabilityBody() throws Exception {
        when(personUnavailabilityService.create(eq("34970000001"), any()))
                .thenThrow(new TemporalPrecisionNotSupportedException());

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T08:00:00.123",
                                  "endAt": "2026-08-10T12:00:00",
                                  "reason": "Consulta médica"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("TEMPORAL_PRECISION_NOT_SUPPORTED"));
    }

    @Test
    @WithMockUser(username = "34970000012", roles = "OPERATOR")
    void shouldReturnBadRequestWhenCreateBodyStartAtIsMissingSeconds() throws Exception {
        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T08:00",
                                  "endAt": "2026-08-10T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000013", roles = "OPERATOR")
    void shouldReturnBadRequestWhenCreateBodyStartAtIsInvalidText() throws Exception {
        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "horario-invalido",
                                  "endAt": "2026-08-10T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000014", roles = "ADMIN")
    void shouldReturnBadRequestWhenUpdateBodyStartAtIsMissingSeconds() throws Exception {
        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T08:00",
                                  "endAt": "2026-08-10T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verify(personUnavailabilityService, never()).update(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "34970000015", roles = "ADMIN")
    void shouldReturnStructuredBadRequestForFractionalUpdateBody() throws Exception {
        when(personUnavailabilityService.update(eq("34970000015"), eq(1L), any()))
                .thenThrow(new TemporalPrecisionNotSupportedException());

        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T08:00:00.123",
                                  "endAt": "2026-08-10T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("TEMPORAL_PRECISION_NOT_SUPPORTED"));
    }

    @Test
    @WithMockUser(username = "34970000002", roles = "ADMIN")
    void shouldCreateUnavailabilityAndReturn201() throws Exception {
        when(personUnavailabilityService.create(eq("34970000002"), any()))
                .thenReturn(new PersonUnavailabilityResponseDTO(5L, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 12, 0, 0), null));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-12T00:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(username = "34970000003", roles = "ADMIN")
    void shouldIgnoreExtraPersonIdFieldInRequestBody() throws Exception {
        when(personUnavailabilityService.create(eq("34970000003"), any()))
                .thenReturn(new PersonUnavailabilityResponseDTO(6L, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 12, 0, 0), null));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-12T00:00:00",
                                  "personId": 999
                                }
                                """))
                .andExpect(status().isCreated());

        verify(personUnavailabilityService).create(eq("34970000003"), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenRequestBodyMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000004", roles = "ADMIN")
    void shouldReturn409WithOverlapErrorCode() throws Exception {
        when(personUnavailabilityService.create(eq("34970000004"), any()))
                .thenThrow(new UnavailabilityOverlapException("O período informado se sobrepõe a uma indisponibilidade já cadastrada."));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-12T00:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNAVAILABILITY_OVERLAP"));
    }

    @Test
    @WithMockUser(username = "34970000005", roles = "ADMIN")
    void shouldReturn409WithAssignmentConflictStructuredResponse() throws Exception {
        when(personUnavailabilityService.create(eq("34970000005"), any()))
                .thenThrow(new UnavailabilityAssignmentConflictException(List.of(
                        new EventAssignmentConflictDTO(15L, "Missa das 19h", LocalDateTime.of(2026, 8, 15, 19, 0), LocalDateTime.of(2026, 8, 15, 20, 0),
                                List.of("READER", "COMMENTATOR"))
                )));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-15T00:00:00",
                                  "endAt": "2026-08-16T00:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNAVAILABILITY_CONFLICT_WITH_ASSIGNMENT"))
                .andExpect(jsonPath("$.conflicts[0].eventId").value(15))
                .andExpect(jsonPath("$.conflicts[0].assignments[0]").value("READER"))
                .andExpect(jsonPath("$.conflicts[0].assignments[1]").value("COMMENTATOR"));
    }

    @Test
    @WithMockUser(username = "34970000006", roles = "ADMIN")
    void shouldUpdateUnavailability() throws Exception {
        when(personUnavailabilityService.update(eq("34970000006"), eq(1L), any()))
                .thenReturn(new PersonUnavailabilityResponseDTO(1L, LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 15, 0, 0), "Atualizado"));

        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-15T00:00:00",
                                  "reason": "Atualizado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endAt").value("2026-08-15T00:00:00"))
                .andExpect(jsonPath("$.reason").value("Atualizado"));
    }

    @Test
    @WithMockUser(username = "34970000007", roles = "ADMIN")
    void shouldReturn404WhenUpdatingRecordThatDoesNotBelongToCaller() throws Exception {
        when(personUnavailabilityService.update(eq("34970000007"), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Indisponibilidade", 99L));

        mockMvc.perform(put("/pessoas/me/indisponibilidades/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-12T00:00:00"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "34970000008", roles = "ADMIN")
    void shouldReturn400WhenStartAtIsInThePast() throws Exception {
        when(personUnavailabilityService.update(eq("34970000008"), eq(1L), any()))
                .thenThrow(new BadRequestException("startAt não pode ser anterior ao instante atual"));

        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2020-01-01T00:00:00",
                                  "endAt": "2020-01-02T00:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(username = "34970000009", roles = "ADMIN")
    void shouldDeleteUnavailabilityAndReturn204() throws Exception {
        mockMvc.perform(delete("/pessoas/me/indisponibilidades/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(personUnavailabilityService).delete("34970000009", 1L);
    }

    @Test
    @WithMockUser(username = "34970000010", roles = "ADMIN")
    void shouldReturn404WhenDeletingRecordThatDoesNotBelongToCaller() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Indisponibilidade", 99L))
                .when(personUnavailabilityService).delete("34970000010", 99L);

        mockMvc.perform(delete("/pessoas/me/indisponibilidades/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnAdminUnavailabilityByRangeWithoutSensitiveFields() throws Exception {
        when(personUnavailabilityService.findByDate(LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 11, 0, 0)))
                .thenReturn(new AdminUnavailabilityResponseDTO(
                        LocalDateTime.of(2026, 8, 10, 0, 0),
                        LocalDateTime.of(2026, 8, 11, 0, 0),
                        List.of(new AdminUnavailabilityPersonDTO(4L, "Arthur Costa",
                                List.of(new AdminUnavailabilityRangeDTO(LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 12, 0, 0)))))
                ));

        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2026-08-10T00:00:00"))
                .andExpect(jsonPath("$.people[0].personId").value(4))
                .andExpect(jsonPath("$.people[0].personName").value("Arthur Costa"))
                .andExpect(jsonPath("$.people[0].unavailabilities[0].startAt").value("2026-08-10T00:00:00"))
                .andExpect(jsonPath("$.people[0].reason").doesNotExist())
                .andExpect(jsonPath("$.people[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.people[0].password").doesNotExist())
                .andExpect(jsonPath("$.people[0].roles").doesNotExist())
                .andExpect(jsonPath("$.people[0].ministries").doesNotExist())
                .andExpect(jsonPath("$.people[0].assignments").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorUsesAdminEndpoint() throws Exception {
        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000011", roles = "ADMIN")
    void shouldReturn409StructuredResponseForPersonUnavailableForEvent() throws Exception {
        when(personUnavailabilityService.create(eq("34970000011"), any()))
                .thenThrow(new PersonUnavailableForEventException(List.of(
                        new PersonUnavailabilityEventConflictDTO(4L, "Arthur Costa", List.of("READER", "COMMENTATOR"),
                                LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 12, 0, 0))
                )));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-08-10T00:00:00",
                                  "endAt": "2026-08-12T00:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSON_UNAVAILABLE_FOR_EVENT"))
                .andExpect(jsonPath("$.conflicts[0].personId").value(4));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
