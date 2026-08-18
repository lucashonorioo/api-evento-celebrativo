package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.ParticipationResponseMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.ParticipationResponseSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EventParticipationResponseServiceImpl implements EventParticipationResponseService {

    private static final int MAX_DECLINE_REASON_LENGTH = 500;

    private final EventParticipationResponseRepository eventParticipationResponseRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final PersonRepository personRepository;
    private final CelebrationEventRepository celebrationEventRepository;
    private final ParticipationResponseMapper participationResponseMapper;
    private final Clock clock;

    public EventParticipationResponseServiceImpl(
            EventParticipationResponseRepository eventParticipationResponseRepository,
            EventAssignmentRepository eventAssignmentRepository,
            PersonRepository personRepository,
            CelebrationEventRepository celebrationEventRepository,
            ParticipationResponseMapper participationResponseMapper,
            Clock clock
    ) {
        this.eventParticipationResponseRepository = eventParticipationResponseRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.personRepository = personRepository;
        this.celebrationEventRepository = celebrationEventRepository;
        this.participationResponseMapper = participationResponseMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipationResponseResponseDTO respond(Long personId, Long eventId, ParticipationResponseRequestDTO requestDTO) {
        validateEventId(eventId);

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));

        // Lock do evento ANTES do assignment (mesma ordem global usada em todo o dominio: Event ->
        // Assignment), para serializar corretamente com um cancelamento concorrente (secao 24): quem
        // vencer o lock decide o resultado da corrida, sem leitura simples de status antes do lock.
        CelebrationEvent event = celebrationEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", eventId));

        if (event.isCancelled()) {
            throw new LifecycleConflictException(
                    "Nao e possivel responder a participacao de um evento cancelado.",
                    "CELEBRATION_EVENT_CANCELLED_PARTICIPATION_REJECTED"
            );
        }

        List<EventAssignment> lockedAssignments =
                eventAssignmentRepository.findAllByEventIdAndPersonIdForUpdate(eventId, person.getId());
        if (lockedAssignments.isEmpty()) {
            throw new ConflictException("A pessoa autenticada nao possui atribuicao neste evento");
        }

        validateEventNotStarted(event);

        ParticipationStatus status = parseStatus(requestDTO == null ? null : requestDTO.getStatus());
        String declineReason = normalizeDeclineReason(status, requestDTO == null ? null : requestDTO.getDeclineReason());

        EventParticipationResponse existing = eventParticipationResponseRepository
                .findByEventIdAndPersonId(eventId, person.getId())
                .orElse(null);

        LocalDateTime respondedAt;
        if (existing == null) {
            respondedAt = LocalDateTime.now(clock);
            EventParticipationResponse response =
                    new EventParticipationResponse(event, person, status, declineReason, respondedAt);
            eventParticipationResponseRepository.save(response);
        } else if (existing.getStatus() != status || !Objects.equals(existing.getDeclineReason(), declineReason)) {
            respondedAt = LocalDateTime.now(clock);
            existing.setStatus(status);
            existing.setDeclineReason(declineReason);
            existing.setRespondedAt(respondedAt);
        } else {
            respondedAt = existing.getRespondedAt();
        }

        return participationResponseMapper.toDto(eventId, status, declineReason, respondedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ParticipationResponseSnapshot> findByPersonIdAndEventIds(Long personId, Collection<Long> eventIds) {
        if (personId == null || eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return eventParticipationResponseRepository.findAllByPersonIdAndEventIdIn(personId, eventIds).stream()
                .collect(Collectors.toMap(
                        response -> response.getEvent().getId(),
                        this::toSnapshot
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ParticipationResponseSnapshot> findByEventId(Long eventId) {
        if (eventId == null) {
            return Collections.emptyMap();
        }
        return eventParticipationResponseRepository.findAllByEventId(eventId).stream()
                .collect(Collectors.toMap(
                        response -> response.getPerson().getId(),
                        this::toSnapshot
                ));
    }

    @Override
    @Transactional
    public void retainOnlyForPersonIds(Long eventId, Collection<Long> personIdsStillAssigned) {
        validateEventId(eventId);
        if (personIdsStillAssigned == null || personIdsStillAssigned.isEmpty()) {
            eventParticipationResponseRepository.deleteAllByEventId(eventId);
            return;
        }
        eventParticipationResponseRepository.deleteAllByEventIdAndPersonIdNotIn(eventId, personIdsStillAssigned);
    }

    private ParticipationResponseSnapshot toSnapshot(EventParticipationResponse response) {
        return new ParticipationResponseSnapshot(
                response.getEvent().getId(),
                response.getPerson().getId(),
                response.getStatus(),
                response.getDeclineReason(),
                response.getRespondedAt()
        );
    }

    private void validateEventNotStarted(CelebrationEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(event.getStartAt())) {
            throw new ConflictException("Nao e possivel responder a participacao apos o inicio do evento");
        }
    }

    private ParticipationStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("O campo status e obrigatorio");
        }
        ParticipationStatus status;
        try {
            status = ParticipationStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Status de participacao invalido: " + rawStatus);
        }
        if (status == ParticipationStatus.PENDING) {
            throw new BadRequestException("O status PENDING nao pode ser definido diretamente");
        }
        return status;
    }

    private String normalizeDeclineReason(ParticipationStatus status, String rawDeclineReason) {
        if (status == ParticipationStatus.CONFIRMED) {
            return null;
        }
        if (rawDeclineReason == null) {
            return null;
        }
        String trimmed = rawDeclineReason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DECLINE_REASON_LENGTH) {
            throw new BadRequestException("O motivo da recusa deve ter no maximo 500 caracteres");
        }
        return trimmed;
    }

    private void validateEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BusinessException("O Id do evento deve ser positivo e nao nulo");
        }
    }
}
