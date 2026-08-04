package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityRangeDTO;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityPersonProjection;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonUnavailabilityConflictServiceImpl implements PersonUnavailabilityConflictService {

    private final PersonUnavailabilityRepository personUnavailabilityRepository;
    private final PersonRepository personRepository;

    public PersonUnavailabilityConflictServiceImpl(
            PersonUnavailabilityRepository personUnavailabilityRepository,
            PersonRepository personRepository
    ) {
        this.personUnavailabilityRepository = personUnavailabilityRepository;
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
}
