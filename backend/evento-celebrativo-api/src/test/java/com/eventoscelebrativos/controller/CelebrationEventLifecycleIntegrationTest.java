package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.security.WithMockAuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, com contexto Spring e banco H2 reais, o ciclo de vida completo de cancelamento/reativacao
 * exposto via HTTP (secoes 39-41, 67-69 da feature celebration-cancellation-lifecycle): GET /eventos
 * e GET /eventos/{id} continuam encontrando um evento CANCELLED (nunca 404), expondo o campo status;
 * cancelar e reativar retornam 200 com o estado final; idempotencia observavel via HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockAuthenticatedUser(authorities = {"ROLE_ADMIN"})
class CelebrationEventLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cancelledEventRemainsVisibleWithStatusInListAndDetailAndReactivateRestoresIt() throws Exception {
        String createResponse = mockMvc.perform(post("/eventos").contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Evento Lifecycle HTTP")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long eventId = extractId(createResponse);

        mockMvc.perform(get("/eventos/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(put("/eventos/" + eventId + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // GET /eventos/{id} nunca vira 404 por causa do cancelamento (secao 40).
        mockMvc.perform(get("/eventos/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // GET /eventos (listagem) preserva o evento CANCELLED (secao 39).
        mockMvc.perform(get("/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + eventId + ")].status").value("CANCELLED"));

        // Idempotencia observavel via HTTP: cancelar um evento ja CANCELLED continua 200/CANCELLED.
        mockMvc.perform(put("/eventos/" + eventId + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/eventos/" + eventId + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/eventos/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Idempotencia observavel via HTTP: reativar um evento ja ACTIVE continua 200/ACTIVE.
        mockMvc.perform(delete("/eventos/" + eventId + "/cancelamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void cancellingUnknownEventReturnsNotFound() throws Exception {
        mockMvc.perform(put("/eventos/999999999/cancelamento"))
                .andExpect(status().isNotFound());
    }

    private String eventPayload(String name) {
        return """
                {
                  "nameMassOrEvent": "%s",
                  "startAt": "2027-07-15T19:00:00",
                  "endAt": "2027-07-15T20:00:00",
                  "massOrCelebration": true
                }
                """.formatted(name);
    }

    private long extractId(String jsonResponse) {
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
        if (!matcher.find()) {
            throw new IllegalStateException("Resposta sem campo id: " + jsonResponse);
        }
        return Long.parseLong(matcher.group(1));
    }
}
