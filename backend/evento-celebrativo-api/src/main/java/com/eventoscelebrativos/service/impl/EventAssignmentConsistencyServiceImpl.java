package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssue;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssueType;
import com.eventoscelebrativos.service.EventAssignmentConsistencyReport;
import com.eventoscelebrativos.service.EventAssignmentConsistencyService;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import com.eventoscelebrativos.service.LegacyEventAssignmentSnapshotResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventAssignmentConsistencyServiceImpl implements EventAssignmentConsistencyService {

    private final LegacyEventAssignmentSnapshotResolver legacySnapshotResolver;

    public EventAssignmentConsistencyServiceImpl(LegacyEventAssignmentSnapshotResolver legacySnapshotResolver) {
        this.legacySnapshotResolver = legacySnapshotResolver;
    }

    @Override
    public EventAssignmentConsistencyReport compareEvent(
            CelebrationEvent legacyEvent,
            List<EventAssignmentSnapshot> parallelAssignments
    ) {
        validateEvent(legacyEvent);
        Long eventId = legacyEvent.getId();
        List<EventAssignmentSnapshot> legacySnapshots = legacySnapshotResolver.resolve(legacyEvent);
        return compareSnapshots(eventId, legacySnapshots, parallelAssignments);
    }

    @Override
    public EventAssignmentConsistencyReport compareSnapshots(
            Long eventId,
            List<EventAssignmentSnapshot> legacyAssignments,
            List<EventAssignmentSnapshot> parallelAssignments
    ) {
        validateEventId(eventId);
        List<EventAssignmentSnapshot> legacySnapshots = safeSnapshots(legacyAssignments);
        List<EventAssignmentSnapshot> parallelSnapshots = safeSnapshots(parallelAssignments);
        List<EventAssignmentConsistencyIssue> issues = new ArrayList<>();

        addUnknownLegacyPersonTypeIssues(legacySnapshots, issues);
        addMultiplePriestsIssues(legacySnapshots, issues);
        addMultiplePriestsIssues(parallelSnapshots, issues);
        addDuplicateParallelIssues(parallelSnapshots, issues);
        addComparisonIssues(eventId, legacySnapshots, parallelSnapshots, issues);

        return new EventAssignmentConsistencyReport(
                eventId,
                legacySnapshots.size(),
                parallelSnapshots.size(),
                issues.stream()
                        .sorted(issueOrder())
                        .toList()
        );
    }

    @Override
    public Map<Long, EventAssignmentConsistencyReport> compareEvents(
            Collection<CelebrationEvent> legacyEvents,
            Map<Long, List<EventAssignmentSnapshot>> parallelAssignments
    ) {
        if (legacyEvents == null) {
            throw new BusinessException("Eventos legados sao obrigatorios para auditoria");
        }
        Map<Long, List<EventAssignmentSnapshot>> safeParallelAssignments = parallelAssignments == null
                ? Map.of()
                : parallelAssignments;

        Map<Long, EventAssignmentConsistencyReport> result = new LinkedHashMap<>();
        legacyEvents.stream()
                .sorted(Comparator.comparing(CelebrationEvent::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(event -> result.put(
                        event.getId(),
                        compareEvent(event, safeParallelAssignments.getOrDefault(event.getId(), List.of()))
                ));
        return java.util.Collections.unmodifiableMap(result);
    }

    @Override
    public Map<Long, EventAssignmentConsistencyReport> compareSnapshotGroups(
            Collection<Long> eventIds,
            Map<Long, List<EventAssignmentSnapshot>> legacyAssignments,
            Map<Long, List<EventAssignmentSnapshot>> parallelAssignments
    ) {
        if (eventIds == null) {
            throw new BusinessException("Ids de eventos sao obrigatorios para auditoria");
        }
        Map<Long, List<EventAssignmentSnapshot>> safeLegacyAssignments = legacyAssignments == null
                ? Map.of()
                : legacyAssignments;
        Map<Long, List<EventAssignmentSnapshot>> safeParallelAssignments = parallelAssignments == null
                ? Map.of()
                : parallelAssignments;

        Map<Long, EventAssignmentConsistencyReport> result = new LinkedHashMap<>();
        eventIds.stream()
                .distinct()
                .sorted(Comparator.nullsLast(Long::compareTo))
                .peek(this::validateEventId)
                .forEach(eventId -> result.put(
                        eventId,
                        compareSnapshots(
                                eventId,
                                safeLegacyAssignments.getOrDefault(eventId, List.of()),
                                safeParallelAssignments.getOrDefault(eventId, List.of())
                        )
                ));
        return java.util.Collections.unmodifiableMap(result);
    }

    private void addUnknownLegacyPersonTypeIssues(
            List<EventAssignmentSnapshot> legacySnapshots,
            List<EventAssignmentConsistencyIssue> issues
    ) {
        legacySnapshots.stream()
                .filter(snapshot -> snapshot.assignmentType() == null)
                .map(snapshot -> new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.UNKNOWN_LEGACY_PERSON_TYPE,
                        snapshot.eventId(),
                        snapshot.assignmentId(),
                        snapshot.personId(),
                        null,
                        null,
                        snapshot.personType()
                ))
                .forEach(issues::add);
    }

    private void addMultiplePriestsIssues(
            List<EventAssignmentSnapshot> snapshots,
            List<EventAssignmentConsistencyIssue> issues
    ) {
        Map<Long, List<EventAssignmentSnapshot>> priestsByEvent = snapshots.stream()
                .filter(snapshot -> snapshot.assignmentType() == EventAssignmentType.PRIEST)
                .collect(Collectors.groupingBy(EventAssignmentSnapshot::eventId));

        priestsByEvent.values().stream()
                .filter(priests -> priests.size() > 1)
                .flatMap(Collection::stream)
                .map(snapshot -> new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.MULTIPLE_PRIESTS,
                        snapshot.eventId(),
                        snapshot.assignmentId(),
                        snapshot.personId(),
                        snapshot.assignmentId() == null ? EventAssignmentType.PRIEST : null,
                        snapshot.assignmentId() == null ? null : EventAssignmentType.PRIEST,
                        snapshot.personType()
                ))
                .forEach(issues::add);
    }

    private void addDuplicateParallelIssues(
            List<EventAssignmentSnapshot> parallelSnapshots,
            List<EventAssignmentConsistencyIssue> issues
    ) {
        parallelSnapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> new EventPersonKey(snapshot.eventId(), snapshot.personId())))
                .values()
                .stream()
                .filter(duplicates -> duplicates.size() > 1)
                .flatMap(Collection::stream)
                .map(snapshot -> new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.DUPLICATE_PARALLEL_ASSIGNMENT,
                        snapshot.eventId(),
                        snapshot.assignmentId(),
                        snapshot.personId(),
                        null,
                        snapshot.assignmentType(),
                        snapshot.personType()
                ))
                .forEach(issues::add);
    }

    private void addComparisonIssues(
            Long eventId,
            List<EventAssignmentSnapshot> legacySnapshots,
            List<EventAssignmentSnapshot> parallelSnapshots,
            List<EventAssignmentConsistencyIssue> issues
    ) {
        List<EventAssignmentSnapshot> knownLegacySnapshots = legacySnapshots.stream()
                .filter(snapshot -> snapshot.assignmentType() != null)
                .toList();

        Set<Long> legacyPersonIds = legacySnapshots.stream()
                .map(EventAssignmentSnapshot::personId)
                .collect(Collectors.toSet());

        Map<Long, List<EventAssignmentSnapshot>> parallelByPersonId = parallelSnapshots.stream()
                .filter(snapshot -> Objects.equals(eventId, snapshot.eventId()))
                .collect(Collectors.groupingBy(
                        EventAssignmentSnapshot::personId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                snapshots -> snapshots.stream()
                                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                                        .toList()
                        )
                ));

        for (EventAssignmentSnapshot legacySnapshot : knownLegacySnapshots) {
            List<EventAssignmentSnapshot> samePersonParallel = parallelByPersonId.getOrDefault(
                    legacySnapshot.personId(),
                    List.of()
            );
            if (samePersonParallel.isEmpty()) {
                issues.add(new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT,
                        legacySnapshot.eventId(),
                        null,
                        legacySnapshot.personId(),
                        legacySnapshot.assignmentType(),
                        null,
                        legacySnapshot.personType()
                ));
                continue;
            }
            boolean sameTypeExists = samePersonParallel.stream()
                    .anyMatch(parallelSnapshot -> parallelSnapshot.assignmentType() == legacySnapshot.assignmentType());
            if (!sameTypeExists) {
                EventAssignmentSnapshot firstParallel = samePersonParallel.get(0);
                issues.add(new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH,
                        legacySnapshot.eventId(),
                        firstParallel.assignmentId(),
                        legacySnapshot.personId(),
                        legacySnapshot.assignmentType(),
                        firstParallel.assignmentType(),
                        legacySnapshot.personType()
                ));
            }
        }

        parallelSnapshots.stream()
                .filter(snapshot -> Objects.equals(eventId, snapshot.eventId()))
                .filter(snapshot -> !legacyPersonIds.contains(snapshot.personId()))
                .map(snapshot -> new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT,
                        snapshot.eventId(),
                        snapshot.assignmentId(),
                        snapshot.personId(),
                        null,
                        snapshot.assignmentType(),
                        snapshot.personType()
                ))
                .forEach(issues::add);

        parallelSnapshots.stream()
                .filter(snapshot -> !Objects.equals(eventId, snapshot.eventId()))
                .map(snapshot -> new EventAssignmentConsistencyIssue(
                        EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT,
                        snapshot.eventId(),
                        snapshot.assignmentId(),
                        snapshot.personId(),
                        null,
                        snapshot.assignmentType(),
                        snapshot.personType()
                ))
                .forEach(issues::add);
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

    private Comparator<EventAssignmentConsistencyIssue> issueOrder() {
        return Comparator
                .comparing(EventAssignmentConsistencyIssue::eventId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(EventAssignmentConsistencyIssue::issueType)
                .thenComparing(EventAssignmentConsistencyIssue::personId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(EventAssignmentConsistencyIssue::assignmentId, Comparator.nullsLast(Long::compareTo));
    }

    private void validateEvent(CelebrationEvent event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new BusinessException("Evento legado valido e obrigatorio para auditoria");
        }
    }

    private void validateEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("Id do evento e obrigatorio para auditoria");
        }
    }

    private record EventPersonKey(Long eventId, Long personId) {
    }
}
