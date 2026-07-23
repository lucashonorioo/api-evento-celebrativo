package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.EventAssignmentAuditEventDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditIssueDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditResponseDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditSummaryDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.projection.LegacyEventAssignmentProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssue;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssueType;
import com.eventoscelebrativos.service.EventAssignmentConsistencyReport;
import com.eventoscelebrativos.service.EventAssignmentConsistencyService;
import com.eventoscelebrativos.service.EventAssignmentOperationalAuditService;
import com.eventoscelebrativos.service.EventAssignmentReadService;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventAssignmentOperationalAuditServiceImpl implements EventAssignmentOperationalAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventAssignmentOperationalAuditServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final CelebrationEventRepository celebrationEventRepository;
    private final EventAssignmentReadService eventAssignmentReadService;
    private final EventAssignmentConsistencyService eventAssignmentConsistencyService;

    public EventAssignmentOperationalAuditServiceImpl(
            CelebrationEventRepository celebrationEventRepository,
            EventAssignmentReadService eventAssignmentReadService,
            EventAssignmentConsistencyService eventAssignmentConsistencyService
    ) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.eventAssignmentReadService = eventAssignmentReadService;
        this.eventAssignmentConsistencyService = eventAssignmentConsistencyService;
    }

    @Override
    @Transactional(readOnly = true)
    public EventAssignmentAuditResponseDTO audit(
            LocalDate startDate,
            LocalDate endDate,
            Long eventId,
            int page,
            int size,
            boolean includeDetails
    ) {
        long startedAt = System.nanoTime();
        validateFilters(startDate, endDate, eventId, page, size);

        Page<Long> eventPage = findEventPage(startDate, endDate, eventId, page, size);
        List<Long> eventIds = List.copyOf(eventPage.getContent());
        Map<Long, EventAssignmentConsistencyReport> reports = eventIds.isEmpty()
                ? Map.of()
                : auditEvents(eventIds);
        EventAssignmentAuditSummaryDTO summary = summarize(eventIds, reports);
        List<EventAssignmentAuditEventDTO> events = includeDetails ? detailedEvents(eventIds, reports) : null;

        LOGGER.info(
                "event-assignment operational audit eventsChecked={} inconsistentEvents={} totalIssues={} durationMs={}",
                summary.eventsChecked(),
                summary.inconsistentEvents(),
                summary.totalIssues(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );

        return new EventAssignmentAuditResponseDTO(
                summary,
                eventPage.getNumber(),
                eventPage.getSize(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages(),
                eventPage.getNumberOfElements(),
                eventPage.isEmpty(),
                events
        );
    }

    private Page<Long> findEventPage(LocalDate startDate, LocalDate endDate, Long eventId, int page, int size) {
        if (eventId != null) {
            if (!celebrationEventRepository.existsById(eventId)) {
                throw new ResourceNotFoundException("Evento celebrativo", eventId);
            }
            return celebrationEventRepository.findEventIdForAssignmentAudit(
                    PageRequest.of(0, 1),
                    eventId,
                    startDate,
                    endDate
            );
        }

        return celebrationEventRepository.findEventIdsForAssignmentAudit(
                PageRequest.of(page, size),
                startDate,
                endDate
        );
    }

    private Map<Long, EventAssignmentConsistencyReport> auditEvents(List<Long> eventIds) {
        Map<Long, List<EventAssignmentSnapshot>> legacyAssignments = findLegacyAssignments(eventIds);
        Map<Long, List<EventAssignmentSnapshot>> parallelAssignments = eventAssignmentReadService.findAllByEventIds(eventIds);
        return eventAssignmentConsistencyService.compareSnapshotGroups(eventIds, legacyAssignments, parallelAssignments);
    }

    private Map<Long, List<EventAssignmentSnapshot>> findLegacyAssignments(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<EventAssignmentSnapshot>> result = new LinkedHashMap<>();
        eventIds.forEach(eventId -> result.put(eventId, List.of()));

        celebrationEventRepository.findLegacyEventAssignmentsForAudit(eventIds).stream()
                .map(this::toLegacySnapshot)
                .collect(Collectors.groupingBy(
                        EventAssignmentSnapshot::eventId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                snapshots -> snapshots.stream()
                                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                                        .toList()
                        )
                ))
                .forEach(result::put);

        return Collections.unmodifiableMap(result);
    }

    private EventAssignmentSnapshot toLegacySnapshot(LegacyEventAssignmentProjection projection) {
        return new EventAssignmentSnapshot(
                null,
                projection.getEventId(),
                projection.getPersonId(),
                toAssignmentType(projection.getPersonType()),
                null,
                projection.getPersonType()
        );
    }

    private EventAssignmentType toAssignmentType(String personType) {
        if (personType == null) {
            return null;
        }
        return switch (personType) {
            case "priest" -> EventAssignmentType.PRIEST;
            case "reader" -> EventAssignmentType.READER;
            case "commentator" -> EventAssignmentType.COMMENTATOR;
            case "minister_of_the_word" -> EventAssignmentType.MINISTER_OF_THE_WORD;
            case "eucharistic_minister" -> EventAssignmentType.EUCHARISTIC_MINISTER;
            default -> null;
        };
    }

    private EventAssignmentAuditSummaryDTO summarize(
            List<Long> eventIds,
            Map<Long, EventAssignmentConsistencyReport> reports
    ) {
        List<EventAssignmentConsistencyReport> orderedReports = orderedReports(eventIds, reports);
        Map<EventAssignmentConsistencyIssueType, Long> issueCounts = orderedReports.stream()
                .flatMap(report -> report.issues().stream())
                .collect(Collectors.groupingBy(
                        EventAssignmentConsistencyIssue::issueType,
                        () -> new EnumMap<>(EventAssignmentConsistencyIssueType.class),
                        Collectors.counting()
                ));

        int eventsChecked = orderedReports.size();
        int inconsistentEvents = (int) orderedReports.stream()
                .filter(report -> !report.consistent())
                .count();

        return new EventAssignmentAuditSummaryDTO(
                eventsChecked,
                eventsChecked - inconsistentEvents,
                inconsistentEvents,
                orderedReports.stream().mapToInt(EventAssignmentConsistencyReport::legacyAssignmentCount).sum(),
                orderedReports.stream().mapToInt(EventAssignmentConsistencyReport::parallelAssignmentCount).sum(),
                orderedReports.stream().mapToInt(report -> report.issues().size()).sum(),
                count(issueCounts, EventAssignmentConsistencyIssueType.MISSING_PARALLEL_ASSIGNMENT),
                count(issueCounts, EventAssignmentConsistencyIssueType.EXTRA_PARALLEL_ASSIGNMENT),
                count(issueCounts, EventAssignmentConsistencyIssueType.ASSIGNMENT_TYPE_MISMATCH),
                count(issueCounts, EventAssignmentConsistencyIssueType.DUPLICATE_PARALLEL_ASSIGNMENT),
                count(issueCounts, EventAssignmentConsistencyIssueType.MULTIPLE_PRIESTS),
                count(issueCounts, EventAssignmentConsistencyIssueType.UNKNOWN_LEGACY_PERSON_TYPE)
        );
    }

    private List<EventAssignmentAuditEventDTO> detailedEvents(
            List<Long> eventIds,
            Map<Long, EventAssignmentConsistencyReport> reports
    ) {
        return orderedReports(eventIds, reports).stream()
                .filter(report -> !report.consistent())
                .map(this::toEventDto)
                .toList();
    }

    private List<EventAssignmentConsistencyReport> orderedReports(
            List<Long> eventIds,
            Map<Long, EventAssignmentConsistencyReport> reports
    ) {
        return eventIds.stream()
                .map(reports::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private EventAssignmentAuditEventDTO toEventDto(EventAssignmentConsistencyReport report) {
        List<EventAssignmentAuditIssueDTO> issues = report.issues().stream()
                .map(this::toIssueDto)
                .toList();
        return new EventAssignmentAuditEventDTO(
                report.eventId(),
                report.consistent(),
                report.legacyAssignmentCount(),
                report.parallelAssignmentCount(),
                issues.size(),
                issues
        );
    }

    private EventAssignmentAuditIssueDTO toIssueDto(EventAssignmentConsistencyIssue issue) {
        return new EventAssignmentAuditIssueDTO(
                issue.issueType(),
                issue.eventId(),
                issue.personId(),
                issue.legacyType(),
                issue.parallelType()
        );
    }

    private int count(Map<EventAssignmentConsistencyIssueType, Long> issueCounts, EventAssignmentConsistencyIssueType issueType) {
        return Math.toIntExact(issueCounts.getOrDefault(issueType, 0L));
    }

    private void validateFilters(LocalDate startDate, LocalDate endDate, Long eventId, int page, int size) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("As datas estão inválidas");
        }
        if (eventId != null && eventId <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if (page < 0) {
            throw new BusinessException("A página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }
}
