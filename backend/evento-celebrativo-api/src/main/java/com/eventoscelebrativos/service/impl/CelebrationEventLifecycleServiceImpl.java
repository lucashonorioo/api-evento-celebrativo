package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.service.CelebrationEventLifecycleService;
import com.eventoscelebrativos.service.PersonMinistryEligibilityResolver;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import com.eventoscelebrativos.service.ScaleParticipantEligibility;
import com.eventoscelebrativos.service.ScheduleConflictNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CelebrationEventLifecycleServiceImpl implements CelebrationEventLifecycleService {

    private final CelebrationEventRepository celebrationEventRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final CelebrationEventMapper celebrationEventMapper;
    private final PersonMinistryEligibilityResolver personMinistryEligibilityResolver;
    private final PersonUnavailabilityConflictService personUnavailabilityConflictService;
    private final AdminRoleMutexService adminRoleMutexService;
    private final ScheduleConflictNotificationService scheduleConflictNotificationService;
    private final Clock clock;

    public CelebrationEventLifecycleServiceImpl(
            CelebrationEventRepository celebrationEventRepository,
            EventAssignmentRepository eventAssignmentRepository,
            CelebrationEventMapper celebrationEventMapper,
            PersonMinistryEligibilityResolver personMinistryEligibilityResolver,
            PersonUnavailabilityConflictService personUnavailabilityConflictService,
            AdminRoleMutexService adminRoleMutexService,
            ScheduleConflictNotificationService scheduleConflictNotificationService,
            Clock clock
    ) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.celebrationEventMapper = celebrationEventMapper;
        this.personMinistryEligibilityResolver = personMinistryEligibilityResolver;
        this.personUnavailabilityConflictService = personUnavailabilityConflictService;
        this.adminRoleMutexService = adminRoleMutexService;
        this.scheduleConflictNotificationService = scheduleConflictNotificationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO cancel(Long eventId) {
        validateId(eventId);

        // Mesmo boundary usado por updateEvent/updateEventScale/deleteEventById (secao 19): nenhuma
        // leitura simples antes do lock.
        CelebrationEvent event = celebrationEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", eventId));

        // Idempotencia (secao 17): CANCELLED -> CANCELLED nao falha mesmo que o evento ja tenha
        // iniciado; a restricao temporal se aplica apenas a uma transicao real.
        if (event.isCancelled()) {
            return celebrationEventMapper.toDto(event);
        }

        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventIdForUpdate(eventId);
        List<Long> assignedPersonIds = distinctSortedPersonIds(currentAssignments);

        // Ordem de locks global (secao 20): evento -> assignments -> mutex ROLE_ADMIN -> pessoas.
        // O mutex e obrigatorio aqui porque reconcile() exige lock ROLE_ADMIN ja adquirido na
        // transacao atual.
        adminRoleMutexService.lockAdminRole();
        if (!assignedPersonIds.isEmpty()) {
            personUnavailabilityConflictService.lockPersonsInOrder(assignedPersonIds);
        }

        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        validateTransitionAllowed(event, currentSecond);

        event.cancel();
        CelebrationEvent saved = celebrationEventRepository.save(event);

        // Evento CANCELLED nunca produz conflito operacional (secao 25): reconciliar aqui resolve
        // qualquer notificacao SCHEDULE_UNAVAILABILITY_CONFLICT ativa para os participantes.
        for (Long personId : assignedPersonIds) {
            scheduleConflictNotificationService.reconcile(eventId, personId, currentSecond);
        }

        return celebrationEventMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO reactivate(Long eventId) {
        validateId(eventId);

        CelebrationEvent event = celebrationEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", eventId));

        // Idempotencia (secao 17): ACTIVE -> ACTIVE nao falha mesmo que o evento ja tenha iniciado.
        if (event.isActive()) {
            return celebrationEventMapper.toDto(event);
        }

        List<EventAssignment> currentAssignments = eventAssignmentRepository.findAllByEventIdForUpdate(eventId);
        List<Long> assignedPersonIds = distinctSortedPersonIds(currentAssignments);

        adminRoleMutexService.lockAdminRole();
        if (!assignedPersonIds.isEmpty()) {
            personUnavailabilityConflictService.lockPersonsInOrder(assignedPersonIds);
        }

        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        validateTransitionAllowed(event, currentSecond);

        // Reativacao e a barreira de seguranca (secao 38): Person/PersonMinistry podem ter mudado
        // enquanto o evento estava CANCELLED, entao toda a escala preservada e revalidada agora, sob o
        // lock de pessoas ja adquirido acima. Falha aqui reverte a transacao inteira: nada e persistido.
        validateAssignmentsEligibleForReactivation(currentAssignments);

        event.reactivate();
        CelebrationEvent saved = celebrationEventRepository.save(event);

        // Indisponibilidade sobreposta nao bloqueia reativacao (secao 35); se ainda existir, o
        // reconciliador recria a notificacao de conflito normalmente.
        for (Long personId : assignedPersonIds) {
            scheduleConflictNotificationService.reconcile(eventId, personId, currentSecond);
        }

        return celebrationEventMapper.toDto(saved);
    }

    private void validateTransitionAllowed(CelebrationEvent event, LocalDateTime currentSecond) {
        if (!currentSecond.isBefore(event.getStartAt())) {
            throw new LifecycleConflictException(
                    "Nao e possivel alterar o lifecycle de um evento que ja iniciou.",
                    "CELEBRATION_EVENT_LIFECYCLE_ALREADY_STARTED"
            );
        }
    }

    /**
     * Reusa {@link PersonMinistryEligibilityResolver} (mesma fonte usada por
     * CelebrationEventServiceImpl para validar escala nova) para exigir, de cada assignment
     * preservado durante o cancelamento: Person existente e ativa, e PersonMinistry correspondente
     * ativo. Nenhuma parte da reativacao e persistida se qualquer assignment for invalido.
     */
    private void validateAssignmentsEligibleForReactivation(List<EventAssignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }

        Map<MinistryType, List<Long>> personIdsByMinistry = new EnumMap<>(MinistryType.class);
        for (EventAssignment assignment : assignments) {
            MinistryType ministryType = MinistryType.valueOf(assignment.getAssignmentType().name());
            personIdsByMinistry
                    .computeIfAbsent(ministryType, key -> new ArrayList<>())
                    .add(assignment.getPerson().getId());
        }

        Map<MinistryType, Map<Long, ScaleParticipantEligibility>> eligibilityByMinistry =
                personMinistryEligibilityResolver.resolve(personIdsByMinistry).stream()
                        .collect(Collectors.groupingBy(
                                ScaleParticipantEligibility::ministryType,
                                Collectors.toMap(ScaleParticipantEligibility::personId, Function.identity())
                        ));

        for (EventAssignment assignment : assignments) {
            MinistryType ministryType = MinistryType.valueOf(assignment.getAssignmentType().name());
            Long personId = assignment.getPerson().getId();
            ScaleParticipantEligibility eligibility = eligibilityByMinistry
                    .getOrDefault(ministryType, Map.of())
                    .get(personId);

            boolean eligible = eligibility != null
                    && eligibility.personFound()
                    && eligibility.ministryAssigned()
                    && eligibility.person().isActive();

            if (!eligible) {
                throw new LifecycleConflictException(
                        "Reativacao bloqueada: a escala atual possui pessoa inativa ou sem funcao "
                                + "ministerial ativa correspondente. Corrija a escala em PUT /eventos/{id}/escala "
                                + "antes de reativar.",
                        "CELEBRATION_EVENT_REACTIVATION_INVALID_SCALE"
                );
            }
        }
    }

    private List<Long> distinctSortedPersonIds(List<EventAssignment> assignments) {
        return assignments.stream()
                .map(assignment -> assignment.getPerson().getId())
                .distinct()
                .sorted()
                .toList();
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
    }
}
