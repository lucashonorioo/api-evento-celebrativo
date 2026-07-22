package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class EventAssignmentShadowReadExecutorTest {

    @Mock
    private EventAssignmentReadService eventAssignmentReadService;

    @Mock
    private EventAssignmentConsistencyService eventAssignmentConsistencyService;

    @InjectMocks
    private EventAssignmentShadowReadExecutor executor;

    @Test
    void shouldDoNothingWhenEventFlagIsDisabled() {
        executor.compareEventIfEnabled(false, "event-detail", event(1L));

        verifyNoInteractions(eventAssignmentReadService, eventAssignmentConsistencyService);
    }

    @Test
    void shouldNotResolvePartialLegacySnapshotsWhenFlagIsDisabled() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        executor.comparePartialAssignmentsIfEnabled(
                false,
                "monthly-schedule",
                List.of(1L),
                EventAssignmentType.READER,
                () -> {
                    supplierCalled.set(true);
                    return List.of();
                }
        );

        assertFalse(supplierCalled.get());
        verifyNoInteractions(eventAssignmentReadService, eventAssignmentConsistencyService);
    }

    @Test
    void shouldCompareConsistentEventWithoutChangingOfficialFlow() {
        CelebrationEvent event = event(1L);
        List<EventAssignmentSnapshot> parallel = List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER));
        EventAssignmentConsistencyReport report = report(1L, 1, 1, List.of());
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(parallel);
        when(eventAssignmentConsistencyService.compareEvent(event, parallel)).thenReturn(report);

        executor.compareEventIfEnabled(true, "event-scale-detail", event);

        verify(eventAssignmentReadService).findAllByEventId(1L);
        verify(eventAssignmentConsistencyService).compareEvent(event, parallel);
    }

    @Test
    void shouldLogIssuesAndNotPropagateWhenEventIsInconsistent(CapturedOutput output) {
        CelebrationEvent event = event(1L);
        List<EventAssignmentSnapshot> parallel = List.of(snapshot(100L, 1L, 10L, EventAssignmentType.COMMENTATOR));
        EventAssignmentConsistencyIssue issue = new EventAssignmentConsistencyIssue(
                EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH,
                1L,
                100L,
                10L,
                EventAssignmentType.READER,
                EventAssignmentType.COMMENTATOR,
                "reader"
        );
        when(eventAssignmentReadService.findAllByEventId(1L)).thenReturn(parallel);
        when(eventAssignmentConsistencyService.compareEvent(event, parallel))
                .thenReturn(report(1L, 1, 1, List.of(issue)));

        assertDoesNotThrow(() -> executor.compareEventIfEnabled(true, "event-scale-detail", event));

        assertTrue(output.getOut().contains("EventAssignment shadow read divergence"));
        assertTrue(output.getOut().contains("ASSIGNMENT_TYPE_MISMATCH"));
    }

    @Test
    void shouldCaptureParallelFailureAndNotCallConsistency(CapturedOutput output) {
        CelebrationEvent event = event(1L);
        when(eventAssignmentReadService.findAllByEventId(1L))
                .thenThrow(new IllegalStateException("parallel read failed"));

        assertDoesNotThrow(() -> executor.compareEventIfEnabled(true, "event-detail", event));

        verify(eventAssignmentConsistencyService, never()).compareEvent(eq(event), org.mockito.ArgumentMatchers.anyList());
        assertTrue(output.getOut().contains("EventAssignment shadow read failed"));
        assertTrue(output.getOut().contains("IllegalStateException"));
    }

    @Test
    void shouldReadSeveralEventsInOneBatchCall() {
        CelebrationEvent first = event(1L);
        CelebrationEvent second = event(2L);
        Map<Long, List<EventAssignmentSnapshot>> parallel = Map.of(
                1L, List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER)),
                2L, List.of(snapshot(200L, 2L, 20L, EventAssignmentType.PRIEST))
        );
        Map<Long, EventAssignmentConsistencyReport> reports = Map.of(
                1L, report(1L, 1, 1, List.of()),
                2L, report(2L, 1, 1, List.of())
        );
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L, 2L))).thenReturn(parallel);
        when(eventAssignmentConsistencyService.compareEvents(List.of(first, second, first), parallel)).thenReturn(reports);

        executor.compareEventsIfEnabled(true, "monthly-schedule", List.of(first, second, first));

        verify(eventAssignmentReadService).findAllByEventIds(List.of(1L, 2L));
        verify(eventAssignmentConsistencyService).compareEvents(List.of(first, second, first), parallel);
    }

    @Test
    void shouldComparePartialAssignmentsWithOneBatchReadAndFilteredParallelSnapshots() {
        EventAssignmentSnapshot legacyReader = snapshot(null, 1L, 10L, EventAssignmentType.READER);
        EventAssignmentSnapshot parallelReader = snapshot(100L, 1L, 10L, EventAssignmentType.READER);
        EventAssignmentSnapshot parallelPriest = snapshot(101L, 1L, 11L, EventAssignmentType.PRIEST);
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L, 2L)))
                .thenReturn(Map.of(
                        1L, List.of(parallelPriest, parallelReader),
                        2L, List.of()
                ));
        when(eventAssignmentConsistencyService.compareSnapshotGroups(eq(List.of(1L, 2L)), anyMap(), anyMap()))
                .thenReturn(Map.of(
                        1L, report(1L, 1, 1, List.of()),
                        2L, report(2L, 0, 0, List.of())
                ));

        executor.comparePartialAssignmentsIfEnabled(
                true,
                "monthly-schedule",
                List.of(2L, 1L, 2L),
                EventAssignmentType.READER,
                () -> List.of(legacyReader)
        );

        ArgumentCaptor<Map<Long, List<EventAssignmentSnapshot>>> parallelCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventAssignmentReadService).findAllByEventIds(List.of(1L, 2L));
        verify(eventAssignmentConsistencyService)
                .compareSnapshotGroups(eq(List.of(1L, 2L)), anyMap(), parallelCaptor.capture());
        assertEquals(List.of(parallelReader), parallelCaptor.getValue().get(1L));
        assertEquals(List.of(), parallelCaptor.getValue().get(2L));
    }

    private CelebrationEvent event(Long eventId) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(eventId);
        return event;
    }

    private EventAssignmentSnapshot snapshot(
            Long assignmentId,
            Long eventId,
            Long personId,
            EventAssignmentType assignmentType
    ) {
        return new EventAssignmentSnapshot(
                assignmentId,
                eventId,
                personId,
                assignmentType,
                "Person " + personId,
                assignmentType == null ? null : assignmentType.name().toLowerCase()
        );
    }

    private EventAssignmentConsistencyReport report(
            Long eventId,
            int legacyCount,
            int parallelCount,
            List<EventAssignmentConsistencyIssue> issues
    ) {
        return new EventAssignmentConsistencyReport(eventId, legacyCount, parallelCount, issues);
    }
}
