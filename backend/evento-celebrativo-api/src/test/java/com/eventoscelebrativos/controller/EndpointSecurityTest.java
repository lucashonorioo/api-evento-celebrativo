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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/pessoas/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-31"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/1/ministries"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/pessoas/1/ministries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ministriesPayload()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/me/indisponibilidades")
                        .param("startAt", "2026-08-01T00:00:00")
                        .param("endAt", "2026-08-31T00:00:00"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/pessoas/me/indisponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unavailabilityPayload()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
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

        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/pessoas/me/escalas/1/participacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload()))
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

        mockMvc.perform(get("/pessoas/1/ministries"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/ministries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ministriesPayload()))
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

        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "34962165544", roles = "OPERATOR")
    void shouldAllowOperatorOnOwnProfileEndpointsOnly() throws Exception {
        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.phoneNumber").value("34962165544"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(put("/pessoas/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("34962165544"));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2000-01-01")
                        .param("endDate", "2030-12-31"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me/indisponibilidades")
                        .param("startAt", "2000-01-01T00:00:00")
                        .param("endAt", "2030-12-31T00:00:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rolePayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/ministries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ministriesPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "34999887766", roles = "ADMIN")
    void shouldAllowAdminOnAdministrativeEndpoints() throws Exception {
        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.phoneNumber").value("34999887766"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));

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

        mockMvc.perform(get("/pessoas/1/ministries"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2000-01-01")
                        .param("endDate", "2030-12-31"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me/indisponibilidades")
                        .param("startAt", "2000-01-01T00:00:00")
                        .param("endAt", "2030-12-31T00:00:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/indisponibilidades")
                        .param("startAt", "2026-08-10T00:00:00")
                        .param("endAt", "2026-08-11T00:00:00"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/pessoas/1/ministries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ministriesPayload()))
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

        mockMvc.perform(get("/eventos/1/escala/participacoes"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotExposeRetiredOperationalAuditEndpointToAnyone() throws Exception {
        mockMvc.perform(get("/admin/event-assignments/consistency"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundForRetiredOperationalAuditEndpointWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/event-assignments/consistency"))
                .andExpect(status().isNotFound());
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

    private String profilePayload() {
        return """
                {
                  "name": "Nome Atualizado",
                  "birthdayDate": "1990-01-01"
                }
                """;
    }

    private String ministriesPayload() {
        return """
                {
                  "ministries": ["COMMENTATOR"]
                }
                """;
    }

    private String unavailabilityPayload() {
        return """
                {
                  "startAt": "2026-08-10T00:00:00",
                  "endAt": "2026-08-12T00:00:00"
                }
                """;
    }

    private String participationPayload() {
        return """
                {
                  "status": "CONFIRMED"
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
                  "startAt": "2027-08-15T19:30:00",
                  "endAt": "2027-08-15T20:30:00",
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
