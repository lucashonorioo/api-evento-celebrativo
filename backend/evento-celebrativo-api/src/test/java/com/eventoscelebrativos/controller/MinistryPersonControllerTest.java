package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.MinistryPersonInactiveException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.service.MinistryAuthorizationService;
import com.eventoscelebrativos.service.MinistryPersonManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(MinistryPersonController.class)
@WithMockUser(roles = "ADMIN")
@Import(MinistryPersonControllerTest.MethodSecurityConfig.class)
class MinistryPersonControllerTest {

    private static final long READER_MINISTRY_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MinistryPersonManagementService ministryPersonManagementService;

    @MockitoBean(name = "ministryAuthorizationService")
    private MinistryAuthorizationService ministryAuthorizationService;

    @BeforeEach
    void grantMinistryManagementByDefault() {
        when(ministryAuthorizationService.canManageMinistry(any())).thenReturn(true);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Test
    void shouldReturnOkWhenListingPeople() throws Exception {
        Page<MinistryPersonResponseDTO> page = new PageImpl<>(List.of(response(1L)), PageRequest.of(0, 10), 1);
        when(ministryPersonManagementService.findPeople(eq(READER_MINISTRY_ID), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void shouldReturnBadRequestForNonNumericMinistryIdInPath() throws Exception {
        mockMvc.perform(get("/ministerios/UNKNOWN_TYPE/pessoas"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ministryPersonManagementService);
    }

    @Test
    void shouldReturnBadRequestForNegativeMinistryIdInPath() throws Exception {
        when(ministryAuthorizationService.canManageMinistry(-1L))
                .thenThrow(new BadRequestException("O Id do ministério deve ser positivo e não nulo"));

        mockMvc.perform(get("/ministerios/{ministryId}/pessoas", -1L))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ministryPersonManagementService);
    }

    @Test
    void shouldReturnForbiddenWhenAuthorizationDenies() throws Exception {
        when(ministryAuthorizationService.canManageMinistry(READER_MINISTRY_ID)).thenReturn(false);

        mockMvc.perform(get("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID)).andExpect(status().isForbidden());
        mockMvc.perform(get("/ministerios/{ministryId}/pessoas/1", READER_MINISTRY_ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1/vinculo", READER_MINISTRY_ID).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/ministerios/{ministryId}/pessoas/1/vinculo", READER_MINISTRY_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ministryPersonManagementService);
    }

    @Test
    void shouldReturnNotFoundWhenPersonOutsideScope() throws Exception {
        when(ministryPersonManagementService.findPersonById(READER_MINISTRY_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(get("/ministerios/{ministryId}/pessoas/99", READER_MINISTRY_ID)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnCreatedWithLocationWhenCreatingPerson() throws Exception {
        when(ministryPersonManagementService.create(eq(READER_MINISTRY_ID), any())).thenReturn(response(5L));

        mockMvc.perform(post("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/ministerios/" + READER_MINISTRY_ID + "/pessoas/5")));
    }

    @Test
    void shouldReturnBadRequestWhenCreateServiceRejectsForbiddenField() throws Exception {
        // rejectForbiddenFields() e chamado dentro do service (mockado aqui); este teste cobre o
        // mapeamento HTTP do BadRequestException resultante. A rejeicao real do DTO contra JSON com
        // password/createAccess/etc (mesmo null/false) e provada com o service real em
        // MinistryPersonManagementIntegrationTest.createRejectsAccountAndLifecycleFieldsEvenWhenFalseOrNull.
        when(ministryPersonManagementService.create(eq(READER_MINISTRY_ID), any())).thenThrow(
                new BadRequestException("Campos não permitidos na criação escopada de pessoa: password",
                        "MINISTRY_PERSON_CREATE_FIELDS_INVALID"));

        mockMvc.perform(post("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "phoneNumber": "34999999999", "birthdayDate": "1990-01-01", "password": "abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MINISTRY_PERSON_CREATE_FIELDS_INVALID"));
    }

    @Test
    void shouldReturnBadRequestWhenUpdateServiceRejectsPhoneNumberField() throws Exception {
        when(ministryPersonManagementService.update(eq(READER_MINISTRY_ID), eq(1L), any())).thenThrow(
                new BadRequestException("Campos não permitidos na atualização escopada de pessoa: phoneNumber",
                        "MINISTRY_PERSON_UPDATE_FIELDS_INVALID"));

        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "birthdayDate": "1990-01-01", "phoneNumber": "34999999999"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MINISTRY_PERSON_UPDATE_FIELDS_INVALID"));
    }

    @Test
    void shouldReturnOkWhenUpdatingPerson() throws Exception {
        when(ministryPersonManagementService.update(eq(READER_MINISTRY_ID), eq(1L), any())).thenReturn(response(1L));

        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1", READER_MINISTRY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnOkWhenAddingOrReactivatingVinculo() throws Exception {
        when(ministryPersonManagementService.addOrReactivateMinistry(READER_MINISTRY_ID, 1L)).thenReturn(response(1L));

        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1/vinculo", READER_MINISTRY_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturnConflictWhenAddingVinculoToInactivePerson() throws Exception {
        when(ministryPersonManagementService.addOrReactivateMinistry(READER_MINISTRY_ID, 1L))
                .thenThrow(new MinistryPersonInactiveException());

        mockMvc.perform(put("/ministerios/{ministryId}/pessoas/1/vinculo", READER_MINISTRY_ID).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MINISTRY_PERSON_INACTIVE"));
    }

    @Test
    void shouldReturnNoContentWhenRemovingVinculo() throws Exception {
        mockMvc.perform(delete("/ministerios/{ministryId}/pessoas/1/vinculo", READER_MINISTRY_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnBadRequestWhenPageSizeAboveMaximum() throws Exception {
        when(ministryPersonManagementService.findPeople(READER_MINISTRY_ID, 0, 500))
                .thenThrow(new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/ministerios/{ministryId}/pessoas", READER_MINISTRY_ID).param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    private MinistryPersonResponseDTO response(Long id) {
        return new MinistryPersonResponseDTO(id, "Pessoa " + id, "34999999999", LocalDate.of(1990, 1, 1));
    }

    private String createPayload() {
        return """
                {"name": "Nova Pessoa", "phoneNumber": "34999999999", "birthdayDate": "1990-01-01"}
                """;
    }

    private String updatePayload() {
        return """
                {"name": "Nome Atualizado", "birthdayDate": "1990-01-01"}
                """;
    }
}
