package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CurrentUserProfileMapper;
import com.eventoscelebrativos.mapper.PersonAdminMapper;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private CurrentUserProfileMapper currentUserProfileMapper;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private EventParticipationResponseService eventParticipationResponseService;

    @Mock
    private UserAccountSynchronizationService userAccountSynchronizationService;

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

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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
        verify(userAccountSynchronizationService).synchronizeExistingPerson(person);
    }

    @Test
    void shouldUpdatePersonRoleToOperatorWhenAnotherAdministratorExists() {
        Person person = person(1L, "Reader", "34999999991", "encoded-password");
        person.addRole(adminRole());
        Person otherAdmin = person(2L, "Admin", "34999999992", "encoded-password");
        otherAdmin.addRole(adminRole());
        Role operatorRole = operatorRole();

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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
        when(personRepository.findByIdWithRolesForUpdate(99L)).thenReturn(Optional.empty());

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
        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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

        when(personRepository.findByIdWithRolesForUpdate(1L)).thenReturn(Optional.of(person));
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

    @Test
    void shouldGetCurrentUserProfileByPhoneNumberOfAuthenticatedPerson() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L)))
                .thenReturn(Map.of(10L, Set.of(MinistryType.READER, MinistryType.COMMENTATOR)));
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "Joao da Silva", "34999999999", person.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        CurrentUserProfileResponseDTO response = service.getCurrentUserProfile("34999999999");

        assertEquals(10L, response.getId());
        assertEquals("Joao da Silva", response.getName());
        assertEquals("34999999999", response.getPhoneNumber());
        assertEquals(person.getBirthdayDate(), response.getBirthdayDate());
        assertEquals(List.of("ROLE_OPERATOR"), response.getRoles());
        assertEquals(List.of(MinistryType.READER, MinistryType.COMMENTATOR), response.getMinistries());
    }

    @Test
    void shouldReturnEmptyMinistriesWhenAuthenticatedPersonHasNoActiveMinistry() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L))).thenReturn(Map.of());
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "Joao da Silva", "34999999999", person.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        CurrentUserProfileResponseDTO response = service.getCurrentUserProfile("34999999999");

        assertEquals(List.of(), response.getMinistries());
    }

    @Test
    void shouldSortCurrentUserProfileMinistriesDeterministically() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L)))
                .thenReturn(Map.of(10L, Set.of(MinistryType.EUCHARISTIC_MINISTER, MinistryType.READER, MinistryType.PRIEST)));
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "Joao da Silva", "34999999999", person.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        CurrentUserProfileResponseDTO response = service.getCurrentUserProfile("34999999999");

        assertEquals(
                List.of(MinistryType.PRIEST, MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER),
                response.getMinistries()
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenAuthenticatedPersonDoesNotExistOnGet() {
        when(personRepository.findByPhoneNumber("34900000000")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentUserProfile("34900000000"));
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldUpdateCurrentUserProfileNameAndBirthdayDate() {
        Person person = person(10L, "Old Name", "34999999999", "encoded-password");
        person.addRole(operatorRole());
        LocalDate newBirthday = LocalDate.of(1992, 3, 15);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L)))
                .thenReturn(Map.of(10L, Set.of(MinistryType.READER)));
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "New Name", "34999999999", newBirthday, List.of("ROLE_OPERATOR")
        ));

        CurrentUserProfileResponseDTO response = service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("New Name", newBirthday)
        );

        assertEquals("New Name", response.getName());
        assertEquals(newBirthday, response.getBirthdayDate());
        assertEquals("New Name", person.getName());
        assertEquals(newBirthday, person.getBirthdayDate());
    }

    @Test
    void shouldTrimNameBeforePersistingOnUpdate() {
        Person person = person(10L, "Old Name", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L))).thenReturn(Map.of());
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "New Name", "34999999999", person.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("  New Name  ", person.getBirthdayDate())
        );

        assertEquals("New Name", person.getName());
    }

    @Test
    void shouldKeepProtectedFieldsUnchangedWhenUpdatingCurrentUserProfile() {
        Person person = person(10L, "Old Name", "34999999999", "encoded-password");
        person.addRole(operatorRole());

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(personRepository.save(person)).thenReturn(person);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L)))
                .thenReturn(Map.of(10L, Set.of(MinistryType.READER)));
        when(currentUserProfileMapper.toDto(person)).thenReturn(currentProfileResponse(
                10L, "New Name", "34999999999", person.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("New Name", person.getBirthdayDate())
        );

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(captor.capture());
        Person saved = captor.getValue();
        assertEquals(10L, saved.getId());
        assertEquals("34999999999", saved.getPhoneNumber());
        assertEquals("encoded-password", saved.getPassword());
        assertTrue(saved.hasRole("ROLE_OPERATOR"));
        verifyNoInteractions(roleRepository, personMinistryCommandService);
    }

    @Test
    void shouldThrowResourceNotFoundWhenAuthenticatedPersonDoesNotExistOnUpdate() {
        when(personRepository.findByPhoneNumber("34900000000")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateCurrentUserProfile(
                "34900000000",
                new CurrentUserProfileUpdateRequestDTO("Name", LocalDate.of(1990, 1, 1))
        ));
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldThrowBadRequestWhenCurrentUserProfileNameIsBlankOnUpdate() {
        Person person = person(10L, "Old Name", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));

        assertThrows(BadRequestException.class, () -> service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("", person.getBirthdayDate())
        ));
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldThrowBadRequestWhenCurrentUserProfileNameIsOnlySpacesOnUpdate() {
        Person person = person(10L, "Old Name", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));

        assertThrows(BadRequestException.class, () -> service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("   ", person.getBirthdayDate())
        ));
        verify(personRepository, never()).save(any());
    }

    @Test
    void shouldUpdateOnlyAuthenticatedPersonNotAnotherPerson() {
        Person authenticatedPerson = person(10L, "Old Name", "34999999999", "encoded-password");
        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(authenticatedPerson));
        when(personRepository.save(authenticatedPerson)).thenReturn(authenticatedPerson);
        when(personMinistryReadService.findActiveMinistriesByPersonIds(List.of(10L))).thenReturn(Map.of());
        when(currentUserProfileMapper.toDto(authenticatedPerson)).thenReturn(currentProfileResponse(
                10L, "New Name", "34999999999", authenticatedPerson.getBirthdayDate(), List.of("ROLE_OPERATOR")
        ));

        service.updateCurrentUserProfile(
                "34999999999",
                new CurrentUserProfileUpdateRequestDTO("New Name", authenticatedPerson.getBirthdayDate())
        );

        verify(personRepository, never()).findByPhoneNumber(argThat(phone -> !"34999999999".equals(phone)));
        verify(personRepository, times(1)).save(any());
        verify(personRepository).save(authenticatedPerson);
    }

    @Test
    void shouldFindCurrentUserSchedulesByPhoneNumberOfAuthenticatedPerson() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(eventProjection(15L, "Missa das 19h", startDate, LocalTime.of(19, 0), true, 2L, "Igreja Matriz")),
                        pageable,
                        1
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(15L)))
                .thenReturn(List.of(assignmentProjection(15L, "READER")));
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(15L)))
                .thenReturn(Map.of());

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(15L, result.getContent().get(0).getEventId());
        assertEquals("Missa das 19h", result.getContent().get(0).getEventName());
        assertEquals(2L, result.getContent().get(0).getLocationId());
        assertEquals("Igreja Matriz", result.getContent().get(0).getLocationName());
        assertEquals(ParticipationStatus.PENDING, result.getContent().get(0).getParticipationStatus());
        assertNull(result.getContent().get(0).getDeclineReason());
        assertNull(result.getContent().get(0).getRespondedAt());
        verify(eventAssignmentRepository).findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable);
    }

    @Test
    void shouldMapConfirmedParticipationOnCurrentUserSchedules() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(eventProjection(15L, "Missa das 19h", startDate, LocalTime.of(19, 0), true, 2L, "Igreja Matriz")),
                        pageable,
                        1
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(15L)))
                .thenReturn(List.of(assignmentProjection(15L, "READER")));
        LocalDateTime respondedAt = LocalDateTime.of(2026, 7, 30, 18, 20);
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(15L)))
                .thenReturn(Map.of(15L, new ParticipationResponseSnapshot(15L, 10L, ParticipationStatus.CONFIRMED, null, respondedAt)));

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertEquals(ParticipationStatus.CONFIRMED, result.getContent().get(0).getParticipationStatus());
        assertNull(result.getContent().get(0).getDeclineReason());
        assertEquals(respondedAt, result.getContent().get(0).getRespondedAt());
    }

    @Test
    void shouldMapDeclinedParticipationWithReasonOnCurrentUserSchedules() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(eventProjection(15L, "Missa das 19h", startDate, LocalTime.of(19, 0), true, 2L, "Igreja Matriz")),
                        pageable,
                        1
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(15L)))
                .thenReturn(List.of(assignmentProjection(15L, "READER")));
        LocalDateTime respondedAt = LocalDateTime.of(2026, 7, 30, 18, 21);
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(15L)))
                .thenReturn(Map.of(15L, new ParticipationResponseSnapshot(15L, 10L, ParticipationStatus.DECLINED, "Viagem", respondedAt)));

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertEquals(ParticipationStatus.DECLINED, result.getContent().get(0).getParticipationStatus());
        assertEquals("Viagem", result.getContent().get(0).getDeclineReason());
        assertEquals(respondedAt, result.getContent().get(0).getRespondedAt());
    }

    @Test
    void shouldFetchParticipationResponsesInBatchByPersonAndEventIdsOnFindSchedules() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(
                                eventProjection(5L, "Evento Um", startDate, LocalTime.of(8, 0), true, null, null),
                                eventProjection(6L, "Evento Dois", startDate, LocalTime.of(19, 0), true, null, null)
                        ),
                        pageable,
                        2
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(5L, 6L)))
                .thenReturn(List.of(assignmentProjection(5L, "READER"), assignmentProjection(6L, "READER")));
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(5L, 6L)))
                .thenReturn(Map.of());

        service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        verify(eventParticipationResponseService, times(1)).findByPersonIdAndEventIds(10L, List.of(5L, 6L));
    }

    @Test
    void shouldThrowResourceNotFoundWhenAuthenticatedPersonDoesNotExistOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        when(personRepository.findByPhoneNumber("34900000000")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.findCurrentUserSchedules("34900000000", startDate, endDate, 0, 10));
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRejectMissingStartDateOnFindSchedules() {
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", null, endDate, 0, 10));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectMissingEndDateOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", startDate, null, 0, 10));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectInvertedDateRangeOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 31);
        LocalDate endDate = LocalDate.of(2026, 8, 1);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectNegativePageOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", startDate, endDate, -1, 10));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectSizeLessThanOneOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 0));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldRejectSizeGreaterThanOneHundredOnFindSchedules() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        assertThrows(BadRequestException.class,
                () -> service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 101));
        verifyNoInteractions(personRepository, eventAssignmentRepository);
    }

    @Test
    void shouldAggregateAssignmentsAcrossDifferentEventsSortedByEnumNaturalOrder() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(eventProjection(15L, "Missa das 19h", startDate, LocalTime.of(19, 0), true, 2L, "Igreja Matriz")),
                        pageable,
                        1
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(15L)))
                .thenReturn(List.of(assignmentProjection(15L, "COMMENTATOR")));
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(15L)))
                .thenReturn(Map.of());

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertEquals(
                List.of(EventAssignmentType.COMMENTATOR),
                result.getContent().get(0).getAssignments()
        );
    }

    @Test
    void shouldPreserveEventOrderReturnedByRepositoryOnFindSchedules() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(
                                eventProjection(5L, "Evento Cedo", LocalDate.of(2026, 8, 5), LocalTime.of(8, 0), true, null, null),
                                eventProjection(20L, "Evento Tarde", LocalDate.of(2026, 8, 5), LocalTime.of(19, 0), true, null, null)
                        ),
                        pageable,
                        2
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(5L, 20L)))
                .thenReturn(List.of(assignmentProjection(5L, "READER"), assignmentProjection(20L, "PRIEST")));
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(5L, 20L)))
                .thenReturn(Map.of());

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertEquals(List.of(5L, 20L), result.getContent().stream().map(CurrentUserScheduleResponseDTO::getEventId).toList());
    }

    @Test
    void shouldReturnEmptyPageWithoutFetchingAssignmentsWhenNoEventsMatchOnFindSchedules() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<CurrentUserScheduleResponseDTO> result = service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(eventAssignmentRepository, never()).findAssignmentTypesByPersonIdAndEventIdIn(any(), any());
    }

    @Test
    void shouldNotConsultMinistriesToDecideScheduleResults() {
        Person person = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(person));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(
                        List.of(eventProjection(15L, "Missa das 19h", startDate, LocalTime.of(19, 0), true, 2L, "Igreja Matriz")),
                        pageable,
                        1
                ));
        when(eventAssignmentRepository.findAssignmentTypesByPersonIdAndEventIdIn(10L, List.of(15L)))
                .thenReturn(List.of(assignmentProjection(15L, "READER")));
        when(eventParticipationResponseService.findByPersonIdAndEventIds(10L, List.of(15L)))
                .thenReturn(Map.of());

        service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldUseIdFoundByAuthenticatedPrincipalNotAnotherPerson() {
        Person authenticatedPerson = person(10L, "Joao da Silva", "34999999999", "encoded-password");
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(personRepository.findByPhoneNumber("34999999999")).thenReturn(Optional.of(authenticatedPerson));
        when(eventAssignmentRepository.findScheduleEventsByPersonId(10L, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.findCurrentUserSchedules("34999999999", startDate, endDate, 0, 10);

        verify(eventAssignmentRepository).findScheduleEventsByPersonId(
                eq(10L), eq(startDate.atStartOfDay()), eq(endDate.plusDays(1).atStartOfDay()), eq(pageable));
        verify(personRepository, never()).findByPhoneNumber(argThat(phone -> !"34999999999".equals(phone)));
    }

    private PersonScheduleEventProjection eventProjection(
            Long eventId,
            String eventName,
            LocalDate eventDate,
            LocalTime eventTime,
            Boolean massOrCelebration,
            Long locationId,
            String locationName
    ) {
        LocalDateTime startAt = LocalDateTime.of(eventDate, eventTime);
        LocalDateTime endAt = startAt.plusHours(1);
        return new PersonScheduleEventProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return eventName;
            }

            @Override
            public LocalDateTime getStartAt() {
                return startAt;
            }

            @Override
            public LocalDateTime getEndAt() {
                return endAt;
            }

            @Override
            public Boolean getMassOrCelebration() {
                return massOrCelebration;
            }

            @Override
            public Long getLocationId() {
                return locationId;
            }

            @Override
            public String getLocationName() {
                return locationName;
            }
        };
    }

    private PersonScheduleAssignmentProjection assignmentProjection(Long eventId, String assignmentType) {
        return new PersonScheduleAssignmentProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getAssignmentType() {
                return assignmentType;
            }
        };
    }

    private CurrentUserProfileResponseDTO currentProfileResponse(
            Long id, String name, String phoneNumber, LocalDate birthdayDate, List<String> roles
    ) {
        return new CurrentUserProfileResponseDTO(id, name, phoneNumber, birthdayDate, roles, List.of());
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
