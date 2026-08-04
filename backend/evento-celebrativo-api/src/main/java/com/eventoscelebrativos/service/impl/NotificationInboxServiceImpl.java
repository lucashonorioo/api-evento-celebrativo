package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.response.NotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.NotificationSummaryResponseDTO;
import com.eventoscelebrativos.dto.response.UnreadNotificationCountResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationRecipient;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationTargetMinistryRepository;
import com.eventoscelebrativos.service.NotificationInboxService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class NotificationInboxServiceImpl implements NotificationInboxService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PREVIEW_CODE_POINTS = 160;
    // markAsRead e markAllAsRead atualizam a mesma linha por caminhos de indice diferentes (unique
    // notification_id+user_account_id vs o indice usado pelo filtro por conta); o InnoDB pode
    // detectar deadlock entre duas dessas atualizacoes concorrentes na mesma conta. Como cada
    // tentativa e um UPDATE condicional idempotente de uma unica linha, reexecutar em uma nova
    // transacao e seguro.
    private static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 3;

    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationTargetMinistryRepository notificationTargetMinistryRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationInboxServiceImpl(
            NotificationRecipientRepository notificationRecipientRepository,
            NotificationTargetMinistryRepository notificationTargetMinistryRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.notificationTargetMinistryRepository = notificationTargetMinistryRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationSummaryResponseDTO> findMine(Long accountId, String filter, String resolutionFilter, int page, int size) {
        Boolean onlyUnread = parseFilter(filter);
        ResolutionFilter resolution = parseResolutionFilter(resolutionFilter);
        validatePage(page, size);
        Page<NotificationRecipient> result = notificationRecipientRepository.findInbox(
                accountId, onlyUnread, resolution.categoryFilter(), resolution.resolvedFilter(), PageRequest.of(page, size));
        return result.map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDetailResponseDTO findMineById(Long accountId, Long notificationId) {
        NotificationRecipient recipient = notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(notificationId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação", notificationId));
        Notification notification = recipient.getNotification();
        List<MinistryType> ministryTypes = notification.getAudience() == NotificationAudience.MINISTRY
                ? notificationTargetMinistryRepository.findMinistryTypesByNotificationId(notificationId)
                : List.of();

        return new NotificationDetailResponseDTO(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getOrigin(),
                notification.getSenderNameSnapshot(),
                notification.getAudience(),
                notification.getCategory(),
                ministryTypes,
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getCreatedAt(),
                notification.getResolvedAt(),
                recipient.getReadAt()
        );
    }

    @Override
    public void markAsRead(Long accountId, Long notificationId) {
        withDeadlockRetry(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
                int updated = notificationRecipientRepository.markAsRead(notificationId, accountId, currentSecond);
                if (updated == 0 && !notificationRecipientRepository.existsByNotificationIdAndUserAccountId(notificationId, accountId)) {
                    throw new ResourceNotFoundException("Notificação", notificationId);
                }
            });
            return null;
        });
    }

    @Override
    public void markAllAsRead(Long accountId) {
        withDeadlockRetry(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
                notificationRecipientRepository.markAllAsRead(accountId, currentSecond);
            });
            return null;
        });
    }

    private <T> T withDeadlockRetry(Supplier<T> action) {
        ConcurrencyFailureException lastFailure = null;
        for (int attempt = 0; attempt < MAX_DEADLOCK_RETRY_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (ConcurrencyFailureException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponseDTO countUnread(Long accountId) {
        return new UnreadNotificationCountResponseDTO(
                notificationRecipientRepository.countByUserAccountIdAndReadAtIsNull(accountId)
        );
    }

    private NotificationSummaryResponseDTO toSummary(NotificationRecipient recipient) {
        Notification notification = recipient.getNotification();
        return new NotificationSummaryResponseDTO(
                notification.getId(),
                notification.getTitle(),
                preview(notification.getMessage()),
                notification.getOrigin(),
                notification.getSenderNameSnapshot(),
                notification.getAudience(),
                notification.getCategory(),
                notification.getCreatedAt(),
                notification.getResolvedAt(),
                recipient.getReadAt()
        );
    }

    // Trunca por ponto de codigo Unicode (nao por char UTF-16), preservando espacos internos e
    // quebras de linha; a mensagem persistida nunca e alterada, apenas a copia exibida na listagem.
    private String preview(String message) {
        int codePointCount = message.codePointCount(0, message.length());
        if (codePointCount <= MAX_PREVIEW_CODE_POINTS) {
            return message;
        }
        int truncateAtIndex = message.offsetByCodePoints(0, MAX_PREVIEW_CODE_POINTS - 1);
        return message.substring(0, truncateAtIndex) + "…";
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

    private ResolutionFilter parseResolutionFilter(String resolutionFilter) {
        String normalized = resolutionFilter == null ? "ALL" : resolutionFilter.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALL" -> new ResolutionFilter(null, null);
            case "ACTIVE" -> new ResolutionFilter(NotificationCategory.SCHEDULE_CONFLICT, Boolean.FALSE);
            case "RESOLVED" -> new ResolutionFilter(NotificationCategory.SCHEDULE_CONFLICT, Boolean.TRUE);
            default -> throw new BadRequestException("O filtro de resolucao deve ser ALL, ACTIVE ou RESOLVED");
        };
    }

    private record ResolutionFilter(NotificationCategory categoryFilter, Boolean resolvedFilter) {
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
