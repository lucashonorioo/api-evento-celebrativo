package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.request.PriestUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.PriestServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriestServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1980, 5, 14);
    private static final String ENTITY_LABEL = "Padre";

    @Mock
    private PriestMapper mapper;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private PriestServiceImpl service;

    @Test
    void shouldCreatePriestAndProvisionAccessAfterPersisting() {
        PriestRequestDTO request = request();
        Person entity = priest(null);
        Person saved = priest(1L);
        PriestResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.PRIEST)).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createPriest(request));

        verify(personMinistryCommandService).create(entity, MinistryType.PRIEST);
        verify(personAccountCoordinator).provisionAccess(saved, request);
    }

    @Test
    void shouldPropagateProvisioningFailureAfterPersonAlreadyCreated() {
        PriestRequestDTO request = request();
        Person entity = priest(null);
        Person saved = priest(1L);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.PRIEST)).thenReturn(saved);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(personAccountCoordinator).provisionAccess(saved, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createPriest(request));
        verify(personMinistryCommandService).create(entity, MinistryType.PRIEST);
    }

    @Test
    void shouldFindPriestByIdWhenExists() {
        Person entity = priest(1L);
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
        Person entity = priest(1L);
        PriestResponseDTO response = response(1L);
        PriestUpdateRequestDTO request = updateRequest();

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.PRIEST, ENTITY_LABEL)).thenReturn(entity);
        when(personCadastralUpdateService.updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        )).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updatePriest(1L, request));
        verify(personCadastralUpdateService).updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        );
        verifyNoInteractions(personAccountCoordinator);

        service.deletePriestById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.PRIEST, ENTITY_LABEL);
    }

    @Test
    void shouldListPriestsUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person priest = priest(1L);
        Person readerWithPriestMinistry = reader(2L);
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
        Person readerWithPriestMinistry = reader(2L);
        Person priest = priest(1L);
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
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(99L, MinistryType.PRIEST, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updatePriest(99L, updateRequest()));

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

    private PriestUpdateRequestDTO updateRequest() {
        return new PriestUpdateRequestDTO("Priest Updated", "34888888885", LocalDate.of(1981, 6, 15));
    }

    private Person priest(Long id) {
        Person priest = new Person("Priest", "34999999995", BIRTHDAY);
        ReflectionTestUtils.setField(priest, "id", id);
        return priest;
    }

    private PriestResponseDTO response(Long id) {
        return new PriestResponseDTO(id, "Priest", "34999999995", BIRTHDAY);
    }

    private Person reader(Long id) {
        Person reader = new Person("Reader", "34999999991", BIRTHDAY);
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }
}
