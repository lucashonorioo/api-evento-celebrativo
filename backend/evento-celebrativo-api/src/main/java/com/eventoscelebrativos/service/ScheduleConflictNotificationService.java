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
 * O parametro {@link AdminRoleMutexGuard} nao e lido por reconcile: sua unica funcao e tornar a
 * precondicao "mutex ja adquirido" parte do contrato em tempo de compilacao, nao apenas deste
 * Javadoc. Como a unica forma de obter um guard e chamando
 * {@code AdminRoleMutexService#lockAdminRole()} primeiro (construtor da implementacao e privado),
 * nao ha como chamar reconcile sem ja estar com o mutex travado nesta transacao - eliminando o
 * deadlock real (gap lock em active_source_key vs. lock de role) comprovado ao chamar reconcile
 * diretamente sem essa ordem.
 */
public interface ScheduleConflictNotificationService {

    void reconcile(AdminRoleMutexGuard mutexGuard, Long eventId, Long personId, LocalDateTime currentSecond);
}
