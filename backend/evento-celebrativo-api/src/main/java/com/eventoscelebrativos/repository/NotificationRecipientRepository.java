package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    boolean existsByNotificationIdAndUserAccountId(Long notificationId, Long userAccountId);

    @Query("""
            SELECT r FROM NotificationRecipient r
            JOIN FETCH r.notification
            WHERE r.notification.id = :notificationId AND r.userAccount.id = :userAccountId
            """)
    Optional<NotificationRecipient> findByNotificationIdAndUserAccountId(
            @Param("notificationId") Long notificationId,
            @Param("userAccountId") Long userAccountId
    );

    /**
     * Caixa pessoal, filtrada e ordenada no banco antes da paginacao. {@code onlyUnread} nulo
     * corresponde ao filtro ALL, {@code TRUE} a UNREAD e {@code FALSE} a READ. O join com Notification
     * e 1:1 por causa da unicidade (notification_id, user_account_id), entao a paginacao aqui e
     * sempre correta. {@code categoryFilter}/{@code resolvedFilter} implementam
     * NotificationResolutionFilter (secao 14): ambos nulos correspondem a ALL; caso contrario
     * category=categoryFilter AND (resolvedAt IS NULL quando resolvedFilter=FALSE, IS NOT NULL
     * quando TRUE).
     */
    @Query("""
            SELECT r FROM NotificationRecipient r
            JOIN FETCH r.notification n
            WHERE r.userAccount.id = :accountId
              AND (:onlyUnread IS NULL
                   OR (:onlyUnread = TRUE AND r.readAt IS NULL)
                   OR (:onlyUnread = FALSE AND r.readAt IS NOT NULL))
              AND (:categoryFilter IS NULL OR n.category = :categoryFilter)
              AND (:resolvedFilter IS NULL
                   OR (:resolvedFilter = TRUE AND n.resolvedAt IS NOT NULL)
                   OR (:resolvedFilter = FALSE AND n.resolvedAt IS NULL))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Page<NotificationRecipient> findInbox(
            @Param("accountId") Long accountId,
            @Param("onlyUnread") Boolean onlyUnread,
            @Param("categoryFilter") NotificationCategory categoryFilter,
            @Param("resolvedFilter") Boolean resolvedFilter,
            Pageable pageable
    );

    /**
     * Update condicional para preservar o primeiro {@code readAt} sob concorrencia (nunca
     * load-modify-save). Retorna 1 quando marcou agora, 0 quando ja estava lida ou o recipient nao
     * existe para esta conta.
     */
    @Modifying
    @Query("""
            UPDATE NotificationRecipient r
            SET r.readAt = :readAt
            WHERE r.notification.id = :notificationId AND r.userAccount.id = :accountId AND r.readAt IS NULL
            """)
    int markAsRead(
            @Param("notificationId") Long notificationId,
            @Param("accountId") Long accountId,
            @Param("readAt") LocalDateTime readAt
    );

    @Modifying
    @Query("""
            UPDATE NotificationRecipient r
            SET r.readAt = :readAt
            WHERE r.userAccount.id = :accountId AND r.readAt IS NULL
            """)
    int markAllAsRead(@Param("accountId") Long accountId, @Param("readAt") LocalDateTime readAt);

    long countByUserAccountIdAndReadAtIsNull(Long accountId);

    @Query("""
            SELECT r.notification.id AS notificationId,
                   COUNT(r) AS recipientCount,
                   SUM(CASE WHEN r.readAt IS NOT NULL THEN 1 ELSE 0 END) AS readCount
            FROM NotificationRecipient r
            WHERE r.notification.id IN :notificationIds
            GROUP BY r.notification.id
            """)
    List<NotificationRecipientCounts> countByNotificationIdIn(@Param("notificationIds") Collection<Long> notificationIds);

    /**
     * Destinatarios administrativos de uma notificacao, com ordenacao fixa por nome (case-insensitive,
     * com desempate por nome exato e id) e filtro aplicado antes da paginacao.
     */
    @Query("""
            SELECT r FROM NotificationRecipient r
            JOIN FETCH r.userAccount ua
            JOIN FETCH ua.person
            WHERE r.notification.id = :notificationId
              AND (:onlyUnread IS NULL
                   OR (:onlyUnread = TRUE AND r.readAt IS NULL)
                   OR (:onlyUnread = FALSE AND r.readAt IS NOT NULL))
            ORDER BY LOWER(r.recipientNameSnapshot) ASC, r.recipientNameSnapshot ASC, r.id ASC
            """)
    Page<NotificationRecipient> findByNotificationIdWithFilter(
            @Param("notificationId") Long notificationId,
            @Param("onlyUnread") Boolean onlyUnread,
            Pageable pageable
    );

    interface NotificationRecipientCounts {
        Long getNotificationId();

        Long getRecipientCount();

        Long getReadCount();
    }
}
