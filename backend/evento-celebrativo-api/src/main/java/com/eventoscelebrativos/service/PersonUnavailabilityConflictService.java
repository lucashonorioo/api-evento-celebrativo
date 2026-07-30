package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.model.EventAssignmentType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente central de disponibilidade: centraliza sobreposição de períodos, conflito de
 * indisponibilidade com EventAssignment existente, validação em lote para escalas e a
 * consulta administrativa por intervalo. Todo fluxo capaz de gerar conflito entre
 * PersonUnavailability e EventAssignment passa por aqui para preservar o invariante
 * em um único lugar. Todas as comparações seguem o intervalo semiaberto [startAt, endAt).
 */
public interface PersonUnavailabilityConflictService {

    void validateNoOverlap(Long personId, LocalDateTime startAt, LocalDateTime endAt, Long excludeUnavailabilityId);

    void validateNoAssignmentConflict(Long personId, LocalDateTime startAt, LocalDateTime endAt);

    void lockPersonsInOrder(Collection<Long> personIds);

    void validateAvailabilityForEvent(Map<Long, Set<EventAssignmentType>> assignmentTypesByPersonId, LocalDateTime startAt, LocalDateTime endAt);

    List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnRange(LocalDateTime startAt, LocalDateTime endAt);
}
