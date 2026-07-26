package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.PersonMinistryCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonMinistryCommandServiceImplTest {

    private static final String ENTITY_LABEL = "Leitor";

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @InjectMocks
    private PersonMinistryCommandServiceImpl service;

    @Test
    void shouldCreatePersonAndMinistryAtomically() {
        Reader reader = reader(null);
        Reader saved = reader(1L);
        when(personRepository.save(reader)).thenReturn(saved);

        Person result = service.create(reader, MinistryType.READER);

        assertSame(saved, result);
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository).save(captor.capture());
        assertSame(saved, captor.getValue().getPerson());
        assertEquals(MinistryType.READER, captor.getValue().getMinistryType());
    }

    @Test
    void shouldRejectCreateWithMissingArguments() {
        assertThrows(BusinessException.class, () -> service.create(null, MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.create(reader(1L), null));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldTranslateMinistryConstraintViolationOnCreateToDatabaseException() {
        Reader reader = reader(null);
        Reader saved = reader(1L);
        when(personRepository.save(reader)).thenReturn(saved);
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThrows(DatabaseException.class, () -> service.create(reader, MinistryType.READER));
    }

    @Test
    void shouldReturnPersonWhenMinistryIsActive() {
        Reader reader = reader(1L);
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldAcceptDivergentLegacySubtypeWhenMinistryIsActive() {
        Commentator commentatorWithReaderMinistry = new Commentator();
        commentatorWithReaderMinistry.setId(1L);
        PersonMinistry readerMinistry = new PersonMinistry(commentatorWithReaderMinistry, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(commentatorWithReaderMinistry));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(readerMinistry));

        assertSame(commentatorWithReaderMinistry, service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(99L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsAbsent() {
        Reader reader = reader(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsInactive() {
        Reader reader = reader(1L);
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        ministry.setActive(false);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(ministry));

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldRejectInvalidIdOnRequireActiveMinistryPerson() {
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(null, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(0L, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPerson(-1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldDelegateSaveToPersonRepository() {
        Reader reader = reader(1L);
        when(personRepository.save(reader)).thenReturn(reader);

        assertSame(reader, service.save(reader));
        verify(personRepository).save(reader);
    }

    @Test
    void shouldDeactivateOnlyTheRequestedMinistryWhenNoAssignmentConflictExists() {
        Reader reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(readerMinistry.getActive());
        verify(personMinistryRepository).save(readerMinistry);
        verify(personRepository, never()).save(any());
        verify(personRepository, never()).delete(any());
        verify(personRepository, never()).deleteById(any());
    }

    @Test
    void shouldBlockRemovalWhenPersonHasEventAssignmentOfSameType() {
        Reader reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(true);

        assertThrows(DatabaseException.class, () -> service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL));

        assertTrue(readerMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldNotBlockRemovalWhenAssignmentIsOfADifferentType() {
        Reader reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(readerMinistry.getActive());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRemovingMissingOrInactiveMinistry() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.removeMinistry(99L, MinistryType.READER, ENTITY_LABEL));
        verifyNoInteractions(eventAssignmentRepository);
    }

    private Reader reader(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setPassword("encoded-password");
        return reader;
    }
}
