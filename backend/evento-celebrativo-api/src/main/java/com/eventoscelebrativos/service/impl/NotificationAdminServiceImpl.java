package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.AdminNotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationRecipientResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationSummaryResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationOrigin;
import com.eventoscelebrativos.model.NotificationRecipient;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.NotificationTargetMinistryRepository;
import com.eventoscelebrativos.service.NotificationAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationAdminServiceImpl implements NotificationAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationTargetMinistryRepository notificationTargetMinistryRepository;

    public NotificationAdminServiceImpl(
            NotificationRepository notificationRepository,
            NotificationRecipientRepository notificationRecipientRepository,
            NotificationTargetMinistryRepository notificationTargetMinistryRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.notificationTargetMinistryRepository = notificationTargetMinistryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminNotificationSummaryResponseDTO> findHistory(
            NotificationOrigin origin,
            NotificationAudience audience,
            LocalDate startDate,
            LocalDate endDate,
            Long senderPersonId,
            int page,
            int size
    ) {
        validatePage(page, size);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate não pode ser posterior a endDate");
        }
        LocalDateTime startInclusive = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        Page<Notification> notifications = notificationRepository.findAdminHistory(
                origin, audience, senderPersonId, startInclusive, endExclusive, PageRequest.of(page, size));

        List<Long> ids = notifications.getContent().stream().map(Notification::getId).toList();
        Map<Long, NotificationRecipientRepository.NotificationRecipientCounts> countsById = ids.isEmpty()
                ? Map.of()
                : notificationRecipientRepository.countByNotificationIdIn(ids).stream()
                        .collect(Collectors.toMap(NotificationRecipientRepository.NotificationRecipientCounts::getNotificationId, c -> c));

        return notifications.map(notification -> toSummary(notification, countsById.get(notification.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationDetailResponseDTO findById(Long notificationId) {
        Notification notification = notificationRepository.findByIdWithSender(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação", notificationId));

        List<MinistryType> ministryTypes = notification.getAudience() == NotificationAudience.MINISTRY
                ? notificationTargetMinistryRepository.findMinistryTypesByNotificationId(notificationId)
                : List.of();

        List<NotificationRecipientRepository.NotificationRecipientCounts> counts =
                notificationRecipientRepository.countByNotificationIdIn(List.of(notificationId));
        long recipientCount = counts.isEmpty() ? 0 : counts.get(0).getRecipientCount();
        long readCount = counts.isEmpty() ? 0 : counts.get(0).getReadCount();

        return new AdminNotificationDetailResponseDTO(
                notification.getId(),
                notification.getOrigin(),
                notification.getAudience(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getMessage(),
                senderPersonIdOf(notification),
                notification.getSenderNameSnapshot(),
                ministryTypes,
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getSourceType(),
                notification.getSourceKey(),
                notification.getCreatedAt(),
                notification.getResolvedAt(),
                recipientCount,
                readCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminNotificationRecipientResponseDTO> findRecipients(Long notificationId, String filter, int page, int size) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Notificação", notificationId);
        }
        Boolean onlyUnread = parseFilter(filter);
        validatePage(page, size);

        Page<NotificationRecipient> recipients = notificationRecipientRepository.findByNotificationIdWithFilter(
                notificationId, onlyUnread, PageRequest.of(page, size));

        return recipients.map(recipient -> new AdminNotificationRecipientResponseDTO(
                recipient.getUserAccount().getPerson().getId(),
                recipient.getRecipientNameSnapshot(),
                recipient.getReadAt()
        ));
    }

    private AdminNotificationSummaryResponseDTO toSummary(
            Notification notification,
            NotificationRecipientRepository.NotificationRecipientCounts counts
    ) {
        return new AdminNotificationSummaryResponseDTO(
                notification.getId(),
                notification.getOrigin(),
                notification.getAudience(),
                notification.getCategory(),
                notification.getTitle(),
                senderPersonIdOf(notification),
                notification.getSenderNameSnapshot(),
                notification.getCreatedAt(),
                notification.getResolvedAt(),
                counts == null ? 0 : counts.getRecipientCount(),
                counts == null ? 0 : counts.getReadCount()
        );
    }

    private Long senderPersonIdOf(Notification notification) {
        UserAccount sender = notification.getSenderUserAccount();
        return sender == null ? null : sender.getPerson().getId();
    }

    private Boolean parseFilter(String filter) {
        String normalized = filter == null ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALL" -> null;
            case "UNREAD" -> Boolean.TRUE;
            case "READ" -> Boolean.FALSE;
            default -> throw new BadRequestException("O filtro deve ser ALL, UNREAD ou READ");
        };
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("O número da página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }
}
