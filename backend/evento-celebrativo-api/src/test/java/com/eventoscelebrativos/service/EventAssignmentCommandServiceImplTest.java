package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.impl.EventAssignmentCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAssignmentCommandServiceImplTest {

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private EventParticipationResponseService eventParticipationResponseService;

    @InjectMocks
    private EventAssignmentCommandServiceImpl service;

    @Test
    void shouldHandleEventWithoutAssignments() {
        CelebrationEvent event = event(1L);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of());

        service.synchronizeAssignments(event, List.of());

        verify(eventAssignmentRepository).findAllByEventId(1L);
        verify(eventAssignmentRepository, never()).saveAll(anyCollection());
        verify(eventAssignmentRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void shouldCreateAssignmentsForAllTypes() {
        CelebrationEvent event = event(1L);
        Person priest = person(new Person(), 10L);
        Person reader = person(new Person(), 11L);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of());

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(priest, EventAssignmentType.PRIEST),
                new EventAssignmentTarget(reader, EventAssignmentType.READER)
        ));

        ArgumentCaptor<Collection<EventAssignment>> captor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(captor.capture());
        assertEquals(List.of(EventAssignmentType.PRIEST, EventAssignmentType.READER),
                captor.getValue().stream().map(EventAssignment::getAssignmentType).toList());
        assertTrue(captor.getValue().stream().allMatch(assignment -> assignment.getEvent() == event));
    }

    @Test
    void shouldBeIdempotentAndPreserveExistingAssignment() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment existing = assignment(100L, event, reader, EventAssignmentType.READER);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        existing.setCreatedAt(createdAt);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(existing));

        service.synchronizeAssignments(event, List.of(new EventAssignmentTarget(reader, EventAssignmentType.READER)));

        assertEquals(100L, existing.getId());
        assertEquals(createdAt, existing.getCreatedAt());
        verify(eventAssignmentRepository, never()).saveAll(anyCollection());
        verify(eventAssignmentRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void shouldAddAndRemoveOnlyChangedAssignments() {
        CelebrationEvent event = event(1L);
        Person kept = person(new Person(), 11L);
        Person removed = person(new Person(), 12L);
        Person added = person(new Person(), 13L);
        EventAssignment keptAssignment = assignment(100L, event, kept, EventAssignmentType.READER);
        EventAssignment removedAssignment = assignment(101L, event, removed, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(keptAssignment, removedAssignment));

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(kept, EventAssignmentType.READER),
                new EventAssignmentTarget(added, EventAssignmentType.READER)
        ));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(13L), saveCaptor.getValue().stream().map(assignment -> assignment.getPerson().getId()).toList());
        assertEquals(List.of(12L), deleteCaptor.getValue().stream().map(assignment -> assignment.getPerson().getId()).toList());
        assertEquals(100L, keptAssignment.getId());
    }

    @Test
    void shouldReplacePriestByRemovingOldAndCreatingNew() {
        CelebrationEvent event = event(1L);
        Person oldPriest = person(new Person(), 10L);
        Person newPriest = person(new Person(), 20L);
        EventAssignment oldAssignment = assignment(100L, event, oldPriest, EventAssignmentType.PRIEST);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(oldAssignment));

        service.synchronizeAssignments(event, List.of(new EventAssignmentTarget(newPriest, EventAssignmentType.PRIEST)));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(20L), saveCaptor.getValue().stream().map(assignment -> assignment.getPerson().getId()).toList());
        assertEquals(List.of(10L), deleteCaptor.getValue().stream().map(assignment -> assignment.getPerson().getId()).toList());
    }

    @Test
    void shouldRemoveOldPairAndCreateNewPairWhenSamePersonChangesFunction() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment existing = assignment(100L, event, reader, EventAssignmentType.READER);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        existing.setCreatedAt(createdAt);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(existing));

        service.synchronizeAssignments(event, List.of(new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(EventAssignmentType.COMMENTATOR),
                saveCaptor.getValue().stream().map(EventAssignment::getAssignmentType).toList());
        assertTrue(deleteCaptor.getValue().contains(existing));
    }

    @Test
    void shouldAddSecondFunctionForSamePersonWithoutRemovingTheFirst() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment existingReaderAssignment = assignment(100L, event, reader, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(existingReaderAssignment));

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.READER),
                new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)
        ));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        verify(eventAssignmentRepository, never()).deleteAll(anyCollection());
        assertEquals(List.of(EventAssignmentType.COMMENTATOR),
                saveCaptor.getValue().stream().map(EventAssignment::getAssignmentType).toList());
        assertEquals(100L, existingReaderAssignment.getId());
    }

    @Test
    void shouldRemoveOnlyOneFunctionAndPreserveTheOtherWhenPersonHasTwoFunctions() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment readerAssignment = assignment(100L, event, reader, EventAssignmentType.READER);
        EventAssignment commentatorAssignment = assignment(101L, event, reader, EventAssignmentType.COMMENTATOR);
        when(eventAssignmentRepository.findAllByEventId(1L))
                .thenReturn(List.of(readerAssignment, commentatorAssignment));

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)
        ));

        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        verify(eventAssignmentRepository, never()).saveAll(anyCollection());
        assertTrue(deleteCaptor.getValue().contains(readerAssignment));
        assertEquals(1, deleteCaptor.getValue().size());
    }

    @Test
    void shouldRemoveAllAssignmentsWhenTargetsAreEmpty() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment assignment = assignment(100L, event, reader, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(assignment));

        service.synchronizeAssignments(event, List.of());

        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(11L), deleteCaptor.getValue().stream().map(item -> item.getPerson().getId()).toList());
        verify(eventAssignmentRepository, never()).saveAll(anyCollection());
    }

    @Test
    void shouldAllowSamePersonInTwoDifferentFunctionsInTheSameTarget() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of());

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.READER),
                new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)
        ));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        assertEquals(List.of(EventAssignmentType.READER, EventAssignmentType.COMMENTATOR),
                saveCaptor.getValue().stream().map(EventAssignment::getAssignmentType).toList());
    }

    @Test
    void shouldRejectRepeatedPersonAndTypePairBeforeSaving() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);

        assertThrows(BusinessException.class, () -> service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.READER),
                new EventAssignmentTarget(reader, EventAssignmentType.READER)
        )));

        verifyNoMoreInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRejectInvalidEventOrTarget() {
        Person reader = person(new Person(), 11L);

        assertThrows(BusinessException.class, () -> service.synchronizeAssignments(event(null), List.of()));
        assertThrows(BusinessException.class, () -> service.synchronizeAssignments(event(1L), List.of(
                new EventAssignmentTarget(reader, null)
        )));
    }

    @Test
    void shouldDeleteAllForEvent() {
        service.deleteAllForEvent(1L);

        verify(eventAssignmentRepository).deleteAllByEventId(1L);
    }

    @Test
    void shouldRejectInvalidEventIdWhenDeleting() {
        assertThrows(BusinessException.class, () -> service.deleteAllForEvent(null));
        assertThrows(BusinessException.class, () -> service.deleteAllForEvent(0L));
        verifyNoMoreInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRetainParticipationResponsesOnlyForPeopleStillAssignedAfterSync() {
        CelebrationEvent event = event(1L);
        Person kept = person(new Person(), 11L);
        Person removed = person(new Person(), 12L);
        Person added = person(new Person(), 13L);
        EventAssignment keptAssignment = assignment(100L, event, kept, EventAssignmentType.READER);
        EventAssignment removedAssignment = assignment(101L, event, removed, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(keptAssignment, removedAssignment));

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(kept, EventAssignmentType.READER),
                new EventAssignmentTarget(added, EventAssignmentType.READER)
        ));

        ArgumentCaptor<Collection<Long>> personIdsCaptor = personIdCollectionCaptor();
        verify(eventParticipationResponseService).retainOnlyForPersonIds(eq(1L), personIdsCaptor.capture());
        assertEquals(Set.of(11L, 13L), java.util.Set.copyOf(personIdsCaptor.getValue()));
    }

    @Test
    void shouldPreservePersonWithTwoFunctionsWhenOnlyOneFunctionRemains() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment readerAssignment = assignment(100L, event, reader, EventAssignmentType.READER);
        EventAssignment commentatorAssignment = assignment(101L, event, reader, EventAssignmentType.COMMENTATOR);
        when(eventAssignmentRepository.findAllByEventId(1L))
                .thenReturn(List.of(readerAssignment, commentatorAssignment));

        service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)
        ));

        ArgumentCaptor<Collection<Long>> personIdsCaptor = personIdCollectionCaptor();
        verify(eventParticipationResponseService).retainOnlyForPersonIds(eq(1L), personIdsCaptor.capture());
        assertEquals(Set.of(11L), java.util.Set.copyOf(personIdsCaptor.getValue()));
    }

    @Test
    void shouldRetainEmptyPersonSetWhenAllAssignmentsAreRemoved() {
        CelebrationEvent event = event(1L);
        Person reader = person(new Person(), 11L);
        EventAssignment assignment = assignment(100L, event, reader, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(assignment));

        service.synchronizeAssignments(event, List.of());

        verify(eventParticipationResponseService).retainOnlyForPersonIds(1L, Set.of());
    }

    private CelebrationEvent event(Long id) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(id);
        return event;
    }

    private <T extends Person> T person(T person, Long id) {
        person.setId(id);
        person.setName("Person " + id);
        person.setPhoneNumber("34975" + String.format("%06d", id));
        return person;
    }

    private EventAssignment assignment(Long id, CelebrationEvent event, Person person, EventAssignmentType type) {
        EventAssignment assignment = new EventAssignment(event, person, type);
        assignment.setId(id);
        return assignment;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<EventAssignment>> collectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<Long>> personIdCollectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }
}
