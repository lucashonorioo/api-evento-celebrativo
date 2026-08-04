package com.eventoscelebrativos.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resolucao temporal de conflitos de escala (secao 13): cobre a passagem natural do tempo, quando
 * nenhuma escrita de dominio aciona {@code ScheduleConflictNotificationService.reconcile}
 * diretamente. Disparado periodicamente por um scheduler externo a este service.
 */
public interface ScheduleConflictResolutionService {

    List<ScheduleConflictReference> findActiveConflictsBatch(int batchSize);

    /**
     * Resolve uma unica ocorrencia ativa se o evento referenciado ja encerrou (endAt &lt;=
     * currentSecond) ou nao existe mais. Idempotente: notificacoes ja resolvidas por outra
     * instancia/caminho concorrente sao apenas ignoradas.
     */
    void resolveOne(Long notificationId, Long eventId, LocalDateTime currentSecond);

    record ScheduleConflictReference(Long notificationId, Long eventId) {
    }
}
