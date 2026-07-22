package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class EventAssignmentShadowReadExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventAssignmentShadowReadExecutor.class);
    private static final int MAX_ISSUE_DETAILS = 20;

    private final EventAssignmentReadService eventAssignmentReadService;
    private final EventAssignmentConsistencyService eventAssignmentConsistencyService;

    public EventAssignmentShadowReadExecutor(
            EventAssignmentReadService eventAssignmentReadService,
            EventAssignmentConsistencyService eventAssignmentConsistencyService
    ) {
        this.eventAssignmentReadService = eventAssignmentReadService;
        this.eventAssignmentConsistencyService = eventAssignmentConsistencyService;
    }

    public void compareEventIfEnabled(
            boolean enabled,
            String operation,
            CelebrationEvent legacyEvent
    ) {
        if (!enabled) {
            return;
        }
        compareEvent(operation, () -> Optional.ofNullable(legacyEvent));
    }

    public void compareEventIfEnabled(
            boolean enabled,
            String operation,
            Supplier<Optional<CelebrationEvent>> legacyEventSupplier
    ) {
        if (!enabled) {
            return;
        }
        compareEvent(operation, legacyEventSupplier);
    }

    public void compareEventsIfEnabled(
            boolean enabled,
            String operation,
            Collection<CelebrationEvent> legacyEvents
    ) {
        if (!enabled) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            List<CelebrationEvent> events = safeEvents(legacyEvents);
            if (events.isEmpty()) {
                logConsistent(operation, 0, 0, elapsedMillis(startedAt), false);
                return;
            }

            List<Long> eventIds = events.stream()
                    .map(CelebrationEvent::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            Map<Long, List<EventAssignmentSnapshot>> parallelAssignments =
                    eventAssignmentReadService.findAllByEventIds(eventIds);
            Map<Long, EventAssignmentConsistencyReport> reports =
                    eventAssignmentConsistencyService.compareEvents(events, parallelAssignments);

            logReports(operation, reports, elapsedMillis(startedAt), false);
        } catch (RuntimeException exception) {
            logTechnicalFailure(operation, exception);
        }
    }

    public void comparePartialAssignmentsIfEnabled(
            boolean enabled,
            String operation,
            Collection<Long> eventIds,
            EventAssignmentType assignmentType,
            Supplier<List<EventAssignmentSnapshot>> legacySnapshotsSupplier
    ) {
        if (!enabled) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            List<Long> normalizedEventIds = normalizeEventIds(eventIds);
            if (normalizedEventIds.isEmpty()) {
                logConsistent(operation, 0, 0, elapsedMillis(startedAt), true);
                return;
            }

            List<EventAssignmentSnapshot> legacySnapshots = safeSnapshots(legacySnapshotsSupplier.get()).stream()
                    .filter(snapshot -> assignmentType == null || snapshot.assignmentType() == assignmentType)
                    .toList();
            Map<Long, List<EventAssignmentSnapshot>> legacyByEvent = groupSnapshotsByRequestedEvent(
                    normalizedEventIds,
                    legacySnapshots
            );

            Map<Long, List<EventAssignmentSnapshot>> parallelByEvent =
                    eventAssignmentReadService.findAllByEventIds(normalizedEventIds);
            Map<Long, List<EventAssignmentSnapshot>> filteredParallelByEvent = filterParallelByAssignmentType(
                    normalizedEventIds,
                    parallelByEvent,
                    assignmentType
            );

            Map<Long, EventAssignmentConsistencyReport> reports =
                    eventAssignmentConsistencyService.compareSnapshotGroups(
                            normalizedEventIds,
                            legacyByEvent,
                            filteredParallelByEvent
                    );

            logReports(operation, reports, elapsedMillis(startedAt), true);
        } catch (RuntimeException exception) {
            logTechnicalFailure(operation, exception);
        }
    }

    private void compareEvent(
            String operation,
            Supplier<Optional<CelebrationEvent>> legacyEventSupplier
    ) {
        long startedAt = System.nanoTime();
        try {
            Optional<CelebrationEvent> legacyEvent = legacyEventSupplier.get();
            if (legacyEvent.isEmpty()) {
                LOGGER.warn("EventAssignment shadow read skipped: operation={}, reason=legacy_event_not_found", operation);
                return;
            }

            Long eventId = legacyEvent.get().getId();
            List<EventAssignmentSnapshot> parallelAssignments =
                    eventAssignmentReadService.findAllByEventId(eventId);
            EventAssignmentConsistencyReport report =
                    eventAssignmentConsistencyService.compareEvent(legacyEvent.get(), parallelAssignments);

            logReports(operation, Map.of(eventId, report), elapsedMillis(startedAt), false);
        } catch (RuntimeException exception) {
            logTechnicalFailure(operation, exception);
        }
    }

    private void logReports(
            String operation,
            Map<Long, EventAssignmentConsistencyReport> reports,
            long durationMillis,
            boolean partial
    ) {
        int eventCount = reports.size();
        int participantCount = reports.values().stream()
                .mapToInt(EventAssignmentConsistencyReport::legacyAssignmentCount)
                .sum();
        List<EventAssignmentConsistencyIssue> issues = reports.values().stream()
                .flatMap(report -> report.issues().stream())
                .toList();

        if (issues.isEmpty()) {
            logConsistent(operation, eventCount, participantCount, durationMillis, partial);
            return;
        }

        long inconsistentEventCount = reports.values().stream()
                .filter(report -> !report.consistent())
                .count();
        LOGGER.warn(
                "EventAssignment shadow read divergence: operation={}, events={}, inconsistentEvents={}, issues={}, durationMs={}, partial={}",
                operation,
                eventCount,
                inconsistentEventCount,
                issues.size(),
                durationMillis,
                partial
        );
        issues.stream()
                .limit(MAX_ISSUE_DETAILS)
                .forEach(issue -> LOGGER.warn(
                        "EventAssignment shadow read issue: operation={}, eventId={}, personId={}, issueType={}, legacyType={}, parallelType={}",
                        operation,
                        issue.eventId(),
                        issue.personId(),
                        issue.issueType(),
                        issue.legacyType(),
                        issue.parallelType()
                ));
        if (issues.size() > MAX_ISSUE_DETAILS) {
            LOGGER.warn(
                    "EventAssignment shadow read issue details truncated: operation={}, omittedIssues={}",
                    operation,
                    issues.size() - MAX_ISSUE_DETAILS
            );
        }
    }

    private void logConsistent(
            String operation,
            int eventCount,
            int participantCount,
            long durationMillis,
            boolean partial
    ) {
        LOGGER.debug(
                "EventAssignment shadow read consistent: operation={}, events={}, participants={}, durationMs={}, partial={}",
                operation,
                eventCount,
                participantCount,
                durationMillis,
                partial
        );
    }

    private void logTechnicalFailure(String operation, RuntimeException exception) {
        LOGGER.warn(
                "EventAssignment shadow read failed: operation={}, exceptionType={}, message={}",
                operation,
                exception.getClass().getName(),
                exception.getMessage()
        );
    }

    private List<CelebrationEvent> safeEvents(Collection<CelebrationEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<EventAssignmentSnapshot> safeSnapshots(List<EventAssignmentSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        return snapshots.stream()
                .filter(Objects::nonNull)
                .sorted(EventAssignmentSnapshot.deterministicOrder())
                .toList();
    }

    private List<Long> normalizeEventIds(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }
        return eventIds.stream()
                .filter(Objects::nonNull)
                .filter(eventId -> eventId > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Long, List<EventAssignmentSnapshot>> groupSnapshotsByRequestedEvent(
            List<Long> eventIds,
            List<EventAssignmentSnapshot> snapshots
    ) {
        Map<Long, List<EventAssignmentSnapshot>> grouped = new LinkedHashMap<>();
        eventIds.forEach(eventId -> grouped.put(eventId, List.of()));

        snapshots.stream()
                .filter(snapshot -> grouped.containsKey(snapshot.eventId()))
                .collect(Collectors.groupingBy(
                        EventAssignmentSnapshot::eventId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                values -> values.stream()
                                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                                        .toList()
                        )
                ))
                .forEach(grouped::put);

        return Collections.unmodifiableMap(new LinkedHashMap<>(grouped));
    }

    private Map<Long, List<EventAssignmentSnapshot>> filterParallelByAssignmentType(
            List<Long> eventIds,
            Map<Long, List<EventAssignmentSnapshot>> parallelByEvent,
            EventAssignmentType assignmentType
    ) {
        Map<Long, List<EventAssignmentSnapshot>> result = new LinkedHashMap<>();
        eventIds.forEach(eventId -> result.put(
                eventId,
                parallelByEvent.getOrDefault(eventId, List.of()).stream()
                        .filter(snapshot -> assignmentType == null || snapshot.assignmentType() == assignmentType)
                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                        .toList()
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
