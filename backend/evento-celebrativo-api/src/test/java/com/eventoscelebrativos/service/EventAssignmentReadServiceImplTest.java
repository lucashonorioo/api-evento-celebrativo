package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.impl.EventAssignmentReadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAssignmentReadServiceImplTest {

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @InjectMocks
    private EventAssignmentReadServiceImpl service;

    @Test
    void shouldReadIndividualEventAssignmentsAsSnapshots() {
        when(eventAssignmentRepository.findAllByEventIdWithPerson(1L)).thenReturn(List.of(
                assignment(100L, 1L, person(new Reader(), 10L, "Reader", "reader"), EventAssignmentType.READER)
        ));

        List<EventAssignmentSnapshot> result = service.findAllByEventId(1L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).assignmentId());
        assertEquals(1L, result.get(0).eventId());
        assertEquals(10L, result.get(0).personId());
        assertEquals(EventAssignmentType.READER, result.get(0).assignmentType());
        assertEquals("Reader", result.get(0).personName());
        assertEquals("reader", result.get(0).personType());
    }

    @Test
    void shouldReturnEmptyListForEventWithoutAssignmentsOrNonExistingEvent() {
        when(eventAssignmentRepository.findAllByEventIdWithPerson(99L)).thenReturn(List.of());

        assertEquals(List.of(), service.findAllByEventId(99L));
    }

    @Test
    void shouldRejectInvalidEventId() {
        assertThrows(BusinessException.class, () -> service.findAllByEventId(null));
        assertThrows(BusinessException.class, () -> service.findAllByEventId(0L));
        verifyNoInteractions(eventAssignmentRepository);
    }

    @Test
    void shouldReturnEmptyMapForEmptyCollectionWithoutQuery() {
        Map<Long, List<EventAssignmentSnapshot>> result = service.findAllByEventIds(List.of());

        assertTrue(result.isEmpty());
        verify(eventAssignmentRepository, never()).findAllByEventIdInWithPerson(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void shouldReadSeveralEventsWithOneBatchQueryAndIncludeEmptyRequestedEvents() {
        EventAssignment secondEventAssignment = assignment(
                200L,
                2L,
                person(new Reader(), 20L, "Reader 20", "reader"),
                EventAssignmentType.READER
        );
        when(eventAssignmentRepository.findAllByEventIdInWithPerson(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(secondEventAssignment));

        Map<Long, List<EventAssignmentSnapshot>> result = service.findAllByEventIds(List.of(3L, 2L, 1L));

        assertEquals(List.of(1L, 2L, 3L), result.keySet().stream().toList());
        assertEquals(List.of(), result.get(1L));
        assertEquals(List.of(20L), result.get(2L).stream().map(EventAssignmentSnapshot::personId).toList());
        assertEquals(List.of(), result.get(3L));
        verify(eventAssignmentRepository).findAllByEventIdInWithPerson(List.of(1L, 2L, 3L));
    }

    @Test
    void shouldNormalizeRepeatedIdsBeforeBatchQuery() {
        when(eventAssignmentRepository.findAllByEventIdInWithPerson(List.of(1L, 2L))).thenReturn(List.of());

        service.findAllByEventIds(List.of(2L, 1L, 2L, 1L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(eventAssignmentRepository).findAllByEventIdInWithPerson(captor.capture());
        assertEquals(List.of(1L, 2L), captor.getValue().stream().toList());
    }

    @Test
    void shouldSortSnapshotsDeterministicallyAndGroupByFunction() {
        when(eventAssignmentRepository.findAllByEventIdWithPerson(1L)).thenReturn(List.of(
                assignment(102L, 1L, person(new Reader(), 12L, "Bruno", "reader"), EventAssignmentType.READER),
                assignment(100L, 1L, person(new Priest(), 10L, "Carlos", "priest"), EventAssignmentType.PRIEST),
                assignment(101L, 1L, person(new Reader(), 11L, "Ana", "reader"), EventAssignmentType.READER)
        ));

        List<EventAssignmentSnapshot> snapshots = service.findAllByEventId(1L);
        EventAssignmentGroup group = EventAssignmentGroup.from(1L, snapshots);

        assertEquals(List.of(EventAssignmentType.PRIEST, EventAssignmentType.READER, EventAssignmentType.READER),
                snapshots.stream().map(EventAssignmentSnapshot::assignmentType).toList());
        assertEquals(10L, group.priest().personId());
        assertEquals(List.of(11L, 12L), group.readers().stream().map(EventAssignmentSnapshot::personId).toList());
        assertTrue(group.commentators().isEmpty());
    }

    @Test
    void shouldProtectReturnedCollectionsFromModification() {
        when(eventAssignmentRepository.findAllByEventIdWithPerson(1L)).thenReturn(List.of());
        when(eventAssignmentRepository.findAllByEventIdInWithPerson(List.of(1L))).thenReturn(List.of());

        assertThrows(UnsupportedOperationException.class, () -> service.findAllByEventId(1L).add(null));
        Map<Long, List<EventAssignmentSnapshot>> result = service.findAllByEventIds(List.of(1L));
        assertThrows(UnsupportedOperationException.class, () -> result.put(2L, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> result.get(1L).add(null));
    }

    @Test
    void shouldRejectMultiplePriestsWhenGrouping() {
        List<EventAssignmentSnapshot> snapshots = List.of(
                snapshot(1L, 10L, EventAssignmentType.PRIEST),
                snapshot(1L, 11L, EventAssignmentType.PRIEST)
        );

        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, snapshots));
    }

    @Test
    void shouldRejectDuplicatedPersonWhenGrouping() {
        List<EventAssignmentSnapshot> snapshots = List.of(
                snapshot(1L, 10L, EventAssignmentType.READER),
                snapshot(1L, 10L, EventAssignmentType.COMMENTATOR)
        );

        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, snapshots));
    }

    @Test
    void shouldRejectMissingAssignmentTypeWhenGrouping() {
        List<EventAssignmentSnapshot> snapshots = List.of(
                snapshot(1L, 10L, null)
        );

        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, snapshots));
    }

    @Test
    void shouldRejectAssignmentFromAnotherEventWhenGrouping() {
        List<EventAssignmentSnapshot> snapshots = List.of(
                snapshot(2L, 10L, EventAssignmentType.READER)
        );

        assertThrows(BusinessException.class, () -> EventAssignmentGroup.from(1L, snapshots));
    }

    @Test
    void shouldGroupEmptyEvent() {
        EventAssignmentGroup group = EventAssignmentGroup.from(1L, List.of());

        assertNull(group.priest());
        assertTrue(group.readers().isEmpty());
        assertTrue(group.commentators().isEmpty());
        assertTrue(group.ministersOfTheWord().isEmpty());
        assertTrue(group.eucharisticMinisters().isEmpty());
    }

    private EventAssignmentSnapshot snapshot(Long eventId, Long personId, EventAssignmentType assignmentType) {
        return new EventAssignmentSnapshot(100L + personId, eventId, personId, assignmentType, "Person " + personId, null);
    }

    private EventAssignment assignment(Long assignmentId, Long eventId, Person person, EventAssignmentType assignmentType) {
        EventAssignment assignment = new EventAssignment(event(eventId), person, assignmentType);
        assignment.setId(assignmentId);
        return assignment;
    }

    private CelebrationEvent event(Long eventId) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(eventId);
        return event;
    }

    private <T extends Person> T person(T person, Long personId, String name, String personType) {
        person.setId(personId);
        person.setName(name);
        person.setPhoneNumber("34976" + String.format("%06d", personId));
        person.setPersonType(personType);
        return person;
    }
}
