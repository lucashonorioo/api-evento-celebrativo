package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
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

import java.time.LocalDate;
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
    public void validateNoOverlap(Long personId, LocalDate startDate, LocalDate endDate, Long excludeUnavailabilityId) {
        List<PersonUnavailability> overlapping = excludeUnavailabilityId == null
                ? personUnavailabilityRepository.findOverlapping(personId, startDate, endDate)
                : personUnavailabilityRepository.findOverlappingExcludingId(personId, startDate, endDate, excludeUnavailabilityId);

        if (!overlapping.isEmpty()) {
            throw new UnavailabilityOverlapException("O período informado se sobrepõe a uma indisponibilidade já cadastrada.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateNoAssignmentConflict(Long personId, LocalDate startDate, LocalDate endDate) {
        List<PersonUnavailabilityAssignmentConflictProjection> rows =
                eventAssignmentRepository.findAssignmentConflictsByPersonIdAndDateRange(personId, startDate, endDate);

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
                        .comparing(EventAssignmentConflictDTO::getEventDate)
                        .thenComparing(EventAssignmentConflictDTO::getEventTime)
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
    public void validateAvailabilityForEvent(Map<Long, Set<EventAssignmentType>> assignmentTypesByPersonId, LocalDate eventDate) {
        if (assignmentTypesByPersonId == null || assignmentTypesByPersonId.isEmpty()) {
            return;
        }

        List<PersonUnavailabilityPersonProjection> unavailablePeople =
                personUnavailabilityRepository.findByPersonIdsAndDate(assignmentTypesByPersonId.keySet(), eventDate);

        if (unavailablePeople.isEmpty()) {
            return;
        }

        List<PersonUnavailabilityEventConflictDTO> conflicts = unavailablePeople.stream()
                .map(unavailability -> new PersonUnavailabilityEventConflictDTO(
                        unavailability.getPersonId(),
                        unavailability.getPersonName(),
                        sortedAssignmentTypeNames(assignmentTypesByPersonId.get(unavailability.getPersonId())),
                        unavailability.getStartDate(),
                        unavailability.getEndDate()
                ))
                .sorted(Comparator.comparing(PersonUnavailabilityEventConflictDTO::getPersonId))
                .toList();

        throw new PersonUnavailableForEventException(conflicts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnDate(LocalDate date) {
        List<PersonUnavailabilityPersonProjection> rows = personUnavailabilityRepository.findAllByDate(date);

        Map<Long, AdminUnavailabilityPersonDTO> byPersonId = new LinkedHashMap<>();
        for (PersonUnavailabilityPersonProjection row : rows) {
            byPersonId.putIfAbsent(row.getPersonId(), new AdminUnavailabilityPersonDTO(
                    row.getPersonId(),
                    row.getPersonName(),
                    row.getStartDate(),
                    row.getEndDate()
            ));
        }
        return List.copyOf(byPersonId.values());
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
                first.getEventDate(),
                first.getEventTime(),
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
