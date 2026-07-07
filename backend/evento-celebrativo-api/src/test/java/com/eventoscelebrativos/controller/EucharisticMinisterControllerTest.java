package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.EucharisticMinisterService;
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

@WebMvcTest(EucharisticMinisterController.class)
@WithMockUser(roles = "ADMIN")
class EucharisticMinisterControllerTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EucharisticMinisterService eucharisticMinisterService;

    @Test
    void shouldReturnCreatedWhenPostingValidEucharisticMinister() throws Exception {
        when(eucharisticMinisterService.createEucharisticMinister(any())).thenReturn(response("Minister"));

        mockMvc.perform(post("/ministrosDeEucaristia").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/ministrosDeEucaristia/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidEucharisticMinister() throws Exception {
        mockMvc.perform(post("/ministrosDeEucaristia").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(eucharisticMinisterService);
    }

    @Test
    void shouldReturnOkWhenGettingEucharisticMinisterByExistingId() throws Exception {
        when(eucharisticMinisterService.findEucharisticMinistersById(1L)).thenReturn(response("Minister"));

        mockMvc.perform(get("/ministrosDeEucaristia/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingEucharisticMinister() throws Exception {
        when(eucharisticMinisterService.findEucharisticMinistersById(99L))
                .thenThrow(new ResourceNotFoundException("Ministro de Eucaristia", 99L));

        mockMvc.perform(get("/ministrosDeEucaristia/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingEucharisticMinisters() throws Exception {
        when(eucharisticMinisterService.findAllEucharisticMinisters()).thenReturn(List.of(response("Minister")));

        mockMvc.perform(get("/ministrosDeEucaristia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidEucharisticMinister() throws Exception {
        when(eucharisticMinisterService.updateEucharisticMinisters(eq(1L), any())).thenReturn(response("Minister Updated"));

        mockMvc.perform(put("/ministrosDeEucaristia/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Minister Updated"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingEucharisticMinister() throws Exception {
        when(eucharisticMinisterService.updateEucharisticMinisters(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Ministro de Eucaristia", 99L));

        mockMvc.perform(put("/ministrosDeEucaristia/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingEucharisticMinister() throws Exception {
        mockMvc.perform(delete("/ministrosDeEucaristia/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(eucharisticMinisterService).deleteEucharisticMinisterById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingEucharisticMinister() throws Exception {
        doThrow(new ResourceNotFoundException("Ministro de Eucaristia", 99L))
                .when(eucharisticMinisterService).deleteEucharisticMinisterById(99L);

        mockMvc.perform(delete("/ministrosDeEucaristia/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private EucharisticMinisterResponseDTO response(String name) {
        return new EucharisticMinisterResponseDTO(1L, name, "34999999993", BIRTHDAY);
    }

    private String validPayload(String name) {
        return personPayload(name, "34999999993");
    }

    private String invalidPayload() {
        return personPayload("", "123");
    }

    private String personPayload(String name, String phoneNumber) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "1990-01-10",
                  "password": "123456"
                }
                """.formatted(name, phoneNumber);
    }
}
