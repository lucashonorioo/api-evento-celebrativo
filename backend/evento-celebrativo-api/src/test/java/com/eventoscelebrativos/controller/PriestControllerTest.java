package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.PriestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PriestController.class)
@WithMockUser(roles = "ADMIN")
class PriestControllerTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1980, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriestService priestService;

    @Test
    void shouldReturnCreatedWhenPostingValidPriest() throws Exception {
        when(priestService.createPriest(any())).thenReturn(response("Priest"));

        mockMvc.perform(post("/padres").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Priest")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/padres/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidPriest() throws Exception {
        mockMvc.perform(post("/padres").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(priestService);
    }

    @Test
    void shouldReturnOkWhenGettingPriestByExistingId() throws Exception {
        when(priestService.findPriestById(1L)).thenReturn(response("Priest"));

        mockMvc.perform(get("/padres/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingPriest() throws Exception {
        when(priestService.findPriestById(99L)).thenThrow(new ResourceNotFoundException("Padre", 99L));

        mockMvc.perform(get("/padres/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingPriests() throws Exception {
        when(priestService.findAllPriests()).thenReturn(List.of(response("Priest")));

        mockMvc.perform(get("/padres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidPriest() throws Exception {
        when(priestService.updatePriest(eq(1L), any())).thenReturn(response("Priest Updated"));

        mockMvc.perform(put("/padres/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Priest Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priest Updated"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingPriest() throws Exception {
        when(priestService.updatePriest(eq(99L), any())).thenThrow(new ResourceNotFoundException("Padre", 99L));

        mockMvc.perform(put("/padres/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Priest")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingPriest() throws Exception {
        mockMvc.perform(delete("/padres/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(priestService).deletePriestById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingPriest() throws Exception {
        doThrow(new ResourceNotFoundException("Padre", 99L)).when(priestService).deletePriestById(99L);

        mockMvc.perform(delete("/padres/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private PriestResponseDTO response(String name) {
        return new PriestResponseDTO(1L, name, "34999999995", BIRTHDAY);
    }

    private String validPayload(String name) {
        return personPayload(name, "34999999995");
    }

    private String invalidPayload() {
        return personPayload("", "123");
    }

    private String personPayload(String name, String phoneNumber) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "1980-01-10",
                  "password": "123456"
                }
                """.formatted(name, phoneNumber);
    }
}
