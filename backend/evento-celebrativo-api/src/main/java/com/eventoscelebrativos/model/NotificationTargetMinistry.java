package com.eventoscelebrativos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ministerio selecionado em uma {@link Notification} de audience MINISTRY. Uma linha por ministerio
 * escolhido no envio (nao por destinatario) - os destinatarios materializados ficam em
 * {@link NotificationRecipient}.
 */
@Entity
@Table(name = "tb_notification_ministry")
@IdClass(NotificationTargetMinistryId.class)
public class NotificationTargetMinistry {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "ministry_type", nullable = false, length = 50)
    private MinistryType ministryType;

    protected NotificationTargetMinistry() {
    }

    public NotificationTargetMinistry(Notification notification, MinistryType ministryType) {
        this.notification = notification;
        this.ministryType = ministryType;
    }

    public Notification getNotification() {
        return notification;
    }

    public MinistryType getMinistryType() {
        return ministryType;
    }
}
