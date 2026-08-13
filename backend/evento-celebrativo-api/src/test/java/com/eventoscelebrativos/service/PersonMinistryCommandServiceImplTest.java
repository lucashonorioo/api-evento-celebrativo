package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.PastorPriestMinistryRequiredException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @InjectMocks
    private PersonMinistryCommandServiceImpl service;

    @Test
    void shouldCreatePersonAndMinistryAtomically() {
        Person reader = reader(null);
        Person saved = reader(1L);
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
        Person reader = reader(null);
        Person saved = reader(1L);
        when(personRepository.save(reader)).thenReturn(saved);
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThrows(DatabaseException.class, () -> service.create(reader, MinistryType.READER));
    }

    @Test
    void shouldReturnPersonWhenMinistryIsActive() {
        Person reader = reader(1L);
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExist() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(99L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsAbsent() {
        Person reader = reader(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPerson(1L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldThrowResourceNotFoundWhenMinistryIsInactive() {
        Person reader = reader(1L);
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
    void shouldLockPersonWithFindByIdForUpdateWhenRequiringActiveMinistryPersonForUpdate() {
        Person reader = reader(1L);
        PersonMinistry ministry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(ministry));

        assertSame(reader, service.requireActiveMinistryPersonForUpdate(1L, MinistryType.READER, ENTITY_LABEL));

        verify(personRepository).findByIdForUpdate(1L);
        verify(personRepository, never()).findById(anyLong());
    }

    @Test
    void shouldThrowResourceNotFoundWhenPersonDoesNotExistForUpdate() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.requireActiveMinistryPersonForUpdate(99L, MinistryType.READER, ENTITY_LABEL));
    }

    @Test
    void shouldRejectInvalidIdOnRequireActiveMinistryPersonForUpdate() {
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPersonForUpdate(null, MinistryType.READER, ENTITY_LABEL));
        assertThrows(BusinessException.class, () -> service.requireActiveMinistryPersonForUpdate(0L, MinistryType.READER, ENTITY_LABEL));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldDeactivateOnlyTheRequestedMinistryWhenNoAssignmentConflictExists() {
        Person reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
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
        Person reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
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
        Person reader = reader(1L);
        PersonMinistry readerMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.READER))
                .thenReturn(Optional.of(readerMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.READER, ENTITY_LABEL);

        assertFalse(readerMinistry.getActive());
    }

    @Test
    void shouldThrowResourceNotFoundWhenRemovingMissingOrInactiveMinistry() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.removeMinistry(99L, MinistryType.READER, ENTITY_LABEL));
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldBlockPriestRemovalWhenPersonIsActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = new PersonMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.PRIEST))
                .thenReturn(Optional.of(priestMinistry));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(true);

        assertThrows(PastorPriestMinistryRequiredException.class,
                () -> service.removeMinistry(1L, MinistryType.PRIEST, "Padre"));

        assertTrue(priestMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldAllowPriestRemovalWhenPersonIsNotActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = new PersonMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        when(personMinistryRepository.findByPersonIdAndMinistryType(1L, MinistryType.PRIEST))
                .thenReturn(Optional.of(priestMinistry));
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(false);
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.PRIEST))
                .thenReturn(false);

        service.removeMinistry(1L, MinistryType.PRIEST, "Padre");

        assertFalse(priestMinistry.getActive());
        verify(personMinistryRepository).save(priestMinistry);
    }

    @Test
    void shouldAddNewMinistriesWhenPersonHasNone() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER, MinistryType.COMMENTATOR));

        assertSame(reader, result.person());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), result.activeMinistries());
        assertEquals(Set.of(MinistryType.READER, MinistryType.COMMENTATOR), result.added());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        assertTrue(result.unchanged().isEmpty());
        ArgumentCaptor<PersonMinistry> captor = ArgumentCaptor.forClass(PersonMinistry.class);
        verify(personMinistryRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(pm -> pm.getPerson() == reader));
    }

    @Test
    void shouldReactivateInactiveMinistryDuringSync() {
        Person reader = reader(1L);
        PersonMinistry inactiveMinistry = new PersonMinistry(reader, MinistryType.READER);
        inactiveMinistry.setActive(false);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(inactiveMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.reactivated());
        assertTrue(inactiveMinistry.getActive());
        verify(personMinistryRepository).save(inactiveMinistry);
    }

    @Test
    void shouldDeactivateActiveMinistryAbsentFromDesiredSetDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertEquals(Set.of(MinistryType.READER), result.deactivated());
        assertTrue(result.activeMinistries().isEmpty());
        assertFalse(activeMinistry.getActive());
        verify(personMinistryRepository).save(activeMinistry);
    }

    @Test
    void shouldPreserveUnchangedMinistryDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of(MinistryType.READER));

        assertEquals(Set.of(MinistryType.READER), result.unchanged());
        assertTrue(result.added().isEmpty());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldApplyAllFourCategoriesInSingleSync() {
        Person reader = reader(1L);
        PersonMinistry unchangedReader = new PersonMinistry(reader, MinistryType.READER);
        PersonMinistry reactivatedCommentator = new PersonMinistry(reader, MinistryType.COMMENTATOR);
        reactivatedCommentator.setActive(false);
        PersonMinistry deactivatedPriest = new PersonMinistry(reader, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L))
                .thenReturn(List.of(unchangedReader, reactivatedCommentator, deactivatedPriest));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.PRIEST))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(
                1L,
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR, MinistryType.EUCHARISTIC_MINISTER)
        );

        assertEquals(Set.of(MinistryType.EUCHARISTIC_MINISTER), result.added());
        assertEquals(Set.of(MinistryType.COMMENTATOR), result.reactivated());
        assertEquals(Set.of(MinistryType.PRIEST), result.deactivated());
        assertEquals(Set.of(MinistryType.READER), result.unchanged());
        assertEquals(
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR, MinistryType.EUCHARISTIC_MINISTER),
                result.activeMinistries()
        );
        assertTrue(reactivatedCommentator.getActive());
        assertFalse(deactivatedPriest.getActive());
    }

    @Test
    void shouldBlockSyncWhenDeactivatingPriestOfActivePastor() {
        Person priest = reader(1L);
        PersonMinistry priestMinistry = new PersonMinistry(priest, MinistryType.PRIEST);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(priest));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(priestMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.PRIEST))
                .thenReturn(false);
        when(parishStaffAssignmentRepository.existsByPersonIdAndResponsibilityAndActiveTrue(1L, ParishResponsibilityType.PASTOR))
                .thenReturn(true);

        assertThrows(PastorPriestMinistryRequiredException.class, () -> service.syncMinistries(1L, Set.of()));

        assertTrue(priestMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBlockSyncAndApplyNoMutationWhenDeactivationConflictsWithEventAssignment() {
        Person reader = reader(1L);
        PersonMinistry activeReaderMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeReaderMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(true);

        assertThrows(DatabaseException.class, () -> service.syncMinistries(1L, Set.of()));

        assertTrue(activeReaderMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldBlockEntireSyncWhenAnyDeactivationConflictsEvenIfOtherChangesWouldSucceed() {
        Person reader = reader(1L);
        PersonMinistry activeReaderMinistry = new PersonMinistry(reader, MinistryType.READER);
        PersonMinistry activeCommentatorMinistry = new PersonMinistry(reader, MinistryType.COMMENTATOR);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L))
                .thenReturn(List.of(activeReaderMinistry, activeCommentatorMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(true);
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.COMMENTATOR))
                .thenReturn(false);

        assertThrows(DatabaseException.class,
                () -> service.syncMinistries(1L, Set.of(MinistryType.EUCHARISTIC_MINISTER)));

        assertTrue(activeReaderMinistry.getActive());
        assertTrue(activeCommentatorMinistry.getActive());
        verify(personMinistryRepository, never()).save(any());
    }

    @Test
    void shouldNotBlockSyncWhenConflictingAssignmentIsOfADifferentTypeDuringSync() {
        Person reader = reader(1L);
        PersonMinistry activeMinistry = new PersonMinistry(reader, MinistryType.READER);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of(activeMinistry));
        when(eventAssignmentRepository.existsByPersonIdAndAssignmentType(1L, EventAssignmentType.READER))
                .thenReturn(false);

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertEquals(Set.of(MinistryType.READER), result.deactivated());
        assertFalse(activeMinistry.getActive());
    }

    @Test
    void shouldTreatEmptyDesiredSetAndNoExistingMinistriesAsNoOp() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());

        PersonMinistrySyncResult result = service.syncMinistries(1L, Set.of());

        assertTrue(result.activeMinistries().isEmpty());
        assertTrue(result.added().isEmpty());
        assertTrue(result.reactivated().isEmpty());
        assertTrue(result.deactivated().isEmpty());
        assertTrue(result.unchanged().isEmpty());
        verify(personMinistryRepository, never()).save(any());
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRejectSyncWithInvalidId() {
        assertThrows(BusinessException.class, () -> service.syncMinistries(null, Set.of(MinistryType.READER)));
        assertThrows(BusinessException.class, () -> service.syncMinistries(0L, Set.of(MinistryType.READER)));
        assertThrows(BusinessException.class, () -> service.syncMinistries(-1L, Set.of(MinistryType.READER)));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldRejectSyncWithNullDesiredSet() {
        assertThrows(BusinessException.class, () -> service.syncMinistries(1L, null));
        verifyNoInteractions(personRepository);
    }

    @Test
    void shouldThrowResourceNotFoundWhenSyncingMinistriesOfMissingPerson() {
        when(personRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.syncMinistries(99L, Set.of(MinistryType.READER)));
        verifyNoInteractions(personMinistryRepository, eventAssignmentRepository);
    }

    @Test
    void shouldTranslateConstraintViolationOnSyncAddToDatabaseException() {
        Person reader = reader(1L);
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reader));
        when(personMinistryRepository.findAllByPersonId(1L)).thenReturn(List.of());
        when(personMinistryRepository.save(any(PersonMinistry.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertThrows(DatabaseException.class, () -> service.syncMinistries(1L, Set.of(MinistryType.READER)));
    }

    private Person reader(Long id) {
        Person reader = new Person();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        return reader;
    }
}
