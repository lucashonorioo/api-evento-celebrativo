package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;
import com.eventoscelebrativos.model.EventAssignmentType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Componente central de disponibilidade: centraliza sobreposição de períodos, bloqueio de nova
 * atribuição sobre indisponibilidade existente, bloqueio de indisponibilidade sobre evento já
 * iniciado, validação em lote para escalas e a consulta administrativa por intervalo. Desde a
 * branch feature/schedule-unavailability-conflict-management, uma indisponibilidade pode
 * conviver com EventAssignments futuros (o conflito passa a ser derivado, não bloqueante); só é
 * rejeitada quando intercepta um evento em andamento. Todas as comparações seguem o intervalo
 * semiaberto [startAt, endAt).
 */
public interface PersonUnavailabilityConflictService {

    void validateNoOverlap(Long personId, LocalDateTime startAt, LocalDateTime endAt, Long excludeUnavailabilityId);

    /**
     * Rejeita a indisponibilidade somente quando ela intercepta um EventAssignment cujo evento
     * já esteja em andamento em currentSecond (event.startAt &lt;= currentSecond &lt; event.endAt).
     * Conflitos com eventos futuros são permitidos e passam a ser derivados administrativamente.
     */
    void validateNoStartedAssignmentConflict(Long personId, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime currentSecond);

    void lockPersonsInOrder(Collection<Long> personIds);

    void validateAvailabilityForEvent(Map<Long, Set<EventAssignmentType>> assignmentTypesByPersonId, LocalDateTime startAt, LocalDateTime endAt);

    List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnRange(LocalDateTime startAt, LocalDateTime endAt);
}
