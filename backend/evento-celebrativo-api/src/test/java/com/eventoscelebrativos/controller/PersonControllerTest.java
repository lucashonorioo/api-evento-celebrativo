package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonController.class)
@Import(PersonControllerTest.MethodSecurityTestConfig.class)
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonService personService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPeopleWhenUserIsAdmin() throws Exception {
        when(personService.findPeople("Alice", null, "reader", "ROLE_ADMIN", 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(adminResponse(1L, "Alice", List.of("ROLE_ADMIN", "ROLE_OPERATOR"))),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas")
                        .param("name", "Alice")
                        .param("personType", "reader")
                        .param("role", "ROLE_ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alice"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("34999999991"))
                .andExpect(jsonPath("$.content[0].personType").value("reader"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.content[0].roles[1]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldFindPersonByIdWhenUserIsAdmin() throws Exception {
        when(personService.findPersonById(1L))
                .thenReturn(adminResponse(1L, "Alice", List.of("ROLE_OPERATOR")));

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundWhenFindingMissingPersonById() throws Exception {
        when(personService.findPersonById(99L))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(get("/pessoas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenListFilterIsInvalid() throws Exception {
        when(personService.findPeople(null, null, "invalid", null, 0, 10))
                .thenThrow(new BadRequestException("Tipo de pessoa invalido"));

        mockMvc.perform(get("/pessoas")
                        .param("personType", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateRoleToAdminWhenUserIsAdmin() throws Exception {
        when(personService.updatePersonRole(eq(1L), any()))
                .thenReturn(roleResponse("ROLE_ADMIN"));

        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateRoleToOperatorWhenUserIsAdmin() throws Exception {
        when(personService.updatePersonRole(eq(1L), any()))
                .thenReturn(roleResponse("ROLE_OPERATOR"));

        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundWhenPersonDoesNotExistOnRoleUpdate() throws Exception {
        when(personService.updatePersonRole(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(put("/pessoas/99/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_ADMIN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenRoleIsInvalid() throws Exception {
        when(personService.updatePersonRole(eq(1L), any()))
                .thenThrow(new BadRequestException("Perfil de acesso invalido"));

        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenSelfDemotionIsBlocked() throws Exception {
        when(personService.updatePersonRole(eq(1L), any()))
                .thenThrow(new ConflictException("Voce nao pode remover o seu proprio perfil administrativo."));

        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_OPERATOR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_CONFLICT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenLastAdministratorDemotionIsBlocked() throws Exception {
        when(personService.updatePersonRole(eq(1L), any()))
                .thenThrow(new ConflictException("O ultimo administrador do sistema nao pode ter seu perfil alterado."));

        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_OPERATOR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_CONFLICT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenPayloadIsInvalid() throws Exception {
        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorListsPeople() throws Exception {
        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorFindsPersonById() throws Exception {
        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorUpdatesRole() throws Exception {
        mockMvc.perform(put("/pessoas/1/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ROLE_ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldFindPersonMinistriesWhenUserIsAdmin() throws Exception {
        when(personService.findPersonMinistries(1L))
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of(MinistryType.READER, MinistryType.COMMENTATOR)));

        mockMvc.perform(get("/pessoas/1/ministries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ministries[0]").value("READER"))
                .andExpect(jsonPath("$.ministries[1]").value("COMMENTATOR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundWhenFindingMinistriesOfMissingPerson() throws Exception {
        when(personService.findPersonMinistries(99L))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(get("/pessoas/99/ministries"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorFindsPersonMinistries() throws Exception {
        mockMvc.perform(get("/pessoas/1/ministries"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdatePersonMinistriesWhenUserIsAdmin() throws Exception {
        when(personService.updatePersonMinistries(eq(1L), any()))
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of(MinistryType.READER)));

        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": ["READER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ministries[0]").value("READER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowEmptyMinistriesListToRemoveAll() throws Exception {
        when(personService.updatePersonMinistries(eq(1L), any()))
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of()));

        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ministries").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenMinistriesFieldIsMissing() throws Exception {
        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenMinistryValueIsInvalid() throws Exception {
        when(personService.updatePersonMinistries(eq(1L), any()))
                .thenThrow(new BadRequestException("Tipo de ministerio invalido: BISHOP"));

        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": ["BISHOP"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnUnprocessableEntityWhenMinistryIsDuplicatedInRequest() throws Exception {
        when(personService.updatePersonMinistries(eq(1L), any()))
                .thenThrow(new BusinessException("Ministerio duplicado no request: READER"));

        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": ["READER", "READER"]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictWhenRemovingMinistryUsedByEventAssignment() throws Exception {
        when(personService.updatePersonMinistries(eq(1L), any()))
                .thenThrow(new DatabaseException(
                        "Não é possível remover os seguintes ministérios, pois possuem vínculos com escalas: READER"));

        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundWhenUpdatingMinistriesOfMissingPerson() throws Exception {
        when(personService.updatePersonMinistries(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(put("/pessoas/99/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": ["READER"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorUpdatesMinistries() throws Exception {
        mockMvc.perform(put("/pessoas/1/ministries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ministries": ["READER"]
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(personService);
    }

    private PersonAdminResponseDTO adminResponse(Long id, String name, List<String> roles) {
        return new PersonAdminResponseDTO(
                id,
                name,
                "3499999999" + id,
                "reader",
                roles
        );
    }

    private PersonRoleUpdateResponseDTO roleResponse(String role) {
        return new PersonRoleUpdateResponseDTO(
                1L,
                "Reader",
                "34999999991",
                "reader",
                List.of(role)
        );
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
