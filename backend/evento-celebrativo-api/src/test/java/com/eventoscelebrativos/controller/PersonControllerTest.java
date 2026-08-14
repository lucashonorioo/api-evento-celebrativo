package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.dto.response.UserAccountLifecycleResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.security.WithMockAuthenticatedUser;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.MinistryCoordinationService;
import com.eventoscelebrativos.service.ParishStaffAssignmentService;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.service.UserAccountLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonController.class)
@Import({PersonControllerTest.MethodSecurityTestConfig.class, AuthenticatedUserResolver.class})
class PersonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonService personService;

    @MockitoBean
    private EventParticipationResponseService eventParticipationResponseService;

    @MockitoBean
    private UserAccountLifecycleService userAccountLifecycleService;

    @MockitoBean
    private ParishStaffAssignmentService parishStaffAssignmentService;

    @MockitoBean
    private MinistryCoordinationService ministryCoordinationService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPeopleWhenUserIsAdmin() throws Exception {
        when(personService.findPeople("Alice", null, "reader", "ROLE_ADMIN", null, null, null, 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(adminResponse(1L, "Alice", List.of(MinistryType.READER), List.of("ROLE_ADMIN", "ROLE_OPERATOR"))),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas")
                        .param("name", "Alice")
                        .param("ministry", "reader")
                        .param("role", "ROLE_ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alice"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("34999999991"))
                .andExpect(jsonPath("$.content[0].ministries[0]").value("READER"))
                .andExpect(jsonPath("$.content[0].ministries.length()").value(1))
                .andExpect(jsonPath("$.content[0].roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.content[0].roles[1]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].personType").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPeopleWithNewAdministrativeFilters() throws Exception {
        when(personService.findPeople(null, null, null, "ROLE_ADMIN", true, true, false, 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(adminResponse(1L, "Alice", List.of(), List.of("ROLE_ADMIN"))),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas")
                        .param("role", "ROLE_ADMIN")
                        .param("personActive", "true")
                        .param("accountExists", "true")
                        .param("accountEnabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].accountExists").value(true))
                .andExpect(jsonPath("$.content[0].accountEnabled").value(true))
                .andExpect(jsonPath("$.content[0].username").value("34999999991"))
                .andExpect(jsonPath("$.content[0].personActive").value(true))
                .andExpect(jsonPath("$.content[0].birthdayDate").value("1990-01-10"));

        verify(personService).findPeople(null, null, null, "ROLE_ADMIN", true, true, false, 0, 10);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenAccountExistsFalseCombinedWithAccountEnabled() throws Exception {
        when(personService.findPeople(null, null, null, null, null, false, true, 0, 10))
                .thenThrow(new BadRequestException(
                        "accountExists=false não pode ser combinado com accountEnabled ou role",
                        "PERSON_ADMIN_FILTERS_INVALID"));

        mockMvc.perform(get("/pessoas")
                        .param("accountExists", "false")
                        .param("accountEnabled", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PERSON_ADMIN_FILTERS_INVALID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldFindPersonByIdWithAccountFieldsForPersonWithoutAccount() throws Exception {
        PersonAdminResponseDTO responseDTO = new PersonAdminResponseDTO(
                2L, "No Account", "34999999992", LocalDate.of(1991, 2, 11), true,
                List.of(), false, null, null, List.of());
        when(personService.findPersonById(2L)).thenReturn(responseDTO);

        mockMvc.perform(get("/pessoas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountExists").value(false))
                .andExpect(jsonPath("$.accountEnabled").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPersonWithNoActiveMinistriesReturningEmptyList() throws Exception {
        when(personService.findPeople(null, null, null, null, null, null, null, 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(adminResponse(1L, "Alice", List.of(), List.of("ROLE_OPERATOR"))),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ministries").isArray())
                .andExpect(jsonPath("$.content[0].ministries").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPersonWithSeveralMinistriesSortedDeterministically() throws Exception {
        when(personService.findPeople(null, null, null, null, null, null, null, 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(adminResponse(
                                1L,
                                "Alice",
                                List.of(MinistryType.PRIEST, MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER),
                                List.of("ROLE_OPERATOR")
                        )),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ministries[0]").value("PRIEST"))
                .andExpect(jsonPath("$.content[0].ministries[1]").value("READER"))
                .andExpect(jsonPath("$.content[0].ministries[2]").value("EUCHARISTIC_MINISTER"))
                .andExpect(jsonPath("$.content[0].ministries.length()").value(3));
    }

    @ParameterizedTest
    @EnumSource(MinistryType.class)
    @WithMockUser(roles = "ADMIN")
    void shouldFilterPeopleByEachMinistryType(MinistryType ministryType) throws Exception {
        String filterValue = ministryType.name().toLowerCase();
        when(personService.findPeople(null, null, filterValue, null, null, null, null, 0, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/pessoas").param("ministry", filterValue))
                .andExpect(status().isOk());

        verify(personService).findPeople(null, null, filterValue, null, null, null, null, 0, 10);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldFindPersonByIdWhenUserIsAdmin() throws Exception {
        when(personService.findPersonById(1L))
                .thenReturn(adminResponse(1L, "Alice", List.of(MinistryType.READER, MinistryType.COMMENTATOR), List.of("ROLE_OPERATOR")));

        mockMvc.perform(get("/pessoas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ministries[0]").value("READER"))
                .andExpect(jsonPath("$.ministries[1]").value("COMMENTATOR"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.personType").doesNotExist());
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
    void shouldFindAccountStateForPersonWithoutAccount() throws Exception {
        when(userAccountLifecycleService.findAccountState(1L))
                .thenReturn(new UserAccountLifecycleResponseDTO(1L, true, false, null, null, List.of()));

        mockMvc.perform(get("/pessoas/1/conta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(1))
                .andExpect(jsonPath("$.personActive").value(true))
                .andExpect(jsonPath("$.accountExists").value(false))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.accountEnabled").doesNotExist())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());

        verify(userAccountLifecycleService).findAccountState(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateAccountWithoutAcceptingExternalAccountId() throws Exception {
        when(userAccountLifecycleService.createAccount(eq(1L), any()))
                .thenReturn(new UserAccountLifecycleResponseDTO(1L, true, true, "34999999991", true, List.of("ROLE_OPERATOR")));

        mockMvc.perform(post("/pessoas/1/conta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 999,
                                  "initialPassword": "123456",
                                  "role": "ROLE_OPERATOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").value(1))
                .andExpect(jsonPath("$.username").value("34999999991"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());

        verify(userAccountLifecycleService).createAccount(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnConflictEnvelopeFromLifecycleEndpoints() throws Exception {
        doThrow(new LifecycleConflictException("Pessoa inativa nao pode receber conta.", "PERSON_INACTIVE"))
                .when(userAccountLifecycleService).createAccount(eq(1L), any());

        mockMvc.perform(post("/pessoas/1/conta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "initialPassword": "123456",
                                  "role": "ROLE_OPERATOR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSON_INACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDelegateAccountEnabledPersonStatusAndAdminPasswordReset() throws Exception {
        mockMvc.perform(put("/pessoas/1/conta/habilitacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/pessoas/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/pessoas/1/conta/senha")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123456\"}"))
                .andExpect(status().isNoContent());

        verify(userAccountLifecycleService).updateAccountEnabled(eq(1L), any());
        verify(userAccountLifecycleService).updatePersonActive(eq(1L), any());
        verify(userAccountLifecycleService).resetPassword(eq(1L), any());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldAllowOperatorToChangeOnlyOwnPassword() throws Exception {
        mockMvc.perform(put("/pessoas/me/conta/senha")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "personId": 999,
                                  "accountId": 999,
                                  "currentPassword": "123456",
                                  "newPassword": "654321"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userAccountLifecycleService).changeOwnPassword(any());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectOperatorOnAdministrativeLifecycleEndpoints() throws Exception {
        mockMvc.perform(get("/pessoas/1/conta"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/pessoas/1/conta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPassword\":\"123456\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/conta/habilitacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/pessoas/1/conta/senha")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123456\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userAccountLifecycleService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenListFilterIsInvalid() throws Exception {
        when(personService.findPeople(null, null, "invalid", null, null, null, null, 0, 10))
                .thenThrow(new BadRequestException("Ministerio invalido"));

        mockMvc.perform(get("/pessoas")
                        .param("ministry", "invalid"))
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
                .andExpect(jsonPath("$.ministries[0]").value("READER"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.personType").doesNotExist());
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
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of(MinistryType.READER, MinistryType.COMMENTATOR), List.of()));

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
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of(MinistryType.READER), List.of()));

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
                .thenReturn(new PersonMinistriesResponseDTO(1L, List.of(), List.of()));

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

    @Test
    void shouldReturnUnauthorizedWhenGettingOwnProfileWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldGetOwnProfileWhenUserIsOperator() throws Exception {
        when(personService.getCurrentUserProfile(10L))
                .thenReturn(currentProfileResponse(10L, "Joao da Silva", "34999999999", List.of("ROLE_OPERATOR"), List.of(MinistryType.READER)));

        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Joao da Silva"))
                .andExpect(jsonPath("$.phoneNumber").value("34999999999"))
                .andExpect(jsonPath("$.birthdayDate").value("1995-05-20"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.ministries[0]").value("READER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.personType").doesNotExist());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 11L, username = "34999999998", authorities = {"ROLE_ADMIN"})
    void shouldGetOwnProfileWhenUserIsAdmin() throws Exception {
        when(personService.getCurrentUserProfile(11L))
                .thenReturn(currentProfileResponse(11L, "Admin User", "34999999998", List.of("ROLE_ADMIN"), List.of()));

        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.ministries").isEmpty());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 99L, username = "34900000000", authorities = {"ROLE_OPERATOR"})
    void shouldReturnNotFoundWhenGettingOwnProfileOfMissingPerson() throws Exception {
        when(personService.getCurrentUserProfile(99L))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnUnauthorizedWhenUpdatingOwnProfileWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("Joao da Silva", "1995-05-20")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldUpdateOwnProfileWhenUserIsOperator() throws Exception {
        when(personService.updateCurrentUserProfile(eq(10L), any()))
                .thenReturn(currentProfileResponse(10L, "Joao da Silva", "34999999999", List.of("ROLE_OPERATOR"), List.of(MinistryType.READER)));

        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("Joao da Silva", "1995-05-20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Joao da Silva"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 11L, username = "34999999998", authorities = {"ROLE_ADMIN"})
    void shouldUpdateOwnProfileWhenUserIsAdmin() throws Exception {
        when(personService.updateCurrentUserProfile(eq(11L), any()))
                .thenReturn(currentProfileResponse(11L, "Admin User", "34999999998", List.of("ROLE_ADMIN"), List.of()));

        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("Admin User", "1990-01-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin User"));
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenUpdatingOwnProfileWithBlankName() throws Exception {
        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("", "1995-05-20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenUpdatingOwnProfileWithNameOnlySpaces() throws Exception {
        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("   ", "1995-05-20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenUpdatingOwnProfileWithFutureBirthdayDate() throws Exception {
        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("Joao da Silva", "2999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockAuthenticatedUser(personId = 99L, username = "34900000000", authorities = {"ROLE_OPERATOR"})
    void shouldReturnNotFoundWhenUpdatingOwnProfileOfMissingPerson() throws Exception {
        when(personService.updateCurrentUserProfile(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileUpdatePayload("Joao da Silva", "1995-05-20")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldIgnoreProtectedFieldsSentInOwnProfileUpdatePayload() throws Exception {
        when(personService.updateCurrentUserProfile(eq(10L), any()))
                .thenReturn(currentProfileResponse(10L, "Joao da Silva", "34999999999", List.of("ROLE_OPERATOR"), List.of(MinistryType.READER)));

        mockMvc.perform(put("/pessoas/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 999,
                                  "name": "Joao da Silva",
                                  "birthdayDate": "1995-05-20",
                                  "phoneNumber": "00000000000",
                                  "roles": ["ROLE_ADMIN"],
                                  "ministries": ["PRIEST"],
                                  "password": "new-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.phoneNumber").value("34999999999"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                .andExpect(jsonPath("$.ministries[0]").value("READER"));
    }

    @Test
    void shouldReturnUnauthorizedWhenGettingCurrentUserSchedulesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldGetCurrentUserSchedulesWhenUserIsOperator() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 10))
                .thenReturn(new PageImpl<>(
                        List.of(scheduleResponse(
                                15L, "Missa das 19h", LocalDateTime.of(2026, 7, 20, 19, 0), LocalDateTime.of(2026, 7, 20, 20, 0),
                                true, 2L, "Igreja Matriz",
                                List.of(EventAssignmentType.READER, EventAssignmentType.COMMENTATOR)
                        )),
                        PageRequest.of(0, 10),
                        1
                ));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(15))
                .andExpect(jsonPath("$.content[0].eventName").value("Missa das 19h"))
                .andExpect(jsonPath("$.content[0].startAt").value("2026-07-20T19:00:00"))
                .andExpect(jsonPath("$.content[0].endAt").value("2026-07-20T20:00:00"))
                .andExpect(jsonPath("$.content[0].eventDate").doesNotExist())
                .andExpect(jsonPath("$.content[0].eventTime").doesNotExist())
                .andExpect(jsonPath("$.content[0].massOrCelebration").value(true))
                .andExpect(jsonPath("$.content[0].locationId").value(2))
                .andExpect(jsonPath("$.content[0].locationName").value("Igreja Matriz"))
                .andExpect(jsonPath("$.content[0].assignments[0]").value("READER"))
                .andExpect(jsonPath("$.content[0].assignments[1]").value("COMMENTATOR"))
                .andExpect(jsonPath("$.content[0].assignments.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].roles").doesNotExist())
                .andExpect(jsonPath("$.content[0].ministries").doesNotExist())
                .andExpect(jsonPath("$.content[0].personId").doesNotExist());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 11L, username = "34999999998", authorities = {"ROLE_ADMIN"})
    void shouldGetCurrentUserSchedulesWhenUserIsAdmin() throws Exception {
        when(personService.findCurrentUserSchedules(
                11L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenStartDateIsMissingOnCurrentUserSchedules() throws Exception {
        mockMvc.perform(get("/pessoas/me/escalas").param("endDate", "2026-07-31"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenEndDateIsMissingOnCurrentUserSchedules() throws Exception {
        mockMvc.perform(get("/pessoas/me/escalas").param("startDate", "2026-07-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenStartDateFormatIsInvalidOnCurrentUserSchedules() throws Exception {
        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "01-07-2026")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OPERATOR")
    void shouldReturnBadRequestWhenEndDateFormatIsInvalidOnCurrentUserSchedules() throws Exception {
        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "31-07-2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(personService);
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenDateRangeIsInvertedOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), 0, 10))
                .thenThrow(new BadRequestException("A data inicial não pode ser posterior à data final"));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-31")
                        .param("endDate", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenPageIsNegativeOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), -1, 10))
                .thenThrow(new BadRequestException("O numero da pagina deve ser maior ou igual a zero"));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenSizeIsZeroOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 0))
                .thenThrow(new BadRequestException("O tamanho da pagina deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenSizeIsGreaterThanOneHundredOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 101))
                .thenThrow(new BadRequestException("O tamanho da pagina deve ser maior que zero e menor ou igual a 100"));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 99L, username = "34900000000", authorities = {"ROLE_OPERATOR"})
    void shouldReturnNotFoundWhenPrincipalDoesNotExistOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                99L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 10))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldIgnoreUnknownPersonIdParameterOnCurrentUserSchedules() throws Exception {
        when(personService.findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/pessoas/me/escalas")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("personId", "999"))
                .andExpect(status().isOk());

        verify(personService).findCurrentUserSchedules(
                10L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 0, 10);
    }

    @Test
    void shouldReturnUnauthorizedWhenRespondingToParticipationWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldRespondToParticipationWhenUserIsOperator() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenReturn(participationResponse(15L, ParticipationStatus.CONFIRMED, null));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(15))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.declineReason").doesNotExist());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 11L, username = "34999999998", authorities = {"ROLE_ADMIN"})
    void shouldRespondToParticipationWhenUserIsAdmin() throws Exception {
        when(eventParticipationResponseService.respond(eq(11L), eq(15L), any()))
                .thenReturn(participationResponse(15L, ParticipationStatus.DECLINED, "Viagem"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("DECLINED", "Viagem")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("Viagem"));
    }

    @Test
    @WithMockUser(username = "34999999999", roles = "OTHER")
    void shouldReturnForbiddenWhenRoleIsNotAdminOrOperator() throws Exception {
        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventParticipationResponseService);
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenParticipationStatusIsInvalid() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenThrow(new BadRequestException("Status de participacao invalido: UNKNOWN"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("UNKNOWN", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenParticipationStatusIsPending() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenThrow(new BadRequestException("O status PENDING nao pode ser definido diretamente"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("PENDING", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnBadRequestWhenDeclineReasonExceedsLimit() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenThrow(new BadRequestException("O motivo da recusa deve ter no maximo 500 caracteres"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("DECLINED", "a".repeat(501))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 99L, username = "34900000000", authorities = {"ROLE_OPERATOR"})
    void shouldReturnNotFoundWhenPersonDoesNotExistOnParticipation() throws Exception {
        when(eventParticipationResponseService.respond(eq(99L), eq(15L), any()))
                .thenThrow(new ResourceNotFoundException("Pessoa", 99L));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnNotFoundWhenEventDoesNotExistOnParticipation() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Evento celebrativo", 99L));

        mockMvc.perform(put("/pessoas/me/escalas/99/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnConflictWhenPersonHasNoAssignmentOnParticipation() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenThrow(new ConflictException("A pessoa autenticada nao possui atribuicao neste evento"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_CONFLICT"));
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldReturnConflictWhenEventAlreadyStarted() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenThrow(new ConflictException("Nao e possivel responder a participacao apos o inicio do evento"));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(participationPayload("CONFIRMED", null)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockAuthenticatedUser(personId = 10L, username = "34999999999", authorities = {"ROLE_OPERATOR"})
    void shouldIgnorePersonIdInParticipationPayload() throws Exception {
        when(eventParticipationResponseService.respond(eq(10L), eq(15L), any()))
                .thenReturn(participationResponse(15L, ParticipationStatus.CONFIRMED, null));

        mockMvc.perform(put("/pessoas/me/escalas/15/participacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED",
                                  "personId": 999
                                }
                                """))
                .andExpect(status().isOk());

        verify(eventParticipationResponseService).respond(eq(10L), eq(15L), any());
    }

    private String participationPayload(String status, String declineReason) {
        if (declineReason == null) {
            return """
                    {
                      "status": "%s"
                    }
                    """.formatted(status);
        }
        return """
                {
                  "status": "%s",
                  "declineReason": "%s"
                }
                """.formatted(status, declineReason);
    }

    private ParticipationResponseResponseDTO participationResponse(Long eventId, ParticipationStatus status, String declineReason) {
        return new ParticipationResponseResponseDTO(eventId, status, declineReason, LocalDateTime.of(2026, 7, 30, 18, 20));
    }

    private CurrentUserScheduleResponseDTO scheduleResponse(
            Long eventId,
            String eventName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean massOrCelebration,
            Long locationId,
            String locationName,
            List<EventAssignmentType> assignments
    ) {
        return new CurrentUserScheduleResponseDTO(
                eventId, eventName, startAt, endAt, massOrCelebration, locationId, locationName, assignments,
                ParticipationStatus.PENDING, null, null
        );
    }

    private CurrentUserProfileResponseDTO currentProfileResponse(
            Long id, String name, String phoneNumber, List<String> roles, List<MinistryType> ministries
    ) {
        return new CurrentUserProfileResponseDTO(id, name, phoneNumber, LocalDate.of(1995, 5, 20), roles, ministries);
    }

    private String profileUpdatePayload(String name, String birthdayDate) {
        return """
                {
                  "name": "%s",
                  "birthdayDate": "%s"
                }
                """.formatted(name, birthdayDate);
    }

    private PersonAdminResponseDTO adminResponse(Long id, String name, List<MinistryType> ministries, List<String> roles) {
        return new PersonAdminResponseDTO(
                id,
                name,
                "3499999999" + id,
                LocalDate.of(1990, 1, 10),
                true,
                ministries,
                true,
                true,
                "3499999999" + id,
                roles
        );
    }

    private PersonRoleUpdateResponseDTO roleResponse(String role) {
        return new PersonRoleUpdateResponseDTO(
                1L,
                "Reader",
                "34999999991",
                List.of(MinistryType.READER),
                List.of(role)
        );
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
