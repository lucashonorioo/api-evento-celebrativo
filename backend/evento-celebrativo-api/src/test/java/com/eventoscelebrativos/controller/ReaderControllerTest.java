package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.ReaderService;
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

@WebMvcTest(ReaderController.class)
@WithMockUser(roles = "ADMIN")
class ReaderControllerTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReaderService readerService;

    @Test
    void shouldReturnCreatedWhenPostingValidReader() throws Exception {
        when(readerService.createReader(any())).thenReturn(response("Reader"));

        mockMvc.perform(post("/leitores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Reader")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/leitores/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Reader"));

        verify(readerService).createReader(any());
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidReader() throws Exception {
        mockMvc.perform(post("/leitores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(readerService);
    }

    @Test
    void shouldReturnOkWhenGettingReaderByExistingId() throws Exception {
        when(readerService.findReaderById(1L)).thenReturn(response("Reader"));

        mockMvc.perform(get("/leitores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.phoneNumber").value("34999999991"));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingReader() throws Exception {
        when(readerService.findReaderById(99L)).thenThrow(new ResourceNotFoundException("Leitor", 99L));

        mockMvc.perform(get("/leitores/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingReaders() throws Exception {
        when(readerService.findAllReaders()).thenReturn(List.of(response("Reader")));

        mockMvc.perform(get("/leitores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidReader() throws Exception {
        when(readerService.updateReader(eq(1L), any())).thenReturn(response("Reader Updated"));

        mockMvc.perform(put("/leitores/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Reader Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reader Updated"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingReader() throws Exception {
        when(readerService.updateReader(eq(99L), any())).thenThrow(new ResourceNotFoundException("Leitor", 99L));

        mockMvc.perform(put("/leitores/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload("Reader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingReader() throws Exception {
        mockMvc.perform(delete("/leitores/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(readerService).deleteReaderById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingReader() throws Exception {
        doThrow(new ResourceNotFoundException("Leitor", 99L)).when(readerService).deleteReaderById(99L);

        mockMvc.perform(delete("/leitores/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictWhenDeletingReferencedReader() throws Exception {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(readerService).deleteReaderById(1L);

        mockMvc.perform(delete("/leitores/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));
    }

    private ReaderResponseDTO response(String name) {
        return new ReaderResponseDTO(1L, name, "34999999991", BIRTHDAY);
    }

    private String validPayload(String name) {
        return personPayload(name, "34999999991");
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
