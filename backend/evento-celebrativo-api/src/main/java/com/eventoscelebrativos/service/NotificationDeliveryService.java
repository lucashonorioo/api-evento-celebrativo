package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.response.NotificationCreateResponseDTO;

/**
 * Envio administrativo imediato e envio interno tipado de notificacoes. Concentra a resolucao de
 * publico, elegibilidade, locks ordenados, deduplicacao e persistencia atomica descritas nesta
 * branch; a logica dos quatro publicos (GLOBAL, ADMIN, MINISTRY, DIRECT) e compartilhada entre os
 * dois metodos, nao duplicada.
 */
public interface NotificationDeliveryService {

    NotificationCreateResponseDTO sendAdministrativeNotification(
            Long senderAccountId,
            NotificationCreateRequestDTO request
    );

    NotificationCreateResponseDTO sendSystemNotification(SystemNotificationCommand command);
}
