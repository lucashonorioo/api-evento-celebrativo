package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Desde a branch feature/schedule-conflict-notifications, este service nao valida mais conflito
 * entre EventAssignment e PersonUnavailability (nem evento em andamento, nem nova atribuicao): esse
 * conflito e sempre permitido e tratado por {@code ScheduleConflictNotificationService}. Este teste
 * cobre apenas o que permanece aqui: sobreposicao entre indisponibilidades da mesma pessoa, o lock
 * ordenado de pessoas e a consulta administrativa por intervalo.
 */
@ExtendWith(MockitoExtension.class)
class PersonUnavailabilityConflictServiceImplTest {

    @Mock
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Mock
    private PersonRepository personRepository;

    private PersonUnavailabilityConflictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonUnavailabilityConflictServiceImpl(personUnavailabilityRepository, personRepository);
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
