package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityRangeDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import com.eventoscelebrativos.dto.response.StartedAssignmentConflictDTO;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityConflictWithStartedAssignmentException;
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
    public void validateNoStartedAssignmentConflict(
            Long personId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime currentSecond
    ) {
        List<PersonUnavailabilityAssignmentConflictProjection> rows =
                eventAssignmentRepository.findStartedAssignmentConflictsByPersonIdAndRange(personId, startAt, endAt, currentSecond);

        if (rows.isEmpty()) {
            return;
        }

        // A partir da V12, uma pessoa possui no maximo uma funcao por evento: no maximo uma linha
        // por eventId ja e garantido pelo banco, sem necessidade de agrupamento.
        List<StartedAssignmentConflictDTO> conflicts = rows.stream()
                .map(row -> new StartedAssignmentConflictDTO(
                        row.getEventId(), row.getEventName(), row.getStartAt(), row.getEndAt(), row.getAssignmentType()))
                .sorted(Comparator
                        .comparing(StartedAssignmentConflictDTO::getEventStartAt)
                        .thenComparing(StartedAssignmentConflictDTO::getEventId))
                .toList();

        throw new UnavailabilityConflictWithStartedAssignmentException(conflicts);
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

    private List<String> sortedAssignmentTypeNames(Set<EventAssignmentType> assignmentTypes) {
        if (assignmentTypes == null || assignmentTypes.isEmpty()) {
            return List.of();
        }
        return assignmentTypes.stream().sorted().map(Enum::name).toList();
    }
}
