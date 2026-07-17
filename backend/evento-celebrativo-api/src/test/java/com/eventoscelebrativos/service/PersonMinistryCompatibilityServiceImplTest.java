package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.service.impl.PersonMinistryCompatibilityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonMinistryCompatibilityServiceImplTest {

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    @InjectMocks
    private PersonMinistryCompatibilityServiceImpl service;

    @Test
    void shouldCreateMinistryWhenItDoesNotExist() {
        Reader reader = reader(1L);
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.empty());
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonMinistry result = service.ensureMinistry(reader, MinistryType.READER);

        assertSame(reader, result.getPerson());
        assertEquals(MinistryType.READER, result.getMinistryType());
        assertTrue(result.getActive());
    }

    @Test
    void shouldKeepActiveMinistryWithoutDuplicating() {
        PersonMinistry existing = new PersonMinistry(reader(1L), MinistryType.READER);
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.ensureMinistry(existing.getPerson(), MinistryType.READER));

        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldReactivateInactiveMinistry() {
        PersonMinistry existing = new PersonMinistry(reader(1L), MinistryType.READER);
        existing.setActive(false);
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(existing));
        when(personMinistryRepository.save(existing)).thenReturn(existing);

        PersonMinistry result = service.ensureMinistry(existing.getPerson(), MinistryType.READER);

        assertTrue(result.getActive());
        verify(personMinistryRepository).save(existing);
    }

    @Test
    void shouldDeleteAllForPersonEvenWhenThereAreNoRows() {
        service.deleteAllForPerson(1L);

        verify(personMinistryRepository).deleteAllByPersonId(1L);
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(BusinessException.class, () -> service.ensureMinistry(null, MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.ensureMinistry(reader(null), MinistryType.READER));
        assertThrows(BusinessException.class, () -> service.ensureMinistry(reader(1L), null));
        assertThrows(BusinessException.class, () -> service.deleteAllForPerson(0L));
    }

    @Test
    void shouldPropagateRepositoryFailureWhenCreatingMinistry() {
        Reader reader = reader(1L);
        RuntimeException failure = new RuntimeException("database unavailable");
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.empty());
        when(personMinistryRepository.save(any(PersonMinistry.class))).thenThrow(failure);

        RuntimeException result = assertThrows(RuntimeException.class,
                () -> service.ensureMinistry(reader, MinistryType.READER));

        assertSame(failure, result);
    }

    @Test
    void shouldSaveNewMinistryWithExpectedPersonAndType() {
        Reader reader = reader(1L);
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.empty());

        service.ensureMinistry(reader, MinistryType.READER);

        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository).save(captor.capture());
        assertSame(reader, captor.getValue().getPerson());
        assertEquals(MinistryType.READER, captor.getValue().getMinistryType());
    }

    private Reader reader(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34971000009");
        reader.setPassword("encoded-password");
        return reader;
    }
}
