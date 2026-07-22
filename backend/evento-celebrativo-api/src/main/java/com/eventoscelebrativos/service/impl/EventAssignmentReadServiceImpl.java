package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.EventAssignmentReadService;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventAssignmentReadServiceImpl implements EventAssignmentReadService {

    private final EventAssignmentRepository eventAssignmentRepository;

    public EventAssignmentReadServiceImpl(EventAssignmentRepository eventAssignmentRepository) {
        this.eventAssignmentRepository = eventAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventAssignmentSnapshot> findAllByEventId(Long eventId) {
        validateEventId(eventId);
        return eventAssignmentRepository.findAllByEventIdWithPerson(eventId).stream()
                .map(this::toSnapshot)
                .sorted(EventAssignmentSnapshot.deterministicOrder())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<EventAssignmentSnapshot>> findAllByEventIds(Collection<Long> eventIds) {
        List<Long> distinctEventIds = validateAndNormalizeEventIds(eventIds);
        if (distinctEventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<EventAssignmentSnapshot>> mutableResult = new LinkedHashMap<>();
        distinctEventIds.forEach(eventId -> mutableResult.put(eventId, List.of()));

        eventAssignmentRepository.findAllByEventIdInWithPerson(distinctEventIds).stream()
                .map(this::toSnapshot)
                .collect(java.util.stream.Collectors.groupingBy(
                        EventAssignmentSnapshot::eventId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(),
                                snapshots -> snapshots.stream()
                                        .sorted(EventAssignmentSnapshot.deterministicOrder())
                                        .toList()
                        )
                ))
                .forEach(mutableResult::put);

        return Collections.unmodifiableMap(mutableResult);
    }

    private EventAssignmentSnapshot toSnapshot(EventAssignment assignment) {
        Person person = assignment.getPerson();
        return new EventAssignmentSnapshot(
                assignment.getId(),
                assignment.getEvent().getId(),
                person.getId(),
                assignment.getAssignmentType(),
                person.getName(),
                person.getPersonType()
        );
    }

    private void validateEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("Id do evento e obrigatorio para ler atribuicoes");
        }
    }

    private List<Long> validateAndNormalizeEventIds(Collection<Long> eventIds) {
        if (eventIds == null) {
            throw new BusinessException("Ids de eventos sao obrigatorios");
        }
        for (Long eventId : eventIds) {
            validateEventId(eventId);
        }
        return eventIds.stream()
                .distinct()
                .sorted()
                .toList();
    }
}
