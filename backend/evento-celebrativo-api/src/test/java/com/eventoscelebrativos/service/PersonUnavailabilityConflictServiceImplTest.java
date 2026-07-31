package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.dto.response.StartedAssignmentConflictDTO;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityConflictWithStartedAssignmentException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.PersonUnavailabilityConflictServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonUnavailabilityConflictServiceImplTest {

    @Mock
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @Mock
    private PersonRepository personRepository;

    private PersonUnavailabilityConflictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonUnavailabilityConflictServiceImpl(
                personUnavailabilityRepository, eventAssignmentRepository, personRepository);
    }

    @Test
    void shouldNotThrowWhenNoOverlapExists() {
        when(personUnavailabilityRepository.findOverlapping(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0)))
                .thenReturn(List.of());

        service.validateNoOverlap(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null);
    }

    @Test
    void shouldThrowOverlapExceptionWhenPeriodsIntersect() {
        when(personUnavailabilityRepository.findOverlapping(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0)))
                .thenReturn(List.of(new PersonUnavailability()));

        assertThrows(UnavailabilityOverlapException.class,
                () -> service.validateNoOverlap(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), null));
    }

    @Test
    void shouldExcludeOwnIdWhenValidatingOverlapForUpdate() {
        when(personUnavailabilityRepository.findOverlappingExcludingId(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), 5L))
                .thenReturn(List.of());

        service.validateNoOverlap(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), 5L);

        verify(personUnavailabilityRepository).findOverlappingExcludingId(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), 5L);
        verify(personUnavailabilityRepository, never()).findOverlapping(anyLong(), any(), any());
    }

    @Test
    void shouldNotThrowWhenNoStartedAssignmentConflictExists() {
        LocalDateTime currentSecond = at(2026, 8, 9, 12, 0);
        when(eventAssignmentRepository.findStartedAssignmentConflictsByPersonIdAndRange(
                1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), currentSecond))
                .thenReturn(List.of());

        service.validateNoStartedAssignmentConflict(1L, at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0), currentSecond);
    }

    @Test
    void shouldThrowWithSingularAssignmentTypeWhenStartedAssignmentConflictExists() {
        LocalDateTime currentSecond = at(2026, 8, 15, 19, 30);
        when(eventAssignmentRepository.findStartedAssignmentConflictsByPersonIdAndRange(
                1L, at(2026, 8, 15, 19, 15), at(2026, 8, 15, 21, 0), currentSecond))
                .thenReturn(List.of(
                        conflictRow(15L, "Missa das 19h", at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0), "READER")
                ));

        UnavailabilityConflictWithStartedAssignmentException exception = assertThrows(
                UnavailabilityConflictWithStartedAssignmentException.class,
                () -> service.validateNoStartedAssignmentConflict(
                        1L, at(2026, 8, 15, 19, 15), at(2026, 8, 15, 21, 0), currentSecond));

        assertEquals(1, exception.getConflicts().size());
        StartedAssignmentConflictDTO conflict = exception.getConflicts().get(0);
        assertEquals(15L, conflict.getEventId());
        assertEquals("READER", conflict.getAssignmentType());
    }

    @Test
    void shouldOrderMultipleEventConflictsByDateTimeThenId() {
        LocalDateTime currentSecond = at(2026, 8, 15, 7, 0);
        when(eventAssignmentRepository.findStartedAssignmentConflictsByPersonIdAndRange(
                1L, at(2026, 8, 1, 0, 0), at(2026, 8, 31, 0, 0), currentSecond))
                .thenReturn(List.of(
                        conflictRow(20L, "Missa Tarde", at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0), "READER"),
                        conflictRow(10L, "Missa Manha", at(2026, 8, 15, 8, 0), at(2026, 8, 15, 9, 0), "READER")
                ));

        UnavailabilityConflictWithStartedAssignmentException exception = assertThrows(
                UnavailabilityConflictWithStartedAssignmentException.class,
                () -> service.validateNoStartedAssignmentConflict(
                        1L, at(2026, 8, 1, 0, 0), at(2026, 8, 31, 0, 0), currentSecond));

        assertEquals(List.of(10L, 20L), exception.getConflicts().stream().map(StartedAssignmentConflictDTO::getEventId).toList());
    }

    @Test
    void shouldLockPersonsInAscendingOrderRegardlessOfInputOrder() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(new Person()));
        when(personRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(new Person()));
        when(personRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(new Person()));

        service.lockPersonsInOrder(List.of(3L, 1L, 2L));

        InOrder inOrder = inOrder(personRepository);
        inOrder.verify(personRepository).findByIdForUpdate(1L);
        inOrder.verify(personRepository).findByIdForUpdate(2L);
        inOrder.verify(personRepository).findByIdForUpdate(3L);
    }

    @Test
    void shouldDoNothingWhenLockingEmptyPersonCollection() {
        service.lockPersonsInOrder(List.of());

        verify(personRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void shouldThrowResourceNotFoundWhenLockingMissingPerson() {
        when(personRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.lockPersonsInOrder(List.of(1L)));
    }

    @Test
    void shouldNotThrowWhenNoPersonIsUnavailableOnEventRange() {
        Map<Long, Set<EventAssignmentType>> plan = Map.of(4L, EnumSet.of(EventAssignmentType.READER));
        when(personUnavailabilityRepository.findByPersonIdsAndRange(plan.keySet(), at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0)))
                .thenReturn(List.of());

        service.validateAvailabilityForEvent(plan, at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0));
    }

    @Test
    void shouldThrowPersonUnavailableForEventExceptionGroupingAssignmentTypesByPerson() {
        Map<Long, Set<EventAssignmentType>> plan = Map.of(
                4L, EnumSet.of(EventAssignmentType.READER, EventAssignmentType.COMMENTATOR)
        );
        when(personUnavailabilityRepository.findByPersonIdsAndRange(plan.keySet(), at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0)))
                .thenReturn(List.of(personProjection(4L, "Arthur Costa", at(2026, 8, 10, 0, 0), at(2026, 8, 20, 0, 0))));

        PersonUnavailableForEventException exception = assertThrows(
                PersonUnavailableForEventException.class,
                () -> service.validateAvailabilityForEvent(plan, at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0)));

        assertEquals(1, exception.getConflicts().size());
        PersonUnavailabilityEventConflictDTO conflict = exception.getConflicts().get(0);
        assertEquals(4L, conflict.getPersonId());
        assertEquals("Arthur Costa", conflict.getPersonName());
        assertEquals(List.of("READER", "COMMENTATOR"), conflict.getAssignmentTypes());
    }

    @Test
    void shouldDoNothingWhenValidatingAvailabilityForEmptyPlan() {
        service.validateAvailabilityForEvent(Map.of(), at(2026, 8, 15, 19, 0), at(2026, 8, 15, 20, 0));

        verify(personUnavailabilityRepository, never()).findByPersonIdsAndRange(any(), any(), any());
    }

    @Test
    void shouldDeduplicatePeopleWhenListingAdminUnavailabilityOnRange() {
        when(personUnavailabilityRepository.findAllByRange(at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0)))
                .thenReturn(List.of(
                        personProjection(4L, "Arthur Costa", at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0)),
                        personProjection(4L, "Arthur Costa", at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0))
                ));

        List<AdminUnavailabilityPersonDTO> result = service.findUnavailablePeopleOnRange(at(2026, 8, 10, 0, 0), at(2026, 8, 12, 0, 0));

        assertEquals(1, result.size());
        assertTrue(result.stream().allMatch(dto -> dto.getPersonId().equals(4L)));
    }

    @Test
    void shouldGroupMultipleDistinctPeriodsForTheSamePersonWhenListingAdminUnavailabilityOnRange() {
        when(personUnavailabilityRepository.findAllByRange(at(2026, 8, 1, 0, 0), at(2026, 8, 31, 0, 0)))
                .thenReturn(List.of(
                        personProjection(4L, "Arthur Costa", at(2026, 8, 5, 0, 0), at(2026, 8, 7, 0, 0)),
                        personProjection(4L, "Arthur Costa", at(2026, 8, 20, 0, 0), at(2026, 8, 22, 0, 0))
                ));

        List<AdminUnavailabilityPersonDTO> result = service.findUnavailablePeopleOnRange(at(2026, 8, 1, 0, 0), at(2026, 8, 31, 0, 0));

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getUnavailabilities().size());
    }

    private PersonUnavailabilityAssignmentConflictProjection conflictRow(
            Long eventId, String eventName, LocalDateTime startAt, LocalDateTime endAt, String assignmentType
    ) {
        return new PersonUnavailabilityAssignmentConflictProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return eventName;
            }

            @Override
            public LocalDateTime getStartAt() {
                return startAt;
            }

            @Override
            public LocalDateTime getEndAt() {
                return endAt;
            }

            @Override
            public String getAssignmentType() {
                return assignmentType;
            }
        };
    }

    private PersonUnavailabilityPersonProjection personProjection(Long personId, String personName, LocalDateTime startAt, LocalDateTime endAt) {
        return new PersonUnavailabilityPersonProjection() {
            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public String getPersonName() {
                return personName;
            }

            @Override
            public LocalDateTime getStartAt() {
                return startAt;
            }

            @Override
            public LocalDateTime getEndAt() {
                return endAt;
            }
        };
    }

    private static LocalDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }
}
