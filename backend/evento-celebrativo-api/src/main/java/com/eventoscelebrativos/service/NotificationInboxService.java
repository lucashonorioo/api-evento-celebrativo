package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.NotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.NotificationSummaryResponseDTO;
import com.eventoscelebrativos.dto.response.UnreadNotificationCountResponseDTO;
import org.springframework.data.domain.Page;

/**
 * Caixa pessoal de notificacoes: a conta e sempre resolvida pelo chamador via
 * {@code AuthenticatedUserResolver.requireCurrentAccountId()}, nunca por parametro de URL.
 */
public interface NotificationInboxService {

    Page<NotificationSummaryResponseDTO> findMine(Long accountId, String filter, String resolutionFilter, int page, int size);

    NotificationDetailResponseDTO findMineById(Long accountId, Long notificationId);

    void markAsRead(Long accountId, Long notificationId);

    void markAllAsRead(Long accountId);

    UnreadNotificationCountResponseDTO countUnread(Long accountId);
}
