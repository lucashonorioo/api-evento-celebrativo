package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.CommentatorService;
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

@WebMvcTest(CommentatorController.class)
@WithMockUser(roles = "ADMIN")
class CommentatorControllerTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentatorService commentatorService;

    @Test
    void shouldReturnCreatedWhenPostingValidCommentator() throws Exception {
        when(commentatorService.createCommentator(any())).thenReturn(response("Commentator"));

        mockMvc.perform(post("/comentaristas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Commentator")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/comentaristas/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPostingInvalidCommentator() throws Exception {
        mockMvc.perform(post("/comentaristas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidPayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(commentatorService);
    }

    @Test
    void shouldReturnOkWhenGettingCommentatorByExistingId() throws Exception {
        when(commentatorService.findCommentatorById(1L)).thenReturn(response("Commentator"));

        mockMvc.perform(get("/comentaristas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenGettingMissingCommentator() throws Exception {
        when(commentatorService.findCommentatorById(99L)).thenThrow(new ResourceNotFoundException("Comentarista", 99L));

        mockMvc.perform(get("/comentaristas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnOkWhenListingCommentators() throws Exception {
        when(commentatorService.findAllCommentators()).thenReturn(List.of(response("Commentator")));

        mockMvc.perform(get("/comentaristas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void shouldReturnOkWhenPuttingValidCommentator() throws Exception {
        when(commentatorService.updateCommentator(eq(1L), any())).thenReturn(response("Commentator Updated"));

        mockMvc.perform(put("/comentaristas/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Commentator Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Commentator Updated"));
    }

    @Test
    void shouldReturnNotFoundWhenPuttingMissingCommentator() throws Exception {
        when(commentatorService.updateCommentator(eq(99L), any())).thenThrow(new ResourceNotFoundException("Comentarista", 99L));

        mockMvc.perform(put("/comentaristas/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validPayload("Commentator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnNoContentWhenDeletingCommentator() throws Exception {
        mockMvc.perform(delete("/comentaristas/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentatorService).deleteCommentatorById(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingCommentator() throws Exception {
        doThrow(new ResourceNotFoundException("Comentarista", 99L)).when(commentatorService).deleteCommentatorById(99L);

        mockMvc.perform(delete("/comentaristas/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private CommentatorResponseDTO response(String name) {
        return new CommentatorResponseDTO(1L, name, "34999999992", BIRTHDAY);
    }

    private String validPayload(String name) {
        return personPayload(name, "34999999992");
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
