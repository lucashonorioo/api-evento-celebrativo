package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.ReaderServiceImpl;
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
class ReaderServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final String ENTITY_LABEL = "Leitor";

    @Mock
    private ReaderMapper readerMapper;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private ReaderServiceImpl service;

    @Test
    void shouldCreateReaderAndProvisionAccessAfterPersisting() {
        ReaderRequestDTO request = request();
        Person entity = reader(null);
        Person saved = reader(1L);
        ReaderResponseDTO response = response(1L);

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.READER)).thenReturn(saved);
        when(readerMapper.toDtoFromPerson(saved)).thenReturn(response);

        ReaderResponseDTO result = service.createReader(request);

        assertSame(response, result);
        verify(personMinistryCommandService).create(entity, MinistryType.READER);
        verify(personAccountCoordinator).provisionAccess(saved, request);
    }

    @Test
    void shouldPropagateProvisioningFailureAfterPersonAlreadyCreated() {
        ReaderRequestDTO request = request();
        Person entity = reader(null);
        Person saved = reader(1L);

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.READER)).thenReturn(saved);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(personAccountCoordinator).provisionAccess(saved, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createReader(request));
        verify(personMinistryCommandService).create(entity, MinistryType.READER);
    }

    @Test
    void shouldFindReaderByIdWhenExists() {
        Person reader = reader(1L);
        ReaderResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL)).thenReturn(reader);
        when(readerMapper.toDtoFromPerson(reader)).thenReturn(response);

        assertSame(response, service.findReaderById(1L));
    }

    @Test
    void shouldThrowResourceNotFoundWhenReaderIdDoesNotExist() {
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.READER, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));

        assertThrows(ResourceNotFoundException.class, () -> service.findReaderById(99L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenReaderIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findReaderById(-1L))
        );
    }

    @Test
    void shouldListReadersUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person reader = reader(1L);
        Person commentatorWithReaderMinistry = commentator(2L);
        List<Person> people = List.of(reader, commentatorWithReaderMinistry);
        List<ReaderResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.READER)).thenReturn(people);
        when(readerMapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.READER);
        verify(readerMapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveReadOrderReturnedByPersonMinistryReadService() {
        Person commentatorWithReaderMinistry = commentator(2L);
        Person reader = reader(1L);
        List<Person> people = List.of(commentatorWithReaderMinistry, reader);
        List<ReaderResponseDTO> responses = List.of(response(2L), response(1L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.READER)).thenReturn(people);
        when(readerMapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllReaders());

        verify(readerMapper).toDtoPersonList(people);
        assertEquals(List.of(2L, 1L), people.stream().map(Person::getId).toList());
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.READER)).thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllReaders()));
        verifyNoInteractions(personMinistryCommandService, readerMapper);
    }

    @Test
    void shouldUpdateReaderWhenExists() {
        ReaderUpdateRequestDTO request = updateRequest();
        Person entity = reader(1L);
        Person saved = reader(1L);
        ReaderResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.READER, ENTITY_LABEL)).thenReturn(entity);
        when(personCadastralUpdateService.updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        )).thenReturn(saved);
        when(readerMapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.updateReader(1L, request));
        verify(personCadastralUpdateService).updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        );
        verifyNoInteractions(personAccountCoordinator);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingReader() {
        ReaderUpdateRequestDTO request = updateRequest();
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(99L, MinistryType.READER, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));

        assertThrows(ResourceNotFoundException.class, () -> service.updateReader(99L, request));
    }

    @Test
    void shouldDeleteReaderWhenExists() {
        service.deleteReaderById(1L);

        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingReader() {
        doThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.READER, ENTITY_LABEL);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteReaderById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedReader() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deleteReaderById(1L));
    }

    private ReaderRequestDTO request() {
        return new ReaderRequestDTO("Reader", "34999999991", BIRTHDAY, "raw-password");
    }

    private ReaderUpdateRequestDTO updateRequest() {
        return new ReaderUpdateRequestDTO("Reader Updated", "34888888881", LocalDate.of(1991, 2, 11));
    }

    private Person reader(Long id) {
        Person reader = new Person("Reader", "34999999991", BIRTHDAY);
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }

    private ReaderResponseDTO response(Long id) {
        return new ReaderResponseDTO(id, "Reader", "34999999991", BIRTHDAY);
    }

    private Person commentator(Long id) {
        Person commentator = new Person("Commentator", "34999999992", BIRTHDAY);
        ReflectionTestUtils.setField(commentator, "id", id);
        return commentator;
    }
}
