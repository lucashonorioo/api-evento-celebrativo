package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta administrativa dos conflitos derivados entre EventAssignment e PersonUnavailability.
 * Nenhum estado de conflito e persistido: tudo e calculado a partir dos dados atuais de
 * CelebrationEvent, EventAssignment, Person e PersonUnavailability a cada chamada.
 */
public interface ScheduleUnavailabilityConflictService {

    List<ScheduleUnavailabilityConflictResponseDTO> findByEventId(Long eventId);

    Page<ScheduleUnavailabilityConflictResponseDTO> findByRange(LocalDateTime rangeStart, LocalDateTime rangeEnd, int page, int size);
}
