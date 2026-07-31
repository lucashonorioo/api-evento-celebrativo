package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.projection.ScheduleConflictUnavailabilityProjection;
import com.eventoscelebrativos.projection.ScheduleUnavailabilityConflictKeyProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.ScheduleUnavailabilityConflictRepository;
import com.eventoscelebrativos.service.impl.ScheduleUnavailabilityConflictServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleUnavailabilityConflictServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

    @Mock
    private ScheduleUnavailabilityConflictRepository scheduleUnavailabilityConflictRepository;

    @Mock
    private CelebrationEventRepository celebrationEventRepository;

    private final Clock clock = Clock.fixed(CURRENT_SECOND.atZone(ZONE).toInstant(), ZONE);

    private ScheduleUnavailabilityConflictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScheduleUnavailabilityConflictServiceImpl(
                scheduleUnavailabilityConflictRepository, celebrationEventRepository, clock);
    }

    @Test
    void shouldThrowResourceNotFoundWhenEventDoesNotExist() {
        when(celebrationEventRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.findByEventId(99L));
        verifyNoInteractions(scheduleUnavailabilityConflictRepository);
    }

    @Test
    void shouldRejectInvalidEventId() {
        assertThrows(BadRequestException.class, () -> service.findByEventId(null));
        assertThrows(BadRequestException.class, () -> service.findByEventId(0L));
        verifyNoInteractions(celebrationEventRepository, scheduleUnavailabilityConflictRepository);
    }

    @Test
    void shouldReturnEmptyListWhenEventHasNoConflicts() {
        when(celebrationEventRepository.existsById(1L)).thenReturn(true);
        when(scheduleUnavailabilityConflictRepository.findConflictsByEventId(1L, CURRENT_SECOND)).thenReturn(List.of());

        List<ScheduleUnavailabilityConflictResponseDTO> result = service.findByEventId(1L);

        assertTrue(result.isEmpty());
        verify(scheduleUnavailabilityConflictRepository, never()).findUnavailabilitiesByPersonIdIn(anyCollection());
    }

    @Test
    void shouldAssembleConflictWithOnlyOverlappingUnavailabilitiesSortedByStartEndId() {
        when(celebrationEventRepository.existsById(1L)).thenReturn(true);
        LocalDateTime eventStartAt = LocalDateTime.of(2026, 8, 15, 19, 0);
        LocalDateTime eventEndAt = LocalDateTime.of(2026, 8, 15, 20, 0);
        when(scheduleUnavailabilityConflictRepository.findConflictsByEventId(1L, CURRENT_SECOND)).thenReturn(List.of(
                key(1L, "Missa", eventStartAt, eventEndAt, 4L, "Arthur Costa", "READER")
        ));
        when(scheduleUnavailabilityConflictRepository.findUnavailabilitiesByPersonIdIn(any())).thenReturn(List.of(
                unavailability(4L, 30L, LocalDateTime.of(2026, 8, 15, 19, 30), LocalDateTime.of(2026, 8, 15, 21, 0)),
                unavailability(4L, 20L, LocalDateTime.of(2026, 8, 15, 18, 30), LocalDateTime.of(2026, 8, 15, 19, 30)),
                // Nao sobrepoe o evento (adjacente antes): deve ser filtrada.
                unavailability(4L, 10L, LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 19, 0))
        ));

        List<ScheduleUnavailabilityConflictResponseDTO> result = service.findByEventId(1L);

        assertEquals(1, result.size());
        ScheduleUnavailabilityConflictResponseDTO conflict = result.get(0);
        assertEquals(1L, conflict.getEventId());
        assertEquals(4L, conflict.getPersonId());
        assertEquals("READER", conflict.getAssignmentType());
        assertEquals(List.of(20L, 30L), conflict.getUnavailabilities().stream().map(u -> u.getId()).toList());
    }

    @Test
    void shouldRejectMissingRangeOnPaginatedQuery() {
        assertThrows(BadRequestException.class, () -> service.findByRange(null, CURRENT_SECOND, 0, 10));
        assertThrows(BadRequestException.class, () -> service.findByRange(CURRENT_SECOND, null, 0, 10));
        verifyNoInteractions(scheduleUnavailabilityConflictRepository);
    }

    @Test
    void shouldRejectInvertedRangeOnPaginatedQuery() {
        assertThrows(BadRequestException.class,
                () -> service.findByRange(CURRENT_SECOND, CURRENT_SECOND.minusDays(1), 0, 10));
    }

    @Test
    void shouldRejectFractionalSecondsOnPaginatedQuery() {
        LocalDateTime withNanos = CURRENT_SECOND.plusNanos(1);
        assertThrows(TemporalPrecisionNotSupportedException.class,
                () -> service.findByRange(withNanos, CURRENT_SECOND.plusDays(1), 0, 10));
    }

    @Test
    void shouldRejectInvalidPaginationOnPaginatedQuery() {
        assertThrows(BadRequestException.class,
                () -> service.findByRange(CURRENT_SECOND, CURRENT_SECOND.plusDays(1), -1, 10));
        assertThrows(BadRequestException.class,
                () -> service.findByRange(CURRENT_SECOND, CURRENT_SECOND.plusDays(1), 0, 0));
        assertThrows(BadRequestException.class,
                () -> service.findByRange(CURRENT_SECOND, CURRENT_SECOND.plusDays(1), 0, 101));
    }

    @Test
    void shouldUseFixedClockAsCurrentSecondForPaginatedQuery() {
        LocalDateTime rangeStart = CURRENT_SECOND;
        LocalDateTime rangeEnd = CURRENT_SECOND.plusDays(30);
        when(scheduleUnavailabilityConflictRepository.findConflictsByRange(
                eq(rangeStart), eq(rangeEnd), eq(CURRENT_SECOND), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduleUnavailabilityConflictResponseDTO> result = service.findByRange(rangeStart, rangeEnd, 0, 10);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldPreserveTotalElementsFromKeyPageWhenAssemblingPaginatedResult() {
        LocalDateTime rangeStart = CURRENT_SECOND;
        LocalDateTime rangeEnd = CURRENT_SECOND.plusDays(30);
        LocalDateTime eventStartAt = LocalDateTime.of(2026, 8, 15, 19, 0);
        LocalDateTime eventEndAt = LocalDateTime.of(2026, 8, 15, 20, 0);
        ScheduleUnavailabilityConflictKeyProjection singleKey =
                key(1L, "Missa", eventStartAt, eventEndAt, 4L, "Arthur Costa", "READER");
        when(scheduleUnavailabilityConflictRepository.findConflictsByRange(
                eq(rangeStart), eq(rangeEnd), eq(CURRENT_SECOND), any()))
                .thenReturn(new PageImpl<>(List.of(singleKey), PageRequest.of(0, 1), 5));
        when(scheduleUnavailabilityConflictRepository.findUnavailabilitiesByPersonIdIn(any())).thenReturn(List.of());

        Page<ScheduleUnavailabilityConflictResponseDTO> result = service.findByRange(rangeStart, rangeEnd, 0, 1);

        assertEquals(5, result.getTotalElements(), "totalElements deve refletir a contagem de pares distintos da pagina de chaves");
        assertEquals(1, result.getNumberOfElements());
    }

    private ScheduleUnavailabilityConflictKeyProjection key(
            Long eventId, String eventName, LocalDateTime eventStartAt, LocalDateTime eventEndAt,
            Long personId, String personName, String assignmentType
    ) {
        return new ScheduleUnavailabilityConflictKeyProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return eventName;
            }

            @Override
            public LocalDateTime getEventStartAt() {
                return eventStartAt;
            }

            @Override
            public LocalDateTime getEventEndAt() {
                return eventEndAt;
            }

            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public String getPersonName() {
                return personName;
            }

            @Override
            public String getAssignmentType() {
                return assignmentType;
            }
        };
    }

    private ScheduleConflictUnavailabilityProjection unavailability(Long personId, Long id, LocalDateTime startAt, LocalDateTime endAt) {
        return new ScheduleConflictUnavailabilityProjection() {
            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public Long getId() {
                return id;
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
}
