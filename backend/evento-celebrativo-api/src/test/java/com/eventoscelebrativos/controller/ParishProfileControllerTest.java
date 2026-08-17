package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ParishProfileNotConfiguredException;
import com.eventoscelebrativos.service.ParishAuthorizationService;
import com.eventoscelebrativos.service.ParishProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParishProfileController.class)
@Import(ParishProfileControllerTest.MethodSecurityTestConfig.class)
class ParishProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParishProfileService parishProfileService;

    @MockitoBean(name = "parishAuthorizationService")
    private ParishAuthorizationService parishAuthorizationService;

    // Autorização real de "endpoint público sem autenticação" é coberta por EndpointSecurityTest
    // (contexto Spring completo, com o SecurityFilterChain real). Este slice test (@WebMvcTest) não
    // carrega o ResourceServerConfig, então usa um usuário autenticado apenas para validar o
    // mapeamento de conteúdo da resposta (campos expostos, 404 semântico).
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnOnlyPublicFieldsWhenConfigured() throws Exception {
        when(parishProfileService.findPublicProfile()).thenReturn(new ParishProfileResponseDTO(
                "Paróquia São José", "Diocese de Uberlândia", "34999990000",
                "secretaria@paroquia.org.br", "Praça Central, 1", "Segunda a sexta, das 8h às 17h"));

        mockMvc.perform(get("/paroquia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia São José"))
                .andExpect(jsonPath("$.diocese").value("Diocese de Uberlândia"))
                .andExpect(jsonPath("$.institutionalPhone").value("34999990000"))
                .andExpect(jsonPath("$.institutionalEmail").value("secretaria@paroquia.org.br"))
                .andExpect(jsonPath("$.officeAddress").value("Praça Central, 1"))
                .andExpect(jsonPath("$.officeHours").value("Segunda a sexta, das 8h às 17h"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.configured").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundWhenNotConfigured() throws Exception {
        when(parishProfileService.findPublicProfile()).thenThrow(new ParishProfileNotConfiguredException());

        mockMvc.perform(get("/paroquia"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PARISH_PROFILE_NOT_CONFIGURED"));
    }

    @Test
    void shouldRejectUpdateWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/paroquia")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parishProfileService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectUpdateForOperator() throws Exception {
        mockMvc.perform(put("/paroquia")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(parishProfileService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateForAdmin() throws Exception {
        when(parishProfileService.update(any())).thenReturn(new ParishProfileResponseDTO(
                "Paróquia São José", "Diocese de Uberlândia", null, null, null, null));

        mockMvc.perform(put("/paroquia")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia São José"))
                .andExpect(jsonPath("$.diocese").value("Diocese de Uberlândia"));

        verify(parishProfileService).update(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        mockMvc.perform(put("/paroquia")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "diocese": "Diocese de Uberlândia"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(parishProfileService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenDiocesIsMissing() throws Exception {
        mockMvc.perform(put("/paroquia")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia São José"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(parishProfileService);
    }

    // --- PUT /paroquia/contato ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateContactWhenAuthorizationServiceGrantsAccess() throws Exception {
        when(parishAuthorizationService.canPerformSecretaryOperations()).thenReturn(true);
        when(parishProfileService.updateContact(any())).thenReturn(new ParishProfileResponseDTO(
                "Paróquia São José", "Diocese de Uberlândia", "34999990000",
                "secretaria@paroquia.org.br", "Praça Central, 1", "Segunda a sexta, das 8h às 17h"));

        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContactPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionalPhone").value("34999990000"))
                .andExpect(jsonPath("$.name").value("Paróquia São José"));

        verify(parishProfileService).updateContact(any());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectContactUpdateWhenAuthorizationServiceDeniesAccess() throws Exception {
        when(parishAuthorizationService.canPerformSecretaryOperations()).thenReturn(false);

        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContactPayload()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(parishProfileService);
    }

    @Test
    void shouldRejectContactUpdateWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContactPayload()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parishProfileService);
        verifyNoInteractions(parishAuthorizationService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectContactUpdateWithUnknownFieldName() throws Exception {
        when(parishAuthorizationService.canPerformSecretaryOperations()).thenReturn(true);

        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institutionalPhone": "34999990000",
                                  "name": "Paróquia adulterada"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(parishProfileService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectContactUpdateWithUnknownDioceseField() throws Exception {
        when(parishAuthorizationService.canPerformSecretaryOperations()).thenReturn(true);

        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diocese": "Diocese adulterada"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(parishProfileService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotConfiguredWhenUpdatingContactBeforeInitialConfiguration() throws Exception {
        when(parishAuthorizationService.canPerformSecretaryOperations()).thenReturn(true);
        when(parishProfileService.updateContact(any())).thenThrow(new ParishProfileNotConfiguredException());

        mockMvc.perform(put("/paroquia/contato")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validContactPayload()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PARISH_PROFILE_NOT_CONFIGURED"));
    }

    private String validContactPayload() {
        return """
                {
                  "institutionalPhone": "34999990000",
                  "institutionalEmail": "secretaria@paroquia.org.br",
                  "officeAddress": "Praça Central, 1",
                  "officeHours": "Segunda a sexta, das 8h às 17h"
                }
                """;
    }

    private String validUpdatePayload() {
        return """
                {
                  "name": "Paróquia São José",
                  "diocese": "Diocese de Uberlândia",
                  "institutionalPhone": "34999990000",
                  "institutionalEmail": "secretaria@paroquia.org.br",
                  "officeAddress": "Praça Central, 1",
                  "officeHours": "Segunda a sexta, das 8h às 17h"
                }
                """;
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
