package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.ScheduleConflictUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.exception.exceptions.TemporalPrecisionNotSupportedException;
import com.eventoscelebrativos.projection.ScheduleConflictUnavailabilityProjection;
import com.eventoscelebrativos.projection.ScheduleUnavailabilityConflictKeyProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.ScheduleUnavailabilityConflictRepository;
import com.eventoscelebrativos.service.ScheduleUnavailabilityConflictService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduleUnavailabilityConflictServiceImpl implements ScheduleUnavailabilityConflictService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScheduleUnavailabilityConflictRepository scheduleUnavailabilityConflictRepository;
    private final CelebrationEventRepository celebrationEventRepository;
    private final Clock clock;

    public ScheduleUnavailabilityConflictServiceImpl(
            ScheduleUnavailabilityConflictRepository scheduleUnavailabilityConflictRepository,
            CelebrationEventRepository celebrationEventRepository,
            Clock clock
    ) {
        this.scheduleUnavailabilityConflictRepository = scheduleUnavailabilityConflictRepository;
        this.celebrationEventRepository = celebrationEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleUnavailabilityConflictResponseDTO> findByEventId(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new BadRequestException("O Id deve ser positivo e não nulo");
        }
        if (!celebrationEventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Evento celebrativo", eventId);
        }

        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        List<ScheduleUnavailabilityConflictKeyProjection> keys =
                scheduleUnavailabilityConflictRepository.findConflictsByEventId(eventId, currentSecond);

        return assembleConflicts(keys);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduleUnavailabilityConflictResponseDTO> findByRange(
            LocalDateTime rangeStart, LocalDateTime rangeEnd, int page, int size
    ) {
        validateRange(rangeStart, rangeEnd, page, size);
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);

        Page<ScheduleUnavailabilityConflictKeyProjection> keyPage = scheduleUnavailabilityConflictRepository
                .findConflictsByRange(rangeStart, rangeEnd, currentSecond, PageRequest.of(page, size));

        List<ScheduleUnavailabilityConflictResponseDTO> content = assembleConflicts(keyPage.getContent());
        return new PageImpl<>(content, keyPage.getPageable(), keyPage.getTotalElements());
    }

    private List<ScheduleUnavailabilityConflictResponseDTO> assembleConflicts(
            List<ScheduleUnavailabilityConflictKeyProjection> keys
    ) {
        if (keys.isEmpty()) {
            return List.of();
        }

        Set<Long> personIds = keys.stream()
                .map(ScheduleUnavailabilityConflictKeyProjection::getPersonId)
                .collect(Collectors.toSet());

        Map<Long, List<ScheduleConflictUnavailabilityProjection>> unavailabilitiesByPerson =
                scheduleUnavailabilityConflictRepository.findUnavailabilitiesByPersonIdIn(personIds).stream()
                        .collect(Collectors.groupingBy(ScheduleConflictUnavailabilityProjection::getPersonId));

        return keys.stream()
                .map(key -> toResponseDTO(key, unavailabilitiesByPerson.getOrDefault(key.getPersonId(), List.of())))
                .toList();
    }

    private ScheduleUnavailabilityConflictResponseDTO toResponseDTO(
            ScheduleUnavailabilityConflictKeyProjection key,
            List<ScheduleConflictUnavailabilityProjection> personUnavailabilities
    ) {
        List<ScheduleConflictUnavailabilityResponseDTO> overlapping = personUnavailabilities.stream()
                .filter(unavailability -> unavailability.getStartAt().isBefore(key.getEventEndAt())
                        && unavailability.getEndAt().isAfter(key.getEventStartAt()))
                .sorted(Comparator.comparing(ScheduleConflictUnavailabilityProjection::getStartAt)
                        .thenComparing(ScheduleConflictUnavailabilityProjection::getEndAt)
                        .thenComparing(ScheduleConflictUnavailabilityProjection::getId))
                .map(unavailability -> new ScheduleConflictUnavailabilityResponseDTO(
                        unavailability.getId(), unavailability.getStartAt(), unavailability.getEndAt()))
                .toList();

        return new ScheduleUnavailabilityConflictResponseDTO(
                key.getEventId(),
                key.getEventName(),
                key.getEventStartAt(),
                key.getEventEndAt(),
                key.getPersonId(),
                key.getPersonName(),
                key.getAssignmentType(),
                overlapping
        );
    }

    private void validateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd, int page, int size) {
        if (rangeStart == null || rangeEnd == null) {
            throw new BadRequestException("startAt e endAt são obrigatórios");
        }
        if (rangeStart.getNano() != 0 || rangeEnd.getNano() != 0) {
            throw new TemporalPrecisionNotSupportedException();
        }
        if (!rangeStart.isBefore(rangeEnd)) {
            throw new BadRequestException("startAt deve ser anterior a endAt");
        }
        if (page < 0) {
            throw new BadRequestException("O número da página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }
}
