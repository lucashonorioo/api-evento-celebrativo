package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.AdminNotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationRecipientResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationSummaryResponseDTO;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationOrigin;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

/**
 * Historico administrativo de notificacoes: nenhuma consulta aqui altera readAt.
 */
public interface NotificationAdminService {

    Page<AdminNotificationSummaryResponseDTO> findHistory(
            NotificationOrigin origin,
            NotificationAudience audience,
            LocalDate startDate,
            LocalDate endDate,
            Long senderPersonId,
            String resolutionFilter,
            int page,
            int size
    );

    AdminNotificationDetailResponseDTO findById(Long notificationId);

    Page<AdminNotificationRecipientResponseDTO> findRecipients(Long notificationId, String filter, int page, int size);
}
