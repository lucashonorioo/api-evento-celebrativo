package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonAdminMapper;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.PersonServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PersonAdminMapper personAdminMapper;

    @Mock
    private PersonRoleUpdateMapper personRoleUpdateMapper;

    @InjectMocks
    private PersonServiceImpl service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFindPeopleWithPaginationAndCombinedFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        Reader first = person(2L, "Alice", "34911111111", "encoded-password");
        Reader second = person(1L, "Alice", "34922222222", "encoded-password");

        when(personRepository.findAdminPageIds("Ali", "349", "reader", "ROLE_ADMIN", pageable))
                .thenReturn(new PageImpl<>(List.of(2L, 1L), pageable, 2));
        when(personRepository.findAllByIdInWithRoles(List.of(2L, 1L)))
                .thenReturn(List.of(second, first));
        when(personAdminMapper.toDto(first)).thenReturn(adminResponse(2L, "Alice", "ROLE_ADMIN"));
        when(personAdminMapper.toDto(second)).thenReturn(adminResponse(1L, "Alice", "ROLE_OPERATOR"));

        Page<PersonAdminResponseDTO> result = service.findPeople(" Ali ", " 349 ", "reader", "ROLE_ADMIN", 0, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals(2L, result.getContent().get(0).getId());
        assertEquals(1L, result.getContent().get(1).getId());
    }

    @Test
    void shouldReturnEmptyPageWithoutLoadingRolesWhenNoPeopleMatch() {
        PageRequest pageable = PageRequest.of(1, 10);
        when(personRepository.findAdminPageIds(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<PersonAdminResponseDTO> result = service.findPeople("", " ", null, "", 1, 10);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(personRepository, never()).findAllByIdInWithRoles(any());
    }

    @Test
    void shouldThrowBadRequestWhenFiltersOrPaginationAreInvalid() {
        assertAll(
                () -> assertThrows(BadRequestException.class,
                        () -> service.findPeople(null, null, "invalid", null, 0, 10)),
                () -> assertThrows(BadRequestException.class,
                        () -> service.findPeople(null, null, null, "ROLE_UNKNOWN", 0, 10)),
                () -> assertThrows(BadRequestException.class,
                        () -> service.findPeople(null, null, null, null, -1, 10)),
                () -> assertThrows(BadRequestException.class,
                        () -> service.findPeople(null, null, null, null, 0, 0)),
                () -> assertThrows(BadRequestException.class,
                        () -> service.findPeople(null, null, null, null, 0, 101))
        );
    }

    @Test
    void shouldFindPersonById() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(personAdminMapper.toDto(person)).thenReturn(adminResponse(1L, "Reader", "ROLE_OPERATOR"));

        PersonAdminResponseDTO response = service.findPersonById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Reader", response.getName());
    }

    @Test
    void shouldThrowResourceNotFoundWhenFindingMissingPersonById() {
        when(personRepository.findByIdWithRoles(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findPersonById(99L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenFindByIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findPersonById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findPersonById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findPersonById(-1L))
        );
    }

    @Test
    void shouldUpdatePersonRoleToAdmin() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(operatorRole());
        Role adminRole = adminRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        assertEquals(1L, response.getId());
        assertEquals("ROLE_ADMIN", response.getRoles().get(0));
        assertTrue(person.hasRole("ROLE_ADMIN"));
        assertFalse(person.hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldUpdatePersonRoleToOperatorWhenAnotherAdministratorExists() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        Reader otherAdmin = person(2L, "Admin", "34999999992", "encoded-password");
        otherAdmin.addRole(adminRole());
        Role operatorRole = operatorRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN")).thenReturn(List.of(person, otherAdmin));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_OPERATOR"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR"));

        assertEquals("ROLE_OPERATOR", response.getRoles().get(0));
        assertTrue(person.hasRole("ROLE_OPERATOR"));
        assertFalse(person.hasRole("ROLE_ADMIN"));
    }

    @Test
    void shouldNotChangePasswordWhenUpdatingRole() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        Role adminRole = adminRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(personRepository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
    }

    @Test
    void shouldThrowBusinessExceptionWhenIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(null, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))),
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(0L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))),
                () -> assertThrows(BusinessException.class, () -> service.updatePersonRole(-1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")))
        );

        verifyNoInteractions(personRepository, roleRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findByIdWithRoles(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updatePersonRole(99L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")));

        verifyNoInteractions(roleRepository);
    }

    @Test
    void shouldThrowBadRequestWhenRoleIsInvalid() {
        assertThrows(BadRequestException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_UNKNOWN")));

        verifyNoInteractions(personRepository, roleRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenAllowedRoleDoesNotExistInDatabase() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")));

        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldBlockSelfAdminDemotion() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        authenticateAs("34999999991");

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole()));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR")));

        assertEquals("Voce nao pode remover o seu proprio perfil administrativo.", exception.getMessage());
        verify(personRepository, never()).save(any());
        verify(personRepository, never()).findPeopleByRoleForUpdate(any());
    }

    @Test
    void shouldAllowCurrentUserToKeepAdminRole() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        authenticateAs("34999999991");

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole()));
        when(personRepository.save(person)).thenReturn(person);
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        assertEquals("ROLE_ADMIN", response.getRoles().get(0));
        verify(personRepository, never()).findPeopleByRoleForUpdate(any());
    }

    @Test
    void shouldBlockLastAdministratorDemotion() {
        Reader person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole()));
        when(personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN")).thenReturn(List.of(person));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR")));

        assertEquals("O ultimo administrador do sistema nao pode ter seu perfil alterado.", exception.getMessage());
        verify(personRepository, never()).save(any());
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "password", List.of())
        );
    }

    private Reader person(Long id, String name, String phoneNumber, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName(name);
        reader.setPhoneNumber(phoneNumber);
        reader.setBirthdayDate(LocalDate.of(1990, 1, 10));
        reader.setPassword(password);
        reader.setPersonType("reader");
        return reader;
    }

    private Role adminRole() {
        return new Role(2L, "ROLE_ADMIN");
    }

    private Role operatorRole() {
        return new Role(1L, "ROLE_OPERATOR");
    }

    private PersonAdminResponseDTO adminResponse(Long id, String name, String role) {
        return new PersonAdminResponseDTO(
                id,
                name,
                "3499999999" + id,
                "reader",
                List.of(role)
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
}
