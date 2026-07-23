package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.EventAssignmentAuditResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.projection.LegacyEventAssignmentProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.impl.EventAssignmentConsistencyServiceImpl;
import com.eventoscelebrativos.service.impl.EventAssignmentOperationalAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAssignmentOperationalAuditServiceImplTest {

    @Mock
    private CelebrationEventRepository celebrationEventRepository;

    @Mock
    private EventAssignmentReadService eventAssignmentReadService;

    private final EventAssignmentConsistencyService consistencyService =
            new EventAssignmentConsistencyServiceImpl(new LegacyEventAssignmentSnapshotResolver());

    @Test
    void shouldAuditFullyConsistentPage() {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L)))
                .thenReturn(List.of(legacy(1L, 10L, "reader")));
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER))));

        EventAssignmentAuditResponseDTO response = service.audit(null, null, null, 0, 20, true);

        assertEquals(1, response.summary().eventsChecked());
        assertEquals(1, response.summary().consistentEvents());
        assertEquals(0, response.summary().inconsistentEvents());
        assertEquals(1, response.summary().legacyParticipants());
        assertEquals(1, response.summary().parallelAssignments());
        assertEquals(0, response.summary().totalIssues());
        assertEquals(List.of(), response.events());
    }

    @Test
    void shouldReportMissingParallelAssignment() {
        EventAssignmentAuditResponseDTO response = auditSingleInconsistentEvent(
                List.of(legacy(1L, 10L, "reader")),
                List.of()
        );

        assertEquals(1, response.summary().missingParallelAssignments());
        assertIssue(response, EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT);
    }

    @Test
    void shouldReportExtraParallelAssignment() {
        EventAssignmentAuditResponseDTO response = auditSingleInconsistentEvent(
                List.of(),
                List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER))
        );

        assertEquals(1, response.summary().extraParallelAssignments());
        assertIssue(response, EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT);
    }

    @Test
    void shouldReportAssignmentTypeMismatch() {
        EventAssignmentAuditResponseDTO response = auditSingleInconsistentEvent(
                List.of(legacy(1L, 10L, "reader")),
                List.of(snapshot(100L, 1L, 10L, EventAssignmentType.COMMENTATOR))
        );

        assertEquals(1, response.summary().assignmentTypeMismatches());
        assertIssue(response, EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH);
    }

    @Test
    void shouldReportSeveralIssuesInSameEvent() {
        EventAssignmentAuditResponseDTO response = auditSingleInconsistentEvent(
                List.of(
                        legacy(1L, 10L, "reader"),
                        legacy(1L, 20L, "commentator")
                ),
                List.of(
                        snapshot(100L, 1L, 10L, EventAssignmentType.COMMENTATOR),
                        snapshot(101L, 1L, 99L, EventAssignmentType.PRIEST)
                )
        );

        assertEquals(1, response.summary().missingParallelAssignments());
        assertEquals(1, response.summary().extraParallelAssignments());
        assertEquals(1, response.summary().assignmentTypeMismatches());
        assertEquals(3, response.events().get(0).issueCount());
    }

    @Test
    void shouldAuditSeveralEventsIndependently() {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L, 2L));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L, 2L)))
                .thenReturn(List.of(
                        legacy(1L, 10L, "reader"),
                        legacy(2L, 20L, "commentator")
                ));
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L, 2L)))
                .thenReturn(Map.of(
                        1L, List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER)),
                        2L, List.of()
                ));

        EventAssignmentAuditResponseDTO response = service.audit(null, null, null, 0, 20, true);

        assertEquals(2, response.summary().eventsChecked());
        assertEquals(1, response.summary().consistentEvents());
        assertEquals(1, response.summary().inconsistentEvents());
        assertEquals(List.of(2L), response.events().stream().map(event -> event.eventId()).toList());
    }

    @Test
    void shouldTreatEventWithoutParticipantsAsConsistent() {
        EventAssignmentAuditResponseDTO response = auditSingleInconsistentEvent(List.of(), List.of());

        assertEquals(1, response.summary().eventsChecked());
        assertEquals(1, response.summary().consistentEvents());
        assertEquals(0, response.summary().totalIssues());
        assertEquals(List.of(), response.events());
    }

    @Test
    void shouldAuditSpecificEventOnly() {
        EventAssignmentOperationalAuditService service = service();
        when(celebrationEventRepository.existsById(7L)).thenReturn(true);
        when(celebrationEventRepository.findEventIdForAssignmentAudit(
                any(PageRequest.class),
                eq(7L),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))
        )).thenReturn(new PageImpl<>(List.of(7L), PageRequest.of(0, 1), 1));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(7L))).thenReturn(List.of());
        when(eventAssignmentReadService.findAllByEventIds(List.of(7L))).thenReturn(Map.of(7L, List.of()));

        EventAssignmentAuditResponseDTO response = service.audit(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                7L,
                3,
                50,
                true
        );

        assertEquals(0, response.page());
        assertEquals(1, response.size());
        assertEquals(1, response.summary().eventsChecked());
        verify(celebrationEventRepository, never()).findEventIdsForAssignmentAudit(any(), any(), any());
    }

    @Test
    void shouldPreserveNotFoundBehaviorForMissingEventId() {
        EventAssignmentOperationalAuditService service = service();
        when(celebrationEventRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.audit(null, null, 99L, 0, 20, true));

        verify(celebrationEventRepository, never()).findEventIdForAssignmentAudit(any(), any(), any(), any());
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldReturnEmptyPageWithoutBatchReads() {
        EventAssignmentOperationalAuditService service = service();
        when(celebrationEventRepository.findEventIdsForAssignmentAudit(
                any(PageRequest.class),
                eq(LocalDate.of(2030, 1, 1)),
                eq(LocalDate.of(2030, 1, 31))
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 0));

        EventAssignmentAuditResponseDTO response = service.audit(
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31),
                null,
                2,
                20,
                true
        );

        assertEquals(0, response.summary().eventsChecked());
        assertTrue(response.empty());
        assertEquals(List.of(), response.events());
        verify(celebrationEventRepository, never()).findLegacyEventAssignmentsForAudit(any());
        verifyNoInteractions(eventAssignmentReadService);
    }

    @Test
    void shouldRejectInvalidInterval() {
        EventAssignmentOperationalAuditService service = service();

        assertThrows(BusinessException.class, () -> service.audit(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                null,
                0,
                20,
                true
        ));

        verifyNoInteractions(celebrationEventRepository, eventAssignmentReadService);
    }

    @Test
    void shouldRejectInvalidPageAndSize() {
        EventAssignmentOperationalAuditService service = service();

        assertThrows(BusinessException.class, () -> service.audit(null, null, null, -1, 20, true));
        assertThrows(BusinessException.class, () -> service.audit(null, null, null, 0, 0, true));
        assertThrows(BusinessException.class, () -> service.audit(null, null, null, 0, 101, true));
        assertThrows(BusinessException.class, () -> service.audit(null, null, 0L, 0, 20, true));

        verifyNoInteractions(celebrationEventRepository, eventAssignmentReadService);
    }

    @Test
    void shouldOmitDetailsWhenDisabledButKeepSummary() {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L)))
                .thenReturn(List.of(legacy(1L, 10L, "reader")));
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of()));

        EventAssignmentAuditResponseDTO response = service.audit(null, null, null, 0, 20, false);

        assertEquals(1, response.summary().totalIssues());
        assertNull(response.events());
    }

    @Test
    void shouldCountDuplicateParallelAssignmentsMultiplePriestsAndUnknownLegacyTypes() {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L)))
                .thenReturn(List.of(
                        legacy(1L, 10L, "priest"),
                        legacy(1L, 11L, "priest"),
                        legacy(1L, 12L, "unsupported")
                ));
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(
                        snapshot(100L, 1L, 10L, EventAssignmentType.PRIEST),
                        snapshot(101L, 1L, 11L, EventAssignmentType.PRIEST),
                        snapshot(102L, 1L, 11L, EventAssignmentType.PRIEST)
                )));

        EventAssignmentAuditResponseDTO response = service.audit(null, null, null, 0, 20, true);

        assertEquals(2, response.summary().duplicateParallelAssignments());
        assertEquals(5, response.summary().multiplePriests());
        assertEquals(1, response.summary().unknownLegacyPersonTypes());
    }

    @Test
    void shouldNotMutateDataReturnedByCollaborators() {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L));
        ArrayList<LegacyEventAssignmentProjection> legacyRows = new ArrayList<>(
                List.of(legacy(1L, 10L, "reader"))
        );
        ArrayList<EventAssignmentSnapshot> parallelRows = new ArrayList<>(
                List.of(snapshot(100L, 1L, 10L, EventAssignmentType.READER))
        );
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L))).thenReturn(legacyRows);
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L))).thenReturn(Map.of(1L, parallelRows));

        service.audit(null, null, null, 0, 20, true);

        assertEquals(1, legacyRows.size());
        assertEquals(1, parallelRows.size());
        assertEquals(10L, legacyRows.get(0).getPersonId());
        assertEquals(10L, parallelRows.get(0).personId());
    }

    @Test
    void shouldUseRequestedPageAndSizeForPagedAudit() {
        EventAssignmentOperationalAuditService service = service();
        when(celebrationEventRepository.findEventIdsForAssignmentAudit(any(PageRequest.class), eq(null), eq(null)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 30), 0));

        service.audit(null, null, null, 2, 30, true);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(celebrationEventRepository).findEventIdsForAssignmentAudit(captor.capture(), eq(null), eq(null));
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(30, captor.getValue().getPageSize());
    }

    private EventAssignmentOperationalAuditService service() {
        return new EventAssignmentOperationalAuditServiceImpl(
                celebrationEventRepository,
                eventAssignmentReadService,
                consistencyService
        );
    }

    private EventAssignmentAuditResponseDTO auditSingleInconsistentEvent(
            List<LegacyEventAssignmentProjection> legacyRows,
            List<EventAssignmentSnapshot> parallelRows
    ) {
        EventAssignmentOperationalAuditService service = service();
        whenEventPage(List.of(1L));
        when(celebrationEventRepository.findLegacyEventAssignmentsForAudit(List.of(1L))).thenReturn(legacyRows);
        when(eventAssignmentReadService.findAllByEventIds(List.of(1L))).thenReturn(Map.of(1L, parallelRows));
        return service.audit(null, null, null, 0, 20, true);
    }

    private void whenEventPage(List<Long> eventIds) {
        when(celebrationEventRepository.findEventIdsForAssignmentAudit(any(PageRequest.class), eq(null), eq(null)))
                .thenReturn(new PageImpl<>(eventIds, PageRequest.of(0, 20), eventIds.size()));
    }

    private void assertIssue(EventAssignmentAuditResponseDTO response, EventAssignmentConsistencyIssueType issueType) {
        assertEquals(1, response.summary().inconsistentEvents());
        assertTrue(response.events().get(0).issues().stream().anyMatch(issue -> issue.issueType() == issueType));
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
                null
        );
    }

    private LegacyEventAssignmentProjection legacy(Long eventId, Long personId, String personType) {
        return new TestLegacyEventAssignmentProjection(eventId, personId, personType);
    }

    private record TestLegacyEventAssignmentProjection(
            Long eventId,
            Long personId,
            String personType
    ) implements LegacyEventAssignmentProjection {

        @Override
        public Long getEventId() {
            return eventId;
        }

        @Override
        public Long getPersonId() {
            return personId;
        }

        @Override
        public String getPersonType() {
            return personType;
        }
    }
}
