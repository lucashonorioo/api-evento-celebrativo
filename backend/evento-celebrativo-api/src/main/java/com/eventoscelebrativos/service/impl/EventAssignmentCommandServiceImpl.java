package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public void synchronizeAssignments(CelebrationEvent event, Collection<EventAssignmentTarget> targets) {
        validateEvent(event);
        List<EventAssignmentTarget> validatedTargets = validateTargets(targets);

        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventId(event.getId());
        Map<PersonAssignmentTypeKey, EventAssignment> currentByPair = new HashMap<>();
        for (EventAssignment assignment : currentAssignments) {
            currentByPair.put(
                    new PersonAssignmentTypeKey(assignment.getPerson().getId(), assignment.getAssignmentType()),
                    assignment
            );
        }

        Set<PersonAssignmentTypeKey> targetPairs = new HashSet<>();
        List<EventAssignment> assignmentsToSave = new ArrayList<>();

        for (EventAssignmentTarget target : validatedTargets) {
            PersonAssignmentTypeKey key = new PersonAssignmentTypeKey(target.person().getId(), target.assignmentType());
            targetPairs.add(key);
            if (!currentByPair.containsKey(key)) {
                assignmentsToSave.add(new EventAssignment(event, target.person(), target.assignmentType()));
            }
        }

        List<EventAssignment> assignmentsToRemove = currentAssignments.stream()
                .filter(assignment -> !targetPairs.contains(
                        new PersonAssignmentTypeKey(assignment.getPerson().getId(), assignment.getAssignmentType())
                ))
                .toList();

        if (!assignmentsToRemove.isEmpty()) {
            eventAssignmentRepository.deleteAll(assignmentsToRemove);
        }
        if (!assignmentsToSave.isEmpty()) {
            eventAssignmentRepository.saveAll(assignmentsToSave);
        }

        Set<Long> finalPersonIds = targetPairs.stream()
                .map(PersonAssignmentTypeKey::personId)
                .collect(Collectors.toSet());
        eventParticipationResponseService.retainOnlyForPersonIds(event.getId(), finalPersonIds);
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
        Set<PersonAssignmentTypeKey> seenPairs = new HashSet<>();
        for (EventAssignmentTarget target : targets) {
            if (target == null || target.person() == null || target.person().getId() == null || target.assignmentType() == null) {
                throw new BusinessException("Pessoa e tipo de atribuicao do evento sao obrigatorios");
            }
            if (!seenPairs.add(new PersonAssignmentTypeKey(target.person().getId(), target.assignmentType()))) {
                throw new BusinessException("A mesma pessoa nao pode ocupar a mesma funcao duas vezes na mesma escala");
            }
            validatedTargets.add(target);
        }
        return validatedTargets;
    }

    private record PersonAssignmentTypeKey(Long personId, EventAssignmentType assignmentType) {
    }
}
