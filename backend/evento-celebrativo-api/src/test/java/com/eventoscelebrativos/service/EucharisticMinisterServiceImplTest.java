package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.EucharisticMinisterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EucharisticMinisterServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1992, 3, 12);
    private static final String FIND_ENTITY_LABEL = "Ministro De Eucaristia";
    private static final String MUTATION_ENTITY_LABEL = "Ministro de Eucaristia";

    @Mock
    private EucharisticMinisterMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @InjectMocks
    private EucharisticMinisterServiceImpl service;

    @Test
    void shouldCreateEucharisticMinisterWithEncryptedPasswordAndOperatorRole() {
        EucharisticMinisterRequestDTO request = request();
        EucharisticMinister entity = minister(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        EucharisticMinister saved = minister(1L, "encoded-password");
        EucharisticMinisterResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personMinistryCommandService.create(any(Person.class), eq(MinistryType.EUCHARISTIC_MINISTER))).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createEucharisticMinister(request));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMinistryCommandService).create(captor.capture(), eq(MinistryType.EUCHARISTIC_MINISTER));
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        EucharisticMinisterRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(minister(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createEucharisticMinister(request));
        verify(personMinistryCommandService, never()).create(any(), any());
    }

    @Test
    void shouldFindEucharisticMinisterByIdWhenExists() {
        EucharisticMinister entity = minister(1L, "encoded-password");
        EucharisticMinisterResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.EUCHARISTIC_MINISTER, FIND_ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findEucharisticMinistersById(1L));
    }

    @Test
    void shouldThrowWhenEucharisticMinisterIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(null));
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.EUCHARISTIC_MINISTER, FIND_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(FIND_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findEucharisticMinistersById(99L));
    }

    @Test
    void shouldUpdateAndDeleteEucharisticMinister() {
        EucharisticMinister entity = minister(1L, "old-password");
        EucharisticMinisterResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(personMinistryCommandService.save(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updateEucharisticMinisters(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        service.deleteEucharisticMinisterById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);
    }

    @Test
    void shouldListEucharisticMinistersUsingPersonMinistryWithoutCallingLegacyRepository() {
        Reader readerWithEucharisticMinisterMinistry = reader(2L, "encoded-password");
        EucharisticMinisterResponseDTO response = new EucharisticMinisterResponseDTO(
                2L,
                "Minister",
                "34999999993",
                BIRTHDAY
        );
        List<Person> people = List.of(readerWithEucharisticMinisterMinistry);
        List<EucharisticMinisterResponseDTO> responses = List.of(response);

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllEucharisticMinisters());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveEucharisticMinisterOrderReturnedByPersonMinistryReadService() {
        EucharisticMinister first = minister(1L, "encoded-password");
        Reader second = reader(2L, "encoded-password");
        List<Person> people = List.of(first, second);
        List<EucharisticMinisterResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllEucharisticMinisters());

        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllEucharisticMinisters()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingEucharisticMinister() {
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateEucharisticMinisters(99L, request()));

        doThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteEucharisticMinisterById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEucharisticMinister() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.EUCHARISTIC_MINISTER, MUTATION_ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deleteEucharisticMinisterById(1L));
    }

    private EucharisticMinisterRequestDTO request() {
        return new EucharisticMinisterRequestDTO("Minister", "34999999993", BIRTHDAY, "raw-password");
    }

    private EucharisticMinister minister(Long id, String password) {
        EucharisticMinister minister = new EucharisticMinister();
        minister.setId(id);
        minister.setName("Minister");
        minister.setPhoneNumber("34999999993");
        minister.setBirthdayDate(BIRTHDAY);
        minister.setPassword(password);
        return minister;
    }

    private Reader reader(Long id, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Minister");
        reader.setPhoneNumber("34999999993");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }

    private EucharisticMinisterResponseDTO response(Long id) {
        return new EucharisticMinisterResponseDTO(id, "Minister", "34999999993", BIRTHDAY);
    }
}
