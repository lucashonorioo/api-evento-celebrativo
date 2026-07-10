package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
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

@WebMvcTest(MinisterOfTheWordController.class)
@WithMockUser(roles = "ADMIN")
class MinisterOfTheWordControllerTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MinisterOfTheWordService ministerOfTheWordService;

    @Test
    void shouldReturnCreatedWhenPostingValidMinisterOfTheWord() throws Exception {
        when(ministerOfTheWordService.createMinisterOfTheWord(any())).thenReturn(response("Minister"));

        mockMvc.perform(post("/ministrosDaPalavra").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/ministrosDaPalavra/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidMinisterOfTheWord() throws Exception {
        mockMvc.perform(post("/ministrosDaPalavra").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(ministerOfTheWordService);
    }

    @Test
    void shouldReturnOkWhenGettingMinisterOfTheWordByExistingId() throws Exception {
        when(ministerOfTheWordService.findMinisterOfTheWordById(1L)).thenReturn(response("Minister"));

        mockMvc.perform(get("/ministrosDaPalavra/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingMinisterOfTheWord() throws Exception {
        when(ministerOfTheWordService.findMinisterOfTheWordById(99L)).thenThrow(new ResourceNotFoundException("Ministro da Palavra", 99L));

        mockMvc.perform(get("/ministrosDaPalavra/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingMinistersOfTheWord() throws Exception {
        when(ministerOfTheWordService.findAllMinistersOfTheWord()).thenReturn(List.of(response("Minister")));

        mockMvc.perform(get("/ministrosDaPalavra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidMinisterOfTheWord() throws Exception {
        when(ministerOfTheWordService.updateMinisterOfTheWord(eq(1L), any())).thenReturn(response("Minister Updated"));

        mockMvc.perform(put("/ministrosDaPalavra/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Minister Updated"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingMinisterOfTheWord() throws Exception {
        when(ministerOfTheWordService.updateMinisterOfTheWord(eq(99L), any())).thenThrow(new ResourceNotFoundException("Ministro da Palavra", 99L));

        mockMvc.perform(put("/ministrosDaPalavra/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Minister")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingMinisterOfTheWord() throws Exception {
        mockMvc.perform(delete("/ministrosDaPalavra/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(ministerOfTheWordService).deleteMinisterOfTheWord(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingMinisterOfTheWord() throws Exception {
        doThrow(new ResourceNotFoundException("Ministro da Palavra", 99L)).when(ministerOfTheWordService).deleteMinisterOfTheWord(99L);

        mockMvc.perform(delete("/ministrosDaPalavra/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictWhenDeletingReferencedMinisterOfTheWord() throws Exception {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(ministerOfTheWordService).deleteMinisterOfTheWord(1L);

        mockMvc.perform(delete("/ministrosDaPalavra/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));
    }

    private MinisterOfTheWordResponseDTO response(String name) {
        return new MinisterOfTheWordResponseDTO(1L, name, "34999999994", BIRTHDAY);
    }

    private String validPayload(String name) {
        return personPayload(name, "34999999994");
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
