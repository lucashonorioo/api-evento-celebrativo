package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.model.EventAssignmentType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente central de disponibilidade: centraliza sobreposição de períodos, conflito de
 * indisponibilidade com EventAssignment existente, validação em lote para escalas e a
 * consulta administrativa por data. Todo fluxo capaz de gerar conflito entre
 * PersonUnavailability e EventAssignment passa por aqui para preservar o invariante
 * em um único lugar.
 */
public interface PersonUnavailabilityConflictService {

    void validateNoOverlap(Long personId, LocalDate startDate, LocalDate endDate, Long excludeUnavailabilityId);

    void validateNoAssignmentConflict(Long personId, LocalDate startDate, LocalDate endDate);

    void lockPersonsInOrder(Collection<Long> personIds);

    void validateAvailabilityForEvent(Map<Long, Set<EventAssignmentType>> assignmentTypesByPersonId, LocalDate eventDate);

    List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnDate(LocalDate date);
}
