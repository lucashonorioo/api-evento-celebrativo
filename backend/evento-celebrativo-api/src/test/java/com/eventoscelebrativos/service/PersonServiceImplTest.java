package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonAdminMapper;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.PersonServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @InjectMocks
    private PersonServiceImpl service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFindPeopleWithPaginationAndCombinedFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        Person first = person(2L, "Alice", "34911111111", "encoded-password");
        Person second = person(1L, "Alice", "34922222222", "encoded-password");

        when(personRepository.findAdminPageIds("Ali", "349", MinistryType.READER, "ROLE_ADMIN", pageable))
                .thenReturn(new PageImpl<>(List.of(2L, 1L), pageable, 2));
        when(personRepository.findAllByIdInWithRoles(List.of(2L, 1L)))
                .thenReturn(List.of(second, first));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(2L, 1L)))
                .thenReturn(Map.of(2L, Set.of(MinistryType.READER)));
        when(personAdminMapper.toDto(first)).thenReturn(adminResponse(2L, "Alice", "ROLE_ADMIN"));
        when(personAdminMapper.toDto(second)).thenReturn(adminResponse(1L, "Alice", "ROLE_OPERATOR"));

        Page<PersonAdminResponseDTO> result = service.findPeople(" Ali ", " 349 ", "reader", "ROLE_ADMIN", 0, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals(2L, result.getContent().get(0).getId());
        assertEquals(List.of(MinistryType.READER), result.getContent().get(0).getMinistries());
        assertEquals(1L, result.getContent().get(1).getId());
        assertEquals(List.of(), result.getContent().get(1).getMinistries());
        verify(personMinistryReadService, times(1)).findActiveMinistriesByPersonIds(List.of(2L, 1L));
    }

    @Test
    void shouldFindPeopleWithSeveralMinistriesSortedDeterministicallyWithoutDuplicates() {
        PageRequest pageable = PageRequest.of(0, 10);
        Person person = person(1L, "Alice", "34911111111", "encoded-password");

        when(personRepository.findAdminPageIds(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(1L), pageable, 1));
        when(personRepository.findAllByIdInWithRoles(List.of(1L)))
                .thenReturn(List.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of(1L, Set.of(MinistryType.EUCHARISTIC_MINISTER, MinistryType.READER, MinistryType.PRIEST)));
        when(personAdminMapper.toDto(person)).thenReturn(adminResponse(1L, "Alice", "ROLE_OPERATOR"));

        Page<PersonAdminResponseDTO> result = service.findPeople(null, null, null, null, 0, 10);

        assertEquals(
                List.of(MinistryType.PRIEST, MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER),
                result.getContent().get(0).getMinistries()
        );
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

    @ParameterizedTest
    @EnumSource(MinistryType.class)
    void shouldFilterPeopleByEachOfTheFiveMinistryTypes(MinistryType ministryType) {
        PageRequest pageable = PageRequest.of(0, 10);
        String filterValue = ministryType.name().toLowerCase();
        when(personRepository.findAdminPageIds(null, null, ministryType, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<PersonAdminResponseDTO> result = service.findPeople(null, null, filterValue, null, 0, 10);

        assertEquals(0, result.getTotalElements());
        verify(personRepository).findAdminPageIds(null, null, ministryType, null, pageable);
    }

    @Test
    void shouldFindPersonById() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of());
        when(personAdminMapper.toDto(person)).thenReturn(adminResponse(1L, "Reader", "ROLE_OPERATOR"));

        PersonAdminResponseDTO response = service.findPersonById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Reader", response.getName());
        assertEquals(List.of(), response.getMinistries());
    }

    @Test
    void shouldFindPersonByIdWithSeveralMinistriesSortedDeterministically() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of(1L, Set.of(MinistryType.EUCHARISTIC_MINISTER, MinistryType.READER, MinistryType.PRIEST)));
        when(personAdminMapper.toDto(person)).thenReturn(adminResponse(1L, "Reader", "ROLE_OPERATOR"));

        PersonAdminResponseDTO response = service.findPersonById(1L);

        assertEquals(
                List.of(MinistryType.PRIEST, MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER),
                response.getMinistries()
        );
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
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(operatorRole());
        Role adminRole = adminRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of(1L, Set.of(MinistryType.READER)));
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        assertEquals(1L, response.getId());
        assertEquals("ROLE_ADMIN", response.getRoles().get(0));
        assertEquals(List.of(MinistryType.READER), response.getMinistries());
        assertTrue(person.hasRole("ROLE_ADMIN"));
        assertFalse(person.hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldUpdatePersonRoleToOperatorWhenAnotherAdministratorExists() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        Person otherAdmin = person(2L, "Admin", "34999999992", "encoded-password");
        otherAdmin.addRole(adminRole());
        Role operatorRole = operatorRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN")).thenReturn(List.of(person, otherAdmin));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of());
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_OPERATOR"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR"));

        assertEquals("ROLE_OPERATOR", response.getRoles().get(0));
        assertTrue(person.hasRole("ROLE_OPERATOR"));
        assertFalse(person.hasRole("ROLE_ADMIN"));
    }

    @Test
    void shouldNotChangePasswordWhenUpdatingRole() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        Role adminRole = adminRole();

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of());
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
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
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN")));

        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldBlockSelfAdminDemotion() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
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
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        authenticateAs("34999999991");

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_ADMIN")).thenReturn(Optional.of(adminRole()));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of());
        when(personRoleUpdateMapper.toDto(person)).thenReturn(roleResponse("ROLE_ADMIN"));

        PersonRoleUpdateResponseDTO response = service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_ADMIN"));

        assertEquals("ROLE_ADMIN", response.getRoles().get(0));
        verify(personRepository, never()).findPeopleByRoleForUpdate(any());
    }

    @Test
    void shouldBlockLastAdministratorDemotion() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());

        when(personRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(person));
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole()));
        when(personRepository.findPeopleByRoleForUpdate("ROLE_ADMIN")).thenReturn(List.of(person));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.updatePersonRole(1L, new PersonRoleUpdateRequestDTO("ROLE_OPERATOR")));

        assertEquals("O ultimo administrador do sistema nao pode ter seu perfil alterado.", exception.getMessage());
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldFindPersonMinistriesWithZeroMinistries() {
        when(personRepository.existsById(1L)).thenReturn(true);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L))).thenReturn(Map.of());

        PersonMinistriesResponseDTO response = service.findPersonMinistries(1L);

        assertEquals(1L, response.getId());
        assertEquals(List.of(), response.getMinistries());
    }

    @Test
    void shouldFindPersonMinistriesWithOneMinistry() {
        when(personRepository.existsById(1L)).thenReturn(true);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of(1L, Set.of(MinistryType.READER)));

        PersonMinistriesResponseDTO response = service.findPersonMinistries(1L);

        assertEquals(List.of(MinistryType.READER), response.getMinistries());
    }

    @Test
    void shouldFindPersonMinistriesWithSeveralMinistriesSortedByNaturalOrder() {
        when(personRepository.existsById(1L)).thenReturn(true);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(1L)))
                .thenReturn(Map.of(1L, Set.of(MinistryType.EUCHARISTIC_MINISTER, MinistryType.READER, MinistryType.PRIEST)));

        PersonMinistriesResponseDTO response = service.findPersonMinistries(1L);

        assertEquals(
                List.of(MinistryType.PRIEST, MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER),
                response.getMinistries()
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenFindingMinistriesOfMissingPerson() {
        when(personRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.findPersonMinistries(99L));
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldThrowBusinessExceptionWhenFindMinistriesIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findPersonMinistries(null)),
                () -> assertThrows(BusinessException.class, () -> service.findPersonMinistries(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findPersonMinistries(-1L))
        );
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldUpdatePersonMinistriesDelegatingParsedSetToCommandService() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        PersonMinistrySyncResult result = new PersonMinistrySyncResult(
                person,
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR),
                Set.of(MinistryType.COMMENTATOR),
                Set.of(),
                Set.of(),
                Set.of(MinistryType.READER)
        );
        when(personMinistryCommandService.syncMinistries(1L, Set.of(MinistryType.READER, MinistryType.COMMENTATOR)))
                .thenReturn(result);

        PersonMinistriesResponseDTO response = service.updatePersonMinistries(
                1L,
                new PersonMinistriesUpdateRequestDTO(List.of("READER", "COMMENTATOR"))
        );

        assertEquals(1L, response.getId());
        assertEquals(List.of(MinistryType.READER, MinistryType.COMMENTATOR), response.getMinistries());
    }

    @Test
    void shouldAllowEmptyMinistriesListInUpdateRequestMeaningRemoveAll() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        PersonMinistrySyncResult result = new PersonMinistrySyncResult(
                person, Set.of(), Set.of(), Set.of(), Set.of(MinistryType.READER), Set.of()
        );
        when(personMinistryCommandService.syncMinistries(1L, Set.of())).thenReturn(result);

        PersonMinistriesResponseDTO response = service.updatePersonMinistries(
                1L,
                new PersonMinistriesUpdateRequestDTO(List.of())
        );

        assertEquals(List.of(), response.getMinistries());
    }

    @Test
    void shouldRejectNullMinistriesListInUpdateRequest() {
        assertThrows(BusinessException.class,
                () -> service.updatePersonMinistries(1L, new PersonMinistriesUpdateRequestDTO(null)));
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldRejectDuplicateMinistryInUpdateRequest() {
        assertThrows(BusinessException.class,
                () -> service.updatePersonMinistries(1L, new PersonMinistriesUpdateRequestDTO(List.of("READER", "READER"))));
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldRejectInvalidMinistryValueInUpdateRequest() {
        assertThrows(BadRequestException.class,
                () -> service.updatePersonMinistries(1L, new PersonMinistriesUpdateRequestDTO(List.of("BISHOP"))));
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldRejectBlankMinistryValueInUpdateRequest() {
        assertThrows(BadRequestException.class,
                () -> service.updatePersonMinistries(1L, new PersonMinistriesUpdateRequestDTO(List.of(" "))));
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldAcceptMinistryValueRegardlessOfCase() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        PersonMinistrySyncResult result = new PersonMinistrySyncResult(
                person, Set.of(MinistryType.READER), Set.of(MinistryType.READER), Set.of(), Set.of(), Set.of()
        );
        when(personMinistryCommandService.syncMinistries(1L, Set.of(MinistryType.READER))).thenReturn(result);

        PersonMinistriesResponseDTO response = service.updatePersonMinistries(
                1L,
                new PersonMinistriesUpdateRequestDTO(List.of("reader"))
        );

        assertEquals(List.of(MinistryType.READER), response.getMinistries());
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "password", List.of())
        );
    }

    private Person person(Long id, String name, String phoneNumber, String password) {
        Person person = new Person();
        person.setId(id);
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword(password);
        return person;
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
                List.of(),
                List.of(role)
        );
    }

    private PersonRoleUpdateResponseDTO roleResponse(String role) {
        return new PersonRoleUpdateResponseDTO(
                1L,
                "Reader",
                "34999999991",
                List.of(),
                List.of(role)
        );
    }
}
