package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.mapper.PersonUnavailabilityMapper;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.PersonUnavailabilityAssignmentConflictProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.PersonUnavailabilityConflictService;
import com.eventoscelebrativos.service.PersonUnavailabilityService;
import com.eventoscelebrativos.service.ScheduleConflictNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PersonUnavailabilityServiceImpl implements PersonUnavailabilityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON_LENGTH = 500;

    private final PersonUnavailabilityRepository personUnavailabilityRepository;
    private final PersonRepository personRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final PersonUnavailabilityConflictService personUnavailabilityConflictService;
    private final AdminRoleMutexService adminRoleMutexService;
    private final ScheduleConflictNotificationService scheduleConflictNotificationService;
    private final PersonUnavailabilityMapper personUnavailabilityMapper;
    private final Clock clock;

    public PersonUnavailabilityServiceImpl(
            PersonUnavailabilityRepository personUnavailabilityRepository,
            PersonRepository personRepository,
            EventAssignmentRepository eventAssignmentRepository,
            NotificationRepository notificationRepository,
            PersonUnavailabilityConflictService personUnavailabilityConflictService,
            AdminRoleMutexService adminRoleMutexService,
            ScheduleConflictNotificationService scheduleConflictNotificationService,
            PersonUnavailabilityMapper personUnavailabilityMapper,
            Clock clock
    ) {
        this.personUnavailabilityRepository = personUnavailabilityRepository;
        this.personRepository = personRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.notificationRepository = notificationRepository;
        this.personUnavailabilityConflictService = personUnavailabilityConflictService;
        this.adminRoleMutexService = adminRoleMutexService;
        this.scheduleConflictNotificationService = scheduleConflictNotificationService;
        this.personUnavailabilityMapper = personUnavailabilityMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonUnavailabilityResponseDTO> findMine(
            Long personId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int page,
            int size
    ) {
        validateQueryRange(startAt, endAt, page, size);
        Person person = findAuthenticatedPerson(personId);

        Page<PersonUnavailability> result = personUnavailabilityRepository.findByPersonIdIntersecting(
                person.getId(),
                startAt,
                endAt,
                PageRequest.of(page, size)
        );

        return result.map(personUnavailabilityMapper::toDto);
    }

    @Override
    @Transactional
    public PersonUnavailabilityResponseDTO create(Long personId, PersonUnavailabilityRequestDTO requestDTO) {
        LocalDateTime startAt = requestDTO.getStartAt();
        LocalDateTime endAt = requestDTO.getEndAt();
        validateTemporalRule(startAt, endAt);
        String reason = normalizeReason(requestDTO.getReason());

        // Mutex central (secao 8): sempre antes do lock de Person no fluxo de indisponibilidade.
        adminRoleMutexService.lockAdminRole();
        Person person = lockAuthenticatedPerson(personId);
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);

        personUnavailabilityConflictService.validateNoOverlap(person.getId(), startAt, endAt, null);

        PersonUnavailability entity = new PersonUnavailability(person, startAt, endAt, reason);
        PersonUnavailability saved = personUnavailabilityRepository.save(entity);

        for (Long eventId : eventAssignmentRepository.findEventIdsByPersonIdAndEndAtAfter(person.getId(), currentSecond)) {
            scheduleConflictNotificationService.reconcile(eventId, person.getId(), currentSecond);
        }

        return personUnavailabilityMapper.toDto(saved);
    }

    @Override
    @Transactional
    public PersonUnavailabilityResponseDTO update(Long personId, Long id, PersonUnavailabilityRequestDTO requestDTO) {
        LocalDateTime startAt = requestDTO.getStartAt();
        LocalDateTime endAt = requestDTO.getEndAt();
        validateTemporalRule(startAt, endAt);
        String reason = normalizeReason(requestDTO.getReason());

        adminRoleMutexService.lockAdminRole();
        Person person = lockAuthenticatedPerson(personId);

        PersonUnavailability existing = personUnavailabilityRepository.findByIdAndPersonId(id, person.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Indisponibilidade", id));

        boolean unchanged = existing.getStartAt().equals(startAt)
                && existing.getEndAt().equals(endAt)
                && Objects.equals(existing.getReason(), reason);

        if (unchanged) {
            return personUnavailabilityMapper.toDto(existing);
        }

        LocalDateTime previousStartAt = existing.getStartAt();
        LocalDateTime previousEndAt = existing.getEndAt();

        personUnavailabilityConflictService.validateNoOverlap(person.getId(), startAt, endAt, id);

        existing.setStartAt(startAt);
        existing.setEndAt(endAt);
        existing.setReason(reason);
        PersonUnavailability saved = personUnavailabilityRepository.save(existing);

        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        TreeSet<Long> affectedEventIds = new TreeSet<>();
        collectEventIds(affectedEventIds, eventAssignmentRepository
                .findAssignmentConflictsByPersonIdAndRange(person.getId(), previousStartAt, previousEndAt));
        collectEventIds(affectedEventIds, eventAssignmentRepository
                .findAssignmentConflictsByPersonIdAndRange(person.getId(), startAt, endAt));
        for (Notification notification : notificationRepository.findActiveScheduleConflictsByPersonId(person.getId())) {
            affectedEventIds.add(notification.getReferenceId());
        }
        for (Long eventId : affectedEventIds) {
            scheduleConflictNotificationService.reconcile(eventId, person.getId(), currentSecond);
        }

        return personUnavailabilityMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long personId, Long id) {
        adminRoleMutexService.lockAdminRole();
        Person person = lockAuthenticatedPerson(personId);

        PersonUnavailability existing = personUnavailabilityRepository.findByIdAndPersonId(id, person.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Indisponibilidade", id));

        personUnavailabilityRepository.delete(existing);

        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        for (Notification notification : notificationRepository.findActiveScheduleConflictsByPersonId(person.getId())) {
            scheduleConflictNotificationService.reconcile(notification.getReferenceId(), person.getId(), currentSecond);
        }
    }

    private void collectEventIds(
            Set<Long> target,
            List<PersonUnavailabilityAssignmentConflictProjection> rows
    ) {
        for (PersonUnavailabilityAssignmentConflictProjection row : rows) {
            target.add(row.getEventId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUnavailabilityResponseDTO findByDate(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BadRequestException("startAt e endAt são obrigatórios");
        }
        validateSecondPrecision(startAt, endAt);
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("startAt deve ser anterior a endAt");
        }
        return new AdminUnavailabilityResponseDTO(
                startAt,
                endAt,
                personUnavailabilityConflictService.findUnavailablePeopleOnRange(startAt, endAt)
        );
    }

    private Person findAuthenticatedPerson(Long personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
    }

    private Person lockAuthenticatedPerson(Long personId) {
        return personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
    }

    private void validateTemporalRule(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BadRequestException("startAt e endAt são obrigatórios");
        }
        validateSecondPrecision(startAt, endAt);
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("startAt deve ser anterior a endAt");
        }
        if (startAt.isBefore(LocalDateTime.now(clock).withNano(0))) {
            throw new BadRequestException("startAt não pode ser anterior ao instante atual");
        }
    }

    private void validateQueryRange(LocalDateTime startAt, LocalDateTime endAt, int page, int size) {
        if (startAt == null || endAt == null) {
            throw new BadRequestException("startAt e endAt são obrigatórios");
        }
        validateSecondPrecision(startAt, endAt);
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("startAt deve ser anterior a endAt");
        }
        if (page < 0) {
            throw new BadRequestException("O número da página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }

    private void validateSecondPrecision(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt.getNano() != 0 || endAt.getNano() != 0) {
            throw new TemporalPrecisionNotSupportedException();
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("O motivo deve ter no máximo 500 caracteres");
        }
        return trimmed;
    }
}
