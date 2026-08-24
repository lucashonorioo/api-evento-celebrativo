package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.MinisterOfTheWordServiceImpl;
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
class MinisterOfTheWordServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1993, 4, 13);
    private static final String FIND_ENTITY_LABEL = "Ministro Da Palavra";
    private static final String MUTATION_ENTITY_LABEL = "Ministro da Palavra";

    @Mock
    private MinisterOfTheWordMapper mapper;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private MinisterOfTheWordServiceImpl service;

    @Test
    void shouldCreateMinisterOfTheWordAndProvisionAccessAfterPersisting() {
        MinisterOfTheWordRequestDTO request = request();
        Person entity = minister(null);
        Person saved = minister(1L);
        MinisterOfTheWordResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.MINISTER_OF_THE_WORD)).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createMinisterOfTheWord(request));

        verify(personMinistryCommandService).create(entity, MinistryType.MINISTER_OF_THE_WORD);
        verify(personAccountCoordinator).provisionAccess(saved, request);
    }

    @Test
    void shouldPropagateProvisioningFailureAfterPersonAlreadyCreated() {
        MinisterOfTheWordRequestDTO request = request();
        Person entity = minister(null);
        Person saved = minister(1L);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.MINISTER_OF_THE_WORD)).thenReturn(saved);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(personAccountCoordinator).provisionAccess(saved, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createMinisterOfTheWord(request));
        verify(personMinistryCommandService).create(entity, MinistryType.MINISTER_OF_THE_WORD);
    }

    @Test
    void shouldFindMinisterOfTheWordByIdWhenExists() {
        Person entity = minister(1L);
        MinisterOfTheWordResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.MINISTER_OF_THE_WORD, FIND_ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findMinisterOfTheWordById(1L));
    }

    @Test
    void shouldThrowWhenMinisterOfTheWordIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(null));
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.MINISTER_OF_THE_WORD, FIND_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(FIND_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findMinisterOfTheWordById(99L));
    }

    @Test
    void shouldUpdateAndDeleteMinisterOfTheWord() {
        Person entity = minister(1L);
        MinisterOfTheWordResponseDTO response = response(1L);
        MinisterOfTheWordUpdateRequestDTO request = updateRequest();

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL)).thenReturn(entity);
        when(personCadastralUpdateService.updateCadastral(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updateMinisterOfTheWord(1L, request));
        verify(mapper).updateMinisterOfTheWordFromDto(request, entity);
        verifyNoInteractions(personAccountCoordinator);

        service.deleteMinisterOfTheWord(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);
    }

    @Test
    void shouldListMinistersOfTheWordUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person readerWithMinisterOfTheWordMinistry = reader(2L);
        MinisterOfTheWordResponseDTO response = new MinisterOfTheWordResponseDTO(
                2L,
                "Minister",
                "34999999994",
                BIRTHDAY
        );
        List<Person> people = List.of(readerWithMinisterOfTheWordMinistry);
        List<MinisterOfTheWordResponseDTO> responses = List.of(response);

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllMinistersOfTheWord());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveMinisterOfTheWordOrderReturnedByPersonMinistryReadService() {
        Person first = minister(1L);
        Person second = reader(2L);
        List<Person> people = List.of(first, second);
        List<MinisterOfTheWordResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllMinistersOfTheWord());

        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllMinistersOfTheWord()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingMinisterOfTheWord() {
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(99L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateMinisterOfTheWord(99L, updateRequest()));

        doThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteMinisterOfTheWord(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedMinisterOfTheWord() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deleteMinisterOfTheWord(1L));
    }

    private MinisterOfTheWordRequestDTO request() {
        return new MinisterOfTheWordRequestDTO("Minister", "34999999994", BIRTHDAY, "raw-password");
    }

    private MinisterOfTheWordUpdateRequestDTO updateRequest() {
        return new MinisterOfTheWordUpdateRequestDTO("Minister", "34999999994", BIRTHDAY);
    }

    private Person minister(Long id) {
        Person minister = new Person("Minister", "34999999994", BIRTHDAY);
        ReflectionTestUtils.setField(minister, "id", id);
        return minister;
    }

    private Person reader(Long id) {
        Person reader = new Person("Minister", "34999999994", BIRTHDAY);
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }

    private MinisterOfTheWordResponseDTO response(Long id) {
        return new MinisterOfTheWordResponseDTO(id, "Minister", "34999999994", BIRTHDAY);
    }
}
