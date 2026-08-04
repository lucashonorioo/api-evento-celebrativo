package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.response.NotificationCreateResponseDTO;

import java.time.LocalDateTime;

/**
 * Envio administrativo imediato e envio interno tipado de notificacoes. Concentra a resolucao de
 * publico, elegibilidade, locks ordenados, deduplicacao e persistencia atomica descritas nesta
 * branch; a logica dos quatro publicos (GLOBAL, ADMIN, MINISTRY, DIRECT) e compartilhada entre os
 * tres metodos, nao duplicada.
 */
public interface NotificationDeliveryService {

    NotificationCreateResponseDTO sendAdministrativeNotification(
            Long senderAccountId,
            NotificationCreateRequestDTO request
    );

    NotificationCreateResponseDTO sendSystemNotification(SystemNotificationCommand command);

    /**
     * Uso exclusivo de {@code ScheduleConflictNotificationService}: envia uma notificacao
     * SYSTEM/SCHEDULE_CONFLICT persistindo {@code activeSourceKey} (garante no maximo uma ocorrencia
     * ativa por conflito) com {@code createdAt} igual ao {@code currentSecond} do comando de dominio
     * que disparou a reconciliacao, na mesma transacao.
     */
    NotificationCreateResponseDTO sendSystemNotificationForConflictReconciliation(
            SystemNotificationCommand command,
            String activeSourceKey,
            LocalDateTime currentSecond
    );
}
