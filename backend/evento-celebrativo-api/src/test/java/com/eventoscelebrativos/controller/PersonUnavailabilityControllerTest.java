package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
@Import(PersonUnavailabilityControllerTest.MethodSecurityTestConfig.class)
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
                        List.of(new PersonUnavailabilityResponseDTO(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), "Viagem")),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas/me/indisponibilidades")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].startDate").value("2026-08-10"))
                .andExpect(jsonPath("$.content[0].endDate").value("2026-08-12"))
                .andExpect(jsonPath("$.content[0].reason").value("Viagem"));
    }

    @Test
    @WithMockUser(username = "34970000002", roles = "ADMIN")
    void shouldCreateUnavailabilityAndReturn201() throws Exception {
        when(personUnavailabilityService.create(eq("34970000002"), any()))
                .thenReturn(new PersonUnavailabilityResponseDTO(5L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(username = "34970000003", roles = "ADMIN")
    void shouldIgnoreExtraPersonIdFieldInRequestBody() throws Exception {
        when(personUnavailabilityService.create(eq("34970000003"), any()))
                .thenReturn(new PersonUnavailabilityResponseDTO(6L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12",
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
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12"
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
                        new EventAssignmentConflictDTO(15L, "Missa das 19h", LocalDate.of(2026, 8, 15), LocalTime.of(19, 0),
                                List.of("READER", "COMMENTATOR"))
                )));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-15",
                                  "endDate": "2026-08-15"
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
                .thenReturn(new PersonUnavailabilityResponseDTO(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 15), "Atualizado"));

        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-15",
                                  "reason": "Atualizado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2026-08-15"))
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
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "34970000008", roles = "ADMIN")
    void shouldReturn400WhenStartDateIsInThePast() throws Exception {
        when(personUnavailabilityService.update(eq("34970000008"), eq(1L), any()))
                .thenThrow(new BadRequestException("A data inicial não pode ser anterior à data atual"));

        mockMvc.perform(put("/pessoas/me/indisponibilidades/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2020-01-01",
                                  "endDate": "2020-01-02"
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
    void shouldReturnAdminUnavailabilityByDateWithoutSensitiveFields() throws Exception {
        when(personUnavailabilityService.findByDate(LocalDate.of(2026, 8, 10)))
                .thenReturn(new AdminUnavailabilityResponseDTO(
                        LocalDate.of(2026, 8, 10),
                        List.of(new AdminUnavailabilityPersonDTO(4L, "Arthur Costa", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                ));

        mockMvc.perform(get("/pessoas/indisponibilidades").param("date", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-10"))
                .andExpect(jsonPath("$.people[0].personId").value(4))
                .andExpect(jsonPath("$.people[0].personName").value("Arthur Costa"))
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
        mockMvc.perform(get("/pessoas/indisponibilidades").param("date", "2026-08-10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personUnavailabilityService);
    }

    @Test
    @WithMockUser(username = "34970000011", roles = "ADMIN")
    void shouldReturn409StructuredResponseForPersonUnavailableForEvent() throws Exception {
        when(personUnavailabilityService.create(eq("34970000011"), any()))
                .thenThrow(new PersonUnavailableForEventException(List.of(
                        new PersonUnavailabilityEventConflictDTO(4L, "Arthur Costa", List.of("READER", "COMMENTATOR"),
                                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
                )));

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12"
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
