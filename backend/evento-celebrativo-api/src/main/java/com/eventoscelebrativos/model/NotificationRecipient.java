package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Destinatario materializado de uma {@link Notification}, com snapshot do nome da pessoa no momento
 * do envio. {@code readAt} so pode passar de nulo para preenchido; a marcacao de leitura concorrente
 * e feita por update condicional no repositorio (nunca por load-modify-save), entao esta entidade nao
 * expoe um mutador publico de leitura.
 */
@Entity
@Table(name = "tb_notification_recipient")
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "recipient_name_snapshot", nullable = false, length = 255)
    private String recipientNameSnapshot;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected NotificationRecipient() {
    }

    public NotificationRecipient(Notification notification, UserAccount userAccount, String recipientNameSnapshot) {
        this.notification = notification;
        this.userAccount = userAccount;
        this.recipientNameSnapshot = recipientNameSnapshot;
    }

    public Long getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getRecipientNameSnapshot() {
        return recipientNameSnapshot;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
