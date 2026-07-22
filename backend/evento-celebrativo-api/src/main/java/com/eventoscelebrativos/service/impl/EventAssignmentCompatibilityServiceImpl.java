package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.EventAssignmentCompatibilityService;
import com.eventoscelebrativos.service.EventAssignmentTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EventAssignmentCompatibilityServiceImpl implements EventAssignmentCompatibilityService {

    private final EventAssignmentRepository eventAssignmentRepository;

    public EventAssignmentCompatibilityServiceImpl(EventAssignmentRepository eventAssignmentRepository) {
        this.eventAssignmentRepository = eventAssignmentRepository;
    }

    @Override
    @Transactional
    public void synchronizeAssignments(CelebrationEvent event, Collection<EventAssignmentTarget> targets) {
        validateEvent(event);
        List<EventAssignmentTarget> validatedTargets = validateTargets(targets);

        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventId(event.getId());
        Map<Long, EventAssignment> currentByPersonId = new HashMap<>();
        for (EventAssignment assignment : currentAssignments) {
            currentByPersonId.put(assignment.getPerson().getId(), assignment);
        }

        Set<Long> targetPersonIds = new HashSet<>();
        List<EventAssignment> assignmentsToSave = new ArrayList<>();

        for (EventAssignmentTarget target : validatedTargets) {
            Long personId = target.person().getId();
            targetPersonIds.add(personId);
            EventAssignment current = currentByPersonId.get(personId);
            if (current == null) {
                assignmentsToSave.add(new EventAssignment(event, target.person(), target.assignmentType()));
            } else if (current.getAssignmentType() != target.assignmentType()) {
                current.setAssignmentType(target.assignmentType());
                assignmentsToSave.add(current);
            }
        }

        List<EventAssignment> assignmentsToRemove = currentAssignments.stream()
                .filter(assignment -> !targetPersonIds.contains(assignment.getPerson().getId()))
                .toList();

        if (!assignmentsToRemove.isEmpty()) {
            eventAssignmentRepository.deleteAll(assignmentsToRemove);
        }
        if (!assignmentsToSave.isEmpty()) {
            eventAssignmentRepository.saveAll(assignmentsToSave);
        }
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

    private List<EventAssignmentTarget> validateTargets(Collection<EventAssignmentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        List<EventAssignmentTarget> validatedTargets = new ArrayList<>(targets.size());
        Set<Long> personIds = new HashSet<>();
        for (EventAssignmentTarget target : targets) {
            if (target == null || target.person() == null || target.person().getId() == null || target.assignmentType() == null) {
                throw new BusinessException("Pessoa e tipo de atribuicao do evento sao obrigatorios");
            }
            if (!personIds.add(target.person().getId())) {
                throw new BusinessException("A mesma pessoa nao pode ocupar mais de uma funcao na mesma escala");
            }
            validatedTargets.add(target);
        }
        return validatedTargets;
    }
}
