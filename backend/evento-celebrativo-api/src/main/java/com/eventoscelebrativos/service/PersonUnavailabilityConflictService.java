package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminUnavailabilityPersonDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Componente central de disponibilidade: centraliza sobreposição de períodos entre indisponibilidades
 * da mesma pessoa, o lock ordenado de pessoas e a consulta administrativa por intervalo. Desde a
 * branch feature/schedule-conflict-notifications, conflito entre EventAssignment e
 * PersonUnavailability nunca é mais bloqueante (nem sobre evento em andamento, nem sobre nova
 * atribuição): é sempre permitido, persistido como Notification/SCHEDULE_CONFLICT e resolvido
 * automaticamente pelo {@code ScheduleConflictNotificationService}. Todas as comparações seguem o
 * intervalo semiaberto [startAt, endAt).
 */
public interface PersonUnavailabilityConflictService {

    void validateNoOverlap(Long personId, LocalDateTime startAt, LocalDateTime endAt, Long excludeUnavailabilityId);

    void lockPersonsInOrder(Collection<Long> personIds);

    List<AdminUnavailabilityPersonDTO> findUnavailablePeopleOnRange(LocalDateTime startAt, LocalDateTime endAt);
}
