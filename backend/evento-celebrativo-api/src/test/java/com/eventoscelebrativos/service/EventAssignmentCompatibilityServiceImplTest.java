package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.impl.EventAssignmentCompatibilityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAssignmentCompatibilityServiceImplTest {

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @InjectMocks
    private EventAssignmentCompatibilityServiceImpl service;

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
        Priest priest = person(new Priest(), 10L);
        Reader reader = person(new Reader(), 11L);
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
        Reader reader = person(new Reader(), 11L);
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
        Reader kept = person(new Reader(), 11L);
        Reader removed = person(new Reader(), 12L);
        Reader added = person(new Reader(), 13L);
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
        Priest oldPriest = person(new Priest(), 10L);
        Priest newPriest = person(new Priest(), 20L);
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
    void shouldUpdateTypeWhenSamePersonChangesFunction() {
        CelebrationEvent event = event(1L);
        Reader reader = person(new Reader(), 11L);
        EventAssignment existing = assignment(100L, event, reader, EventAssignmentType.READER);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        existing.setCreatedAt(createdAt);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(existing));

        service.synchronizeAssignments(event, List.of(new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)));

        ArgumentCaptor<Collection<EventAssignment>> saveCaptor = collectionCaptor();
        verify(eventAssignmentRepository).saveAll(saveCaptor.capture());
        assertSame(existing, saveCaptor.getValue().iterator().next());
        assertEquals(100L, existing.getId());
        assertEquals(createdAt, existing.getCreatedAt());
        assertEquals(EventAssignmentType.COMMENTATOR, existing.getAssignmentType());
    }

    @Test
    void shouldRemoveAllAssignmentsWhenTargetsAreEmpty() {
        CelebrationEvent event = event(1L);
        Reader reader = person(new Reader(), 11L);
        EventAssignment assignment = assignment(100L, event, reader, EventAssignmentType.READER);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of(assignment));

        service.synchronizeAssignments(event, List.of());

        ArgumentCaptor<Collection<EventAssignment>> deleteCaptor = collectionCaptor();
        verify(eventAssignmentRepository).deleteAll(deleteCaptor.capture());
        assertEquals(List.of(11L), deleteCaptor.getValue().stream().map(item -> item.getPerson().getId()).toList());
        verify(eventAssignmentRepository, never()).saveAll(anyCollection());
    }

    @Test
    void shouldRejectRepeatedPersonBeforeSaving() {
        CelebrationEvent event = event(1L);
        Reader reader = person(new Reader(), 11L);

        assertThrows(BusinessException.class, () -> service.synchronizeAssignments(event, List.of(
                new EventAssignmentTarget(reader, EventAssignmentType.READER),
                new EventAssignmentTarget(reader, EventAssignmentType.COMMENTATOR)
        )));

        verifyNoMoreInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldRejectInvalidEventOrTarget() {
        Reader reader = person(new Reader(), 11L);

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
    void shouldNotUseIndividualPersonAssignmentQueries() {
        CelebrationEvent event = event(1L);
        Reader reader = person(new Reader(), 11L);
        when(eventAssignmentRepository.findAllByEventId(1L)).thenReturn(List.of());

        service.synchronizeAssignments(event, List.of(new EventAssignmentTarget(reader, EventAssignmentType.READER)));

        verify(eventAssignmentRepository, never()).findByEventIdAndPersonId(1L, 11L);
        verify(eventAssignmentRepository, never()).existsByEventIdAndPersonId(1L, 11L);
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
}
