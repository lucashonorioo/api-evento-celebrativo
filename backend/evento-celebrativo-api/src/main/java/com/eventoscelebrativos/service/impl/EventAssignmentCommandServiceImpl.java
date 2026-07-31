package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.MultipleAssignmentsForPersonInEventException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.EventAssignmentCommandService;
import com.eventoscelebrativos.service.EventAssignmentTarget;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventAssignmentCommandServiceImpl implements EventAssignmentCommandService {

    private final EventAssignmentRepository eventAssignmentRepository;
    private final EventParticipationResponseService eventParticipationResponseService;

    public EventAssignmentCommandServiceImpl(
            EventAssignmentRepository eventAssignmentRepository,
            EventParticipationResponseService eventParticipationResponseService
    ) {
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.eventParticipationResponseService = eventParticipationResponseService;
    }

    @Override
    @Transactional
    public void synchronizeAssignments(
            CelebrationEvent event,
            Collection<EventAssignment> currentAssignments,
            Collection<EventAssignmentTarget> targets
    ) {
        validateEvent(event);
        List<EventAssignmentTarget> validatedTargets = validateTargets(event.getId(), targets);
        List<EventAssignment> current = currentAssignments == null ? List.of() : List.copyOf(currentAssignments);

        Map<Long, EventAssignment> currentByPersonId = new HashMap<>();
        for (EventAssignment assignment : current) {
            currentByPersonId.put(assignment.getPerson().getId(), assignment);
        }

        Map<Long, EventAssignmentTarget> targetByPersonId = new LinkedHashMap<>();
        for (EventAssignmentTarget target : validatedTargets) {
            targetByPersonId.put(target.person().getId(), target);
        }

        List<EventAssignment> assignmentsToRemove = current.stream()
                .filter(assignment -> !targetByPersonId.containsKey(assignment.getPerson().getId()))
                .toList();
        if (!assignmentsToRemove.isEmpty()) {
            eventAssignmentRepository.deleteAll(assignmentsToRemove);
        }

        List<EventAssignment> assignmentsToSave = new ArrayList<>();
        for (Map.Entry<Long, EventAssignmentTarget> entry : targetByPersonId.entrySet()) {
            EventAssignment existingAssignment = currentByPersonId.get(entry.getKey());
            EventAssignmentType targetType = entry.getValue().assignmentType();
            if (existingAssignment == null) {
                assignmentsToSave.add(new EventAssignment(event, entry.getValue().person(), targetType));
            } else if (existingAssignment.getAssignmentType() != targetType) {
                existingAssignment.setAssignmentType(targetType);
                assignmentsToSave.add(existingAssignment);
            }
        }
        if (!assignmentsToSave.isEmpty()) {
            eventAssignmentRepository.saveAll(assignmentsToSave);
        }

        eventParticipationResponseService.retainOnlyForPersonIds(event.getId(), targetByPersonId.keySet());
    }

    @Override
    @Transactional
    public void deleteAllForEvent(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("O Id deve ser positivo e nao nulo");
        }
        eventAssignmentRepository.deleteAllByEventId(eventId);
    }

    private void validateEvent(CelebrationEvent event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new BusinessException("Evento valido e obrigatorio para sincronizar atribuicoes");
        }
    }

    private List<EventAssignmentTarget> validateTargets(Long eventId, Collection<EventAssignmentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        List<EventAssignmentTarget> validatedTargets = new ArrayList<>(targets.size());
        Map<Long, EventAssignmentType> seenTypeByPersonId = new HashMap<>();
        for (EventAssignmentTarget target : targets) {
            if (target == null || target.person() == null || target.person().getId() == null || target.assignmentType() == null) {
                throw new BusinessException("Pessoa e tipo de atribuicao do evento sao obrigatorios");
            }
            Long personId = target.person().getId();
            EventAssignmentType existingType = seenTypeByPersonId.putIfAbsent(personId, target.assignmentType());
            if (existingType != null) {
                throw new MultipleAssignmentsForPersonInEventException(
                        eventId, personId, EnumSet.of(existingType, target.assignmentType()));
            }
            validatedTargets.add(target);
        }
        return validatedTargets;
    }
}
