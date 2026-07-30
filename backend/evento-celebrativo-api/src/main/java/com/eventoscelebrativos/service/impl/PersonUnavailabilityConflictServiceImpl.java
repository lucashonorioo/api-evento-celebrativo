package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityRangeDTO;
import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityAssignmentConflictException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PersonUnavailabilityConflictServiceImpl implements PersonUnavailabilityConflictService {

    private final PersonUnavailabilityRepository personUnavailabilityRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final PersonRepository personRepository;

    public PersonUnavailabilityConflictServiceImpl(
            PersonUnavailabilityRepository personUnavailabilityRepository,
            EventAssignmentRepository eventAssignmentRepository,
            PersonRepository personRepository
    ) {
        this.personUnavailabilityRepository = personUnavailabilityRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.personRepository = personRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateNoOverlap(Long personId, LocalDateTime startAt, LocalDateTime endAt, Long excludeUnavailabilityId) {
        List<PersonUnavailability> overlapping = excludeUnavailabilityId == null
                ? personUnavailabilityRepository.findOverlapping(personId, startAt, endAt)
                : personUnavailabilityRepository.findOverlappingExcludingId(personId, startAt, endAt, excludeUnavailabilityId);

        if (!overlapping.isEmpty()) {
            throw new UnavailabilityOverlapException("O período informado se sobrepõe a uma indisponibilidade já cadastrada.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateNoAssignmentConflict(Long personId, LocalDateTime startAt, LocalDateTime endAt) {
        List<PersonUnavailabilityAssignmentConflictProjection> rows =
                eventAssignmentRepository.findAssignmentConflictsByPersonIdAndRange(personId, startAt, endAt);

        if (rows.isEmpty()) {
            return;
        }

        Map<Long, List<PersonUnavailabilityAssignmentConflictProjection>> rowsByEvent = new LinkedHashMap<>();
        for (PersonUnavailabilityAssignmentConflictProjection row : rows) {
            rowsByEvent.computeIfAbsent(row.getEventId(), id -> new ArrayList<>()).add(row);
        }

        List<EventAssignmentConflictDTO> conflicts = rowsByEvent.values().stream()
                .map(this::toEventAssignmentConflictDTO)
                .sorted(Comparator
                        .comparing(EventAssignmentConflictDTO::getStartAt)
                        .thenComparing(EventAssignmentConflictDTO::getEventId))
                .toList();

        throw new UnavailabilityAssignmentConflictException(conflicts);
    }

    @Override
    @Transactional
    public void lockPersonsInOrder(Collection<Long> personIds) {
        if (personIds == null || personIds.isEmpty()) {
            return;
        }
        List<Long> ordered = personIds.stream().distinct().sorted().toList();
        for (Long personId : ordered) {
            personRepository.findByIdForUpdate(personId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateAvailabilityForEvent(
            Map<Long, Set<EventAssignmentType>> assignmentTypesByPersonId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        if (assignmentTypesByPersonId == null || assignmentTypesByPersonId.isEmpty()) {
            return;
        }

        List<PersonUnavailabilityPersonProjection> unavailablePeople =
                personUnavailabilityRepository.findByPersonIdsAndRange(assignmentTypesByPersonId.keySet(), startAt, endAt);

        if (unavailablePeople.isEmpty()) {
            return;
        }

        List<PersonUnavailabilityEventConflictDTO> conflicts = unavailablePeople.stream()
                .map(unavailability -> new PersonUnavailabilityEventConflictDTO(
                        unavailability.getPersonId(),
                        unavailability.getPersonName(),
                        sortedAssignmentTypeNames(assignmentTypesByPersonId.get(unavailability.getPersonId())),
                        unavailability.getStartAt(),
                        unavailability.getEndAt()
                ))
                .sorted(Comparator.comparing(PersonUnavailabilityEventConflictDTO::getPersonId)
                        .thenComparing(PersonUnavailabilityEventConflictDTO::getStartAt))
                .toList();

        throw new PersonUnavailableForEventException(conflicts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnRange(LocalDateTime startAt, LocalDateTime endAt) {
        List<PersonUnavailabilityPersonProjection> rows = personUnavailabilityRepository.findAllByRange(startAt, endAt);

        Map<Long, String> nameByPersonId = new LinkedHashMap<>();
        Map<Long, List<AdminUnavailabilityRangeDTO>> rangesByPersonId = new LinkedHashMap<>();
        for (PersonUnavailabilityPersonProjection row : rows) {
            nameByPersonId.putIfAbsent(row.getPersonId(), row.getPersonName());
            rangesByPersonId
                    .computeIfAbsent(row.getPersonId(), id -> new ArrayList<>())
                    .add(new AdminUnavailabilityRangeDTO(row.getStartAt(), row.getEndAt()));
        }

        return nameByPersonId.entrySet().stream()
                .map(entry -> new AdminUnavailabilityPersonDTO(
                        entry.getKey(),
                        entry.getValue(),
                        rangesByPersonId.getOrDefault(entry.getKey(), List.of())
                ))
                .toList();
    }

    private EventAssignmentConflictDTO toEventAssignmentConflictDTO(List<PersonUnavailabilityAssignmentConflictProjection> rows) {
        PersonUnavailabilityAssignmentConflictProjection first = rows.get(0);
        List<String> assignments = rows.stream()
                .map(row -> EventAssignmentType.valueOf(row.getAssignmentType()))
                .sorted()
                .map(Enum::name)
                .toList();

        return new EventAssignmentConflictDTO(
                first.getEventId(),
                first.getEventName(),
                first.getStartAt(),
                first.getEndAt(),
                assignments
        );
    }

    private List<String> sortedAssignmentTypeNames(Set<EventAssignmentType> assignmentTypes) {
        if (assignmentTypes == null || assignmentTypes.isEmpty()) {
            return List.of();
        }
        return assignmentTypes.stream().sorted().map(Enum::name).toList();
    }
}
