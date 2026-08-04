package com.eventoscelebrativos.service;

import java.time.LocalDateTime;

/**
 * Reconciliador central de conflitos entre EventAssignment e PersonUnavailability (secao 9):
 * garante no maximo uma Notification/SCHEDULE_CONFLICT ativa por identidade eventId+personId,
 * criando-a quando o conflito passa a existir e resolvendo-a quando deixa de existir. Chamado
 * pelos fluxos de escrita de escala/evento e de indisponibilidade, sempre apos o chamador ja ter
 * adquirido o mutex ROLE_ADMIN e os locks de pessoa/evento necessarios (secao 8) - {@code reconcile}
 * nao adquire esses locks por conta propria, apenas os utiliza.
 * <p>
 * A precondicao "mutex ROLE_ADMIN ja adquirido nesta transacao" e verificada em runtime pela
 * implementacao, contra o estado real da transacao Spring atual (ver
 * {@code AdminRoleMutexService#requireLockedInCurrentTransaction}), antes de qualquer leitura de
 * {@code active_source_key}. Isso substitui um guard de tipo marcador que podia ser forjado por
 * qualquer classe implementando a mesma interface publica sem nunca ter passado por
 * {@code AdminRoleMutexService#lockAdminRole}.
 */
public interface ScheduleConflictNotificationService {

    void reconcile(Long eventId, Long personId, LocalDateTime currentSecond);
}
