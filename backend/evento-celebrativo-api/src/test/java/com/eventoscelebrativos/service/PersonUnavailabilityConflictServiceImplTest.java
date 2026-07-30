package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityAssignmentConflictException;
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

import java.time.LocalDate;
import java.time.LocalTime;
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
        when(personUnavailabilityRepository.findOverlapping(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(List.of());

        service.validateNoOverlap(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null);
    }

    @Test
    void shouldThrowOverlapExceptionWhenPeriodsIntersect() {
        when(personUnavailabilityRepository.findOverlapping(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(List.of(new PersonUnavailability()));

        assertThrows(UnavailabilityOverlapException.class,
                () -> service.validateNoOverlap(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null));
    }

    @Test
    void shouldExcludeOwnIdWhenValidatingOverlapForUpdate() {
        when(personUnavailabilityRepository.findOverlappingExcludingId(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5L))
                .thenReturn(List.of());

        service.validateNoOverlap(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5L);

        verify(personUnavailabilityRepository).findOverlappingExcludingId(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5L);
        verify(personUnavailabilityRepository, never()).findOverlapping(anyLong(), any(), any());
    }

    @Test
    void shouldNotThrowWhenNoAssignmentConflictExists() {
        when(eventAssignmentRepository.findAssignmentConflictsByPersonIdAndDateRange(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(List.of());

        service.validateNoAssignmentConflict(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12));
    }

    @Test
    void shouldGroupAssignmentConflictsByEventAndOrderAssignmentsByEnumOrder() {
        when(eventAssignmentRepository.findAssignmentConflictsByPersonIdAndDateRange(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(List.of(
                        conflictRow(15L, "Missa das 19h", LocalDate.of(2026, 8, 15), LocalTime.of(19, 0), "COMMENTATOR"),
                        conflictRow(15L, "Missa das 19h", LocalDate.of(2026, 8, 15), LocalTime.of(19, 0), "READER")
                ));

        UnavailabilityAssignmentConflictException exception = assertThrows(
                UnavailabilityAssignmentConflictException.class,
                () -> service.validateNoAssignmentConflict(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)));

        assertEquals(1, exception.getConflicts().size());
        EventAssignmentConflictDTO conflict = exception.getConflicts().get(0);
        assertEquals(15L, conflict.getEventId());
        assertEquals(List.of("READER", "COMMENTATOR"), conflict.getAssignments());
    }

    @Test
    void shouldOrderMultipleEventConflictsByDateTimeThenId() {
        when(eventAssignmentRepository.findAssignmentConflictsByPersonIdAndDateRange(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(
                        conflictRow(20L, "Missa Tarde", LocalDate.of(2026, 8, 15), LocalTime.of(19, 0), "READER"),
                        conflictRow(10L, "Missa Manha", LocalDate.of(2026, 8, 15), LocalTime.of(8, 0), "READER")
                ));

        UnavailabilityAssignmentConflictException exception = assertThrows(
                UnavailabilityAssignmentConflictException.class,
                () -> service.validateNoAssignmentConflict(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertEquals(List.of(10L, 20L), exception.getConflicts().stream().map(EventAssignmentConflictDTO::getEventId).toList());
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
    void shouldNotThrowWhenNoPersonIsUnavailableOnEventDate() {
        Map<Long, Set<EventAssignmentType>> plan = Map.of(4L, EnumSet.of(EventAssignmentType.READER));
        when(personUnavailabilityRepository.findByPersonIdsAndDate(plan.keySet(), LocalDate.of(2026, 8, 15)))
                .thenReturn(List.of());

        service.validateAvailabilityForEvent(plan, LocalDate.of(2026, 8, 15));
    }

    @Test
    void shouldThrowPersonUnavailableForEventExceptionGroupingAssignmentTypesByPerson() {
        Map<Long, Set<EventAssignmentType>> plan = Map.of(
                4L, EnumSet.of(EventAssignmentType.READER, EventAssignmentType.COMMENTATOR)
        );
        when(personUnavailabilityRepository.findByPersonIdsAndDate(plan.keySet(), LocalDate.of(2026, 8, 15)))
                .thenReturn(List.of(personProjection(4L, "Arthur Costa", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20))));

        PersonUnavailableForEventException exception = assertThrows(
                PersonUnavailableForEventException.class,
                () -> service.validateAvailabilityForEvent(plan, LocalDate.of(2026, 8, 15)));

        assertEquals(1, exception.getConflicts().size());
        PersonUnavailabilityEventConflictDTO conflict = exception.getConflicts().get(0);
        assertEquals(4L, conflict.getPersonId());
        assertEquals("Arthur Costa", conflict.getPersonName());
        assertEquals(List.of("READER", "COMMENTATOR"), conflict.getAssignmentTypes());
    }

    @Test
    void shouldDoNothingWhenValidatingAvailabilityForEmptyPlan() {
        service.validateAvailabilityForEvent(Map.of(), LocalDate.of(2026, 8, 15));

        verify(personUnavailabilityRepository, never()).findByPersonIdsAndDate(any(), any());
    }

    @Test
    void shouldDeduplicatePeopleWhenListingAdminUnavailabilityOnDate() {
        when(personUnavailabilityRepository.findAllByDate(LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(
                        personProjection(4L, "Arthur Costa", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)),
                        personProjection(4L, "Arthur Costa", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))
                ));

        List<AdminUnavailabilityPersonDTO> result = service.findUnavailablePeopleOnDate(LocalDate.of(2026, 8, 10));

        assertEquals(1, result.size());
        assertTrue(result.stream().allMatch(dto -> dto.getPersonId().equals(4L)));
    }

    private PersonUnavailabilityAssignmentConflictProjection conflictRow(
            Long eventId, String eventName, LocalDate eventDate, LocalTime eventTime, String assignmentType
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
            public LocalDate getEventDate() {
                return eventDate;
            }

            @Override
            public LocalTime getEventTime() {
                return eventTime;
            }

            @Override
            public String getAssignmentType() {
                return assignmentType;
            }
        };
    }

    private PersonUnavailabilityPersonProjection personProjection(Long personId, String personName, LocalDate startDate, LocalDate endDate) {
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
            public LocalDate getStartDate() {
                return startDate;
            }

            @Override
            public LocalDate getEndDate() {
                return endDate;
            }
        };
    }
}
