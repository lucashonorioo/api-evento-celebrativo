package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinistryRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryResponseDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.MinistryAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MinistryController.class)
@WithMockUser(roles = "ADMIN")
@Import(MinistryControllerTest.MethodSecurityConfig.class)
class MinistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MinistryAdministrationService ministryAdministrationService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Test
    void shouldListMinistries() throws Exception {
        when(ministryAdministrationService.findAll()).thenReturn(List.of(
                new MinistryResponseDTO(10L, "Acólitos", true),
                new MinistryResponseDTO(2L, "Leitores", false)
        ));

        mockMvc.perform(get("/ministerios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Acólitos"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].normalizedName").doesNotExist())
                .andExpect(jsonPath("$[0].legacyMinistryType").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Leitores"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void shouldFindMinistryById() throws Exception {
        when(ministryAdministrationService.findById(10L))
                .thenReturn(new MinistryResponseDTO(10L, "Acólitos", true));

        mockMvc.perform(get("/ministerios/{ministryId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Acólitos"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnNotFoundWhenMinistryDoesNotExist() throws Exception {
        when(ministryAdministrationService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Ministerio", 99L));

        mockMvc.perform(get("/ministerios/{ministryId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldCreateMinistry() throws Exception {
        when(ministryAdministrationService.create(any(MinistryRequestDTO.class)))
                .thenReturn(new MinistryResponseDTO(10L, "Acólitos", true));

        mockMvc.perform(post("/ministerios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acólitos"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/ministerios/10")))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Acólitos"))
                .andExpect(jsonPath("$.active").value(true));

        verify(ministryAdministrationService).create(any(MinistryRequestDTO.class));
    }

    @Test
    void shouldReturnValidationErrorWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/ministerios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].fieldName").value("name"));

        verifyNoInteractions(ministryAdministrationService);
    }

    @Test
    void shouldRenameMinistry() throws Exception {
        when(ministryAdministrationService.rename(eq(2L), any(MinistryRequestDTO.class)))
                .thenReturn(new MinistryResponseDTO(2L, "Leitores e Salmistas", true));

        mockMvc.perform(put("/ministerios/{ministryId}", 2L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Leitores e Salmistas"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Leitores e Salmistas"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldUpdateStatus() throws Exception {
        when(ministryAdministrationService.updateStatus(eq(10L), any(MinistryStatusUpdateRequestDTO.class)))
                .thenReturn(new MinistryResponseDTO(10L, "Acólitos", false));

        mockMvc.perform(put("/ministerios/{ministryId}/status", 10L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldReturnConflictFromService() throws Exception {
        when(ministryAdministrationService.updateStatus(eq(2L), any(MinistryStatusUpdateRequestDTO.class)))
                .thenThrow(new LifecycleConflictException(
                        "Nao e possivel desativar ministerio com vinculos ativos.",
                        "MINISTRY_HAS_ACTIVE_MEMBERSHIPS"));

        mockMvc.perform(put("/ministerios/{ministryId}/status", 2L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MINISTRY_HAS_ACTIVE_MEMBERSHIPS"));
    }

    @Test
    void shouldNotExposePhysicalDelete() throws Exception {
        mockMvc.perform(delete("/ministerios/{ministryId}", 10L).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectOperatorOnCatalogAdministration() throws Exception {
        mockMvc.perform(get("/ministerios")).andExpect(status().isForbidden());
        mockMvc.perform(get("/ministerios/{ministryId}", 10L)).andExpect(status().isForbidden());
        mockMvc.perform(post("/ministerios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acólitos"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/ministerios/{ministryId}", 10L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acólitos"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/ministerios/{ministryId}/status", 10L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ministryAdministrationService);
    }

    @Test
    @WithAnonymousUser
    void shouldRequireAuthenticationForCatalogAdministration() throws Exception {
        mockMvc.perform(get("/ministerios")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/ministerios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acólitos"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/ministerios/{ministryId}", 10L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Acólitos"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/ministerios/{ministryId}/status", 10L).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ministryAdministrationService);
    }
}
