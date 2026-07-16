package com.eventoscelebrativos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAllowPublicEventEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/eventos"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/escala/eucaristia")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2026-12-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowPublicEucharistScaleEndpointWithInvalidSortWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/eventos/escala/eucaristia")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2026-12-31")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "[\"string\"]"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowSwaggerEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRequireAuthenticationForPeopleAndLocationLists() throws Exception {
        mockMvc.perform(get("/locais"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/leitores"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/comentaristas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ministrosDeEucaristia"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ministrosDaPalavra"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/padres"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireAuthenticationForAdministrativeEventEndpoints() throws Exception {
        mockMvc.perform(post("/eventos/com-escala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventWithScalePayload()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-31")
                        .param("type", "READER"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectOperatorOnAdministrativeEndpoints() throws Exception {
        mockMvc.perform(post("/locais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLocationPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolePayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/eventos/com-escala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventWithScalePayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/eventos/1/escala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validScalePayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-31")
                        .param("type", "READER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminOnAdministrativeEndpoints() throws Exception {
        mockMvc.perform(post("/locais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLocationPayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/pessoas/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolePayload()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/eventos/com-escala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventWithScalePayload()))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/eventos/1/escala")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validScalePayload()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/escalas")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-31")
                        .param("type", "READER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isOk());
    }

    private String validLocationPayload() {
        return """
                {
                  "churchName": "Igreja Teste Segurança",
                  "address": "Rua Teste, 100"
                }
                """;
    }

    private String rolePayload() {
        return """
                {
                  "role": "ROLE_OPERATOR"
                }
                """;
    }

    private String validScalePayload() {
        return """
                {
                  "locationId": 1,
                  "priestId": 13,
                  "readerIds": [4],
                  "commentatorIds": [1],
                  "ministerOfTheWordIds": [7],
                  "eucharisticMinisterIds": [10]
                }
                """;
    }

    private String validEventWithScalePayload() {
        return """
                {
                  "nameMassOrEvent": "Missa Teste Segurança",
                  "eventDate": "2027-08-15",
                  "eventTime": "19:30:00",
                  "massOrCelebration": true,
                  "locationId": 1,
                  "priestId": 13,
                  "readerIds": [4],
                  "commentatorIds": [1],
                  "ministerOfTheWordIds": [7],
                  "eucharisticMinisterIds": [10]
                }
                """;
    }
}
