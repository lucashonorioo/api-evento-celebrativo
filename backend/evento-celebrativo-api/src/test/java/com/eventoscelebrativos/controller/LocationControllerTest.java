package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.LocationResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
@WithMockUser(roles = "ADMIN")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationService locationService;

    @Test
    void shouldReturnCreatedWhenPostingValidLocation() throws Exception {
        when(locationService.createLocation(any())).thenReturn(response("Igreja Matriz"));

        mockMvc.perform(post("/locais").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Igreja Matriz")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/locais/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidLocation() throws Exception {
        mockMvc.perform(post("/locais").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(locationService);
    }

    @Test
    void shouldReturnOkWhenGettingLocationByExistingId() throws Exception {
        when(locationService.findLocationById(1L)).thenReturn(response("Igreja Matriz"));

        mockMvc.perform(get("/locais/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingLocation() throws Exception {
        when(locationService.findLocationById(99L)).thenThrow(new ResourceNotFoundException("Local", 99L));

        mockMvc.perform(get("/locais/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingLocations() throws Exception {
        when(locationService.findAllLocations()).thenReturn(List.of(response("Igreja Matriz")));

        mockMvc.perform(get("/locais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidLocation() throws Exception {
        when(locationService.updateLocation(eq(1L), any())).thenReturn(response("Igreja Atualizada"));

        mockMvc.perform(put("/locais/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Igreja Atualizada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.churchName").value("Igreja Atualizada"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingLocation() throws Exception {
        when(locationService.updateLocation(eq(99L), any())).thenThrow(new ResourceNotFoundException("Local", 99L));

        mockMvc.perform(put("/locais/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Igreja Matriz")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingLocation() throws Exception {
        mockMvc.perform(delete("/locais/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(locationService).deleteLocationById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingLocation() throws Exception {
        doThrow(new ResourceNotFoundException("Local", 99L)).when(locationService).deleteLocationById(99L);

        mockMvc.perform(delete("/locais/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictWhenDeletingReferencedLocation() throws Exception {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(locationService).deleteLocationById(1L);

        mockMvc.perform(delete("/locais/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));
    }

    private LocationResponseDTO response(String churchName) {
        return new LocationResponseDTO(1L, churchName, "Rua Central, 100");
    }

    private String validPayload(String churchName) {
        return """
                {
                  "churchName": "%s",
                  "address": "Rua Central, 100"
                }
                """.formatted(churchName);
    }

    private String invalidPayload() {
        return """
                {
                  "churchName": "",
                  "address": ""
                }
                """;
    }
}
