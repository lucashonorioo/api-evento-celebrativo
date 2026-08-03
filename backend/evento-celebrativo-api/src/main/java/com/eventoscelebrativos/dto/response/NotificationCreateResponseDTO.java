package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationOrigin;

import java.time.LocalDateTime;

public class NotificationCreateResponseDTO {

    private Long notificationId;
    private NotificationOrigin origin;
    private NotificationAudience audience;
    private String title;
    private LocalDateTime createdAt;
    private long recipientCount;

    public NotificationCreateResponseDTO() {
    }

    public NotificationCreateResponseDTO(
            Long notificationId,
            NotificationOrigin origin,
            NotificationAudience audience,
            String title,
            LocalDateTime createdAt,
            long recipientCount
    ) {
        this.notificationId = notificationId;
        this.origin = origin;
        this.audience = audience;
        this.title = title;
        this.createdAt = createdAt;
        this.recipientCount = recipientCount;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public NotificationOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(NotificationOrigin origin) {
        this.origin = origin;
    }

    public NotificationAudience getAudience() {
        return audience;
    }

    public void setAudience(NotificationAudience audience) {
        this.audience = audience;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(long recipientCount) {
        this.recipientCount = recipientCount;
    }
}
