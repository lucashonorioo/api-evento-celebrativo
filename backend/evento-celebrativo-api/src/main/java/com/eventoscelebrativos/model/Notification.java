package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Notificacao interna enviada a uma ou mais {@link UserAccount}. Conteudo, publico e metadados sao
 * definidos no momento do envio por {@code NotificationDeliveryService} e nunca sao alterados depois
 * (sem edicao, reenvio ou exclusao). O unico estado mutavel e a resolucao de conflitos de escala:
 * {@code resolvedAt}/{@code activeSourceKey}, controlados exclusivamente por {@link #resolve}.
 */
@Entity
@Table(name = "tb_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_account_id")
    private UserAccount senderUserAccount;

    @Column(name = "sender_name_snapshot", nullable = false, length = 255)
    private String senderNameSnapshot;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_key", length = 255)
    private String sourceKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "active_source_key", length = 255)
    private String activeSourceKey;

    protected Notification() {
    }

    private Notification(
            NotificationOrigin origin,
            NotificationAudience audience,
            NotificationCategory category,
            String title,
            String message,
            UserAccount senderUserAccount,
            String senderNameSnapshot,
            String referenceType,
            Long referenceId,
            String sourceType,
            String sourceKey,
            String activeSourceKey,
            LocalDateTime createdAt
    ) {
        this.origin = origin;
        this.audience = audience;
        this.category = category;
        this.title = title;
        this.message = message;
        this.senderUserAccount = senderUserAccount;
        this.senderNameSnapshot = senderNameSnapshot;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
        this.activeSourceKey = activeSourceKey;
        this.createdAt = createdAt;
    }

    public static Notification administrative(
            UserAccount sender,
            String senderNameSnapshot,
            NotificationAudience audience,
            String title,
            String message,
            LocalDateTime createdAt
    ) {
        return new Notification(
                NotificationOrigin.ADMIN,
                audience,
                NotificationCategory.GENERAL,
                title,
                message,
                sender,
                senderNameSnapshot,
                null,
                null,
                null,
                null,
                null,
                createdAt
        );
    }

    public static Notification system(
            NotificationAudience audience,
            NotificationCategory category,
            String title,
            String message,
            String referenceType,
            Long referenceId,
            String sourceType,
            String sourceKey,
            LocalDateTime createdAt
    ) {
        return new Notification(
                NotificationOrigin.SYSTEM,
                audience,
                category,
                title,
                message,
                null,
                "Sistema",
                referenceType,
                referenceId,
                sourceType,
                sourceKey,
                null,
                createdAt
        );
    }

    /**
     * Unico ponto de criacao de notificacao com {@code activeSourceKey}: forca origin=SYSTEM e
     * category=SCHEDULE_CONFLICT, garantindo que nenhuma outra combinacao possa possuir uma chave
     * ativa (invariante "somente SYSTEM/SCHEDULE_CONFLICT podem possuir activeSourceKey").
     */
    public static Notification scheduleConflict(
            NotificationAudience audience,
            String title,
            String message,
            String referenceType,
            Long referenceId,
            String sourceType,
            String sourceKey,
            String activeSourceKey,
            LocalDateTime createdAt
    ) {
        return new Notification(
                NotificationOrigin.SYSTEM,
                audience,
                NotificationCategory.SCHEDULE_CONFLICT,
                title,
                message,
                null,
                "Sistema",
                referenceType,
                referenceId,
                sourceType,
                sourceKey,
                activeSourceKey,
                createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public NotificationOrigin getOrigin() {
        return origin;
    }

    public NotificationAudience getAudience() {
        return audience;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public UserAccount getSenderUserAccount() {
        return senderUserAccount;
    }

    public String getSenderNameSnapshot() {
        return senderNameSnapshot;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public String getActiveSourceKey() {
        return activeSourceKey;
    }

    /**
     * Resolve uma ocorrencia ativa de conflito de escala: libera {@code activeSourceKey} (permitindo
     * uma nova ocorrencia futura para a mesma identidade eventId+personId) e registra
     * {@code resolvedAt}. Idempotente (segunda chamada e no-op, mesmo que o argumento passado nao
     * seja mais validado nesse caso) e nunca reabre uma notificacao ja resolvida. Restrito a
     * SCHEDULE_CONFLICT: as demais categorias nunca tem activeSourceKey e nao fazem sentido
     * "resolvidas". Na primeira resolucao, exige um instante nao nulo com precisao de segundo
     * (nanos = 0, o mesmo grau de precisao de {@code currentSecond} em toda a reconciliacao) -
     * rejeitado antes de tocar {@code activeSourceKey}, nunca depois.
     */
    public void resolve(LocalDateTime resolvedAt) {
        if (category != NotificationCategory.SCHEDULE_CONFLICT) {
            throw new IllegalStateException("Somente notificacoes SCHEDULE_CONFLICT podem ser resolvidas.");
        }
        if (this.resolvedAt != null) {
            return;
        }
        if (resolvedAt == null) {
            throw new IllegalArgumentException("resolvedAt nao pode ser nulo.");
        }
        if (resolvedAt.getNano() != 0) {
            throw new IllegalArgumentException("resolvedAt deve ter precisao de segundo (nanos = 0).");
        }
        this.resolvedAt = resolvedAt;
        this.activeSourceKey = null;
    }
}
