package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.CelebrationEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CelebrationEventController.class)
@WithMockUser(roles = "ADMIN")
class CelebrationEventControllerTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime EVENT_TIME = LocalTime.of(19, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CelebrationEventService celebrationEventService;

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

    private CelebrationEventResponseDTO response(String nameMassOrEvent) {
        return new CelebrationEventResponseDTO(1L, nameMassOrEvent, EVENT_DATE, EVENT_TIME, true);
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
}
