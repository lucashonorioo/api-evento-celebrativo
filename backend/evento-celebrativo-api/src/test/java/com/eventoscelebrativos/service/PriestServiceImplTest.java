package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.PriestServiceImpl;
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
class PriestServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1980, 5, 14);
    private static final String ENTITY_LABEL = "Padre";

    @Mock
    private PriestMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @InjectMocks
    private PriestServiceImpl service;

    @Test
    void shouldCreatePriestWithEncryptedPasswordAndOperatorRole() {
        PriestRequestDTO request = request();
        Priest entity = priest(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Priest saved = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personMinistryCommandService.create(any(Person.class), eq(MinistryType.PRIEST))).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createPriest(request));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMinistryCommandService).create(captor.capture(), eq(MinistryType.PRIEST));
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        PriestRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(priest(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createPriest(request));
        verify(personMinistryCommandService, never()).create(any(), any());
    }

    @Test
    void shouldFindPriestByIdWhenExists() {
        Priest entity = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.PRIEST, ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findPriestById(1L));
    }

    @Test
    void shouldThrowWhenPriestIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findPriestById(null));
        assertThrows(BusinessException.class, () -> service.findPriestById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.PRIEST, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findPriestById(99L));
    }

    @Test
    void shouldUpdateAndDeletePriest() {
        Priest entity = priest(1L, "old-password");
        PriestResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.PRIEST, ENTITY_LABEL)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(personMinistryCommandService.save(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updatePriest(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        service.deletePriestById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.PRIEST, ENTITY_LABEL);
    }

    @Test
    void shouldListPriestsUsingPersonMinistryWithoutCallingLegacyRepository() {
        Priest priest = priest(1L, "encoded-password");
        Reader readerWithPriestMinistry = reader(2L, "encoded-password");
        List<Person> people = List.of(priest, readerWithPriestMinistry);
        List<PriestResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.PRIEST);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreservePriestOrderReturnedByPersonMinistryReadService() {
        Reader readerWithPriestMinistry = reader(2L, "encoded-password");
        Priest priest = priest(1L, "encoded-password");
        List<Person> people = List.of(readerWithPriestMinistry, priest);
        List<PriestResponseDTO> responses = List.of(response(2L), response(1L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());

        verify(mapper).toDtoPersonList(people);
        assertEquals(List.of(2L, 1L), people.stream().map(Person::getId).toList());
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllPriests()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingPriest() {
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.PRIEST, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updatePriest(99L, request()));

        doThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.PRIEST, ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deletePriestById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedPriest() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.PRIEST, ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deletePriestById(1L));
    }

    private PriestRequestDTO request() {
        return new PriestRequestDTO("Priest", "34999999995", BIRTHDAY, "raw-password");
    }

    private Priest priest(Long id, String password) {
        Priest priest = new Priest();
        priest.setId(id);
        priest.setName("Priest");
        priest.setPhoneNumber("34999999995");
        priest.setBirthdayDate(BIRTHDAY);
        priest.setPassword(password);
        return priest;
    }

    private PriestResponseDTO response(Long id) {
        return new PriestResponseDTO(id, "Priest", "34999999995", BIRTHDAY);
    }

    private Reader reader(Long id, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }
}
