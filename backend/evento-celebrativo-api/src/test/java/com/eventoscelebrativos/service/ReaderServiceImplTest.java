package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.ReaderServiceImpl;
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
class ReaderServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final String ENTITY_LABEL = "Leitor";

    @Mock
    private ReaderMapper readerMapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @InjectMocks
    private ReaderServiceImpl service;

    @Test
    void shouldCreateReaderWithEncryptedPasswordAndOperatorRole() {
        ReaderRequestDTO request = request();
        Person entity = reader(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Person saved = reader(1L, "encoded-password");
        saved.addRole(operatorRole);
        ReaderResponseDTO response = response(1L);

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(personMinistryCommandService.create(any(Person.class), eq(MinistryType.READER))).thenReturn(saved);
        when(readerMapper.toDtoFromPerson(saved)).thenReturn(response);

        ReaderResponseDTO result = service.createReader(request);

        assertSame(response, result);
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMinistryCommandService).create(captor.capture(), eq(MinistryType.READER));
        Person readerToSave = captor.getValue();
        assertEquals("encoded-password", readerToSave.getPassword());
        assertNotEquals("raw-password", readerToSave.getPassword());
        assertTrue(readerToSave.hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        ReaderRequestDTO request = request();
        Person entity = reader(null, "raw-password");

        when(readerMapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createReader(request));
        verify(personMinistryCommandService, never()).create(any(), any());
    }

    @Test
    void shouldFindReaderByIdWhenExists() {
        Person reader = reader(1L, "encoded-password");
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
        Person reader = reader(1L, "encoded-password");
        Person commentatorWithReaderMinistry = commentator(2L, "encoded-password");
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
        Person commentatorWithReaderMinistry = commentator(2L, "encoded-password");
        Person reader = reader(1L, "encoded-password");
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
        ReaderRequestDTO request = request();
        Person entity = reader(1L, "old-password");
        Person saved = reader(1L, "encoded-password");
        ReaderResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(personMinistryCommandService.save(entity)).thenReturn(saved);
        when(readerMapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.updateReader(1L, request));
        verify(readerMapper).updateReaderFromDto(request, entity);
        assertEquals("encoded-password", entity.getPassword());
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingReader() {
        ReaderRequestDTO request = request();
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.READER, ENTITY_LABEL))
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

    private Person reader(Long id, String password) {
        Person reader = new Person();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }

    private ReaderResponseDTO response(Long id) {
        return new ReaderResponseDTO(id, "Reader", "34999999991", BIRTHDAY);
    }

    private Person commentator(Long id, String password) {
        Person commentator = new Person();
        commentator.setId(id);
        commentator.setName("Commentator");
        commentator.setPhoneNumber("34999999992");
        commentator.setBirthdayDate(BIRTHDAY);
        commentator.setPassword(password);
        return commentator;
    }
}
