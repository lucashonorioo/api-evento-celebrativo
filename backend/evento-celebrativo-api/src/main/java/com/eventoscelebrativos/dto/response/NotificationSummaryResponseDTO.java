package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;

import java.time.LocalDateTime;

public class NotificationSummaryResponseDTO {

    private Long notificationId;
    private String title;
    private String messagePreview;
    private NotificationOrigin origin;
    private String senderNameSnapshot;
    private NotificationAudience audience;
    private NotificationCategory category;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime readAt;

    public NotificationSummaryResponseDTO() {
    }

    public NotificationSummaryResponseDTO(
            Long notificationId,
            String title,
            String messagePreview,
            NotificationOrigin origin,
            String senderNameSnapshot,
            NotificationAudience audience,
            NotificationCategory category,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            LocalDateTime readAt
    ) {
        this.notificationId = notificationId;
        this.title = title;
        this.messagePreview = messagePreview;
        this.origin = origin;
        this.senderNameSnapshot = senderNameSnapshot;
        this.audience = audience;
        this.category = category;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.readAt = readAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessagePreview() {
        return messagePreview;
    }

    public void setMessagePreview(String messagePreview) {
        this.messagePreview = messagePreview;
    }

    public NotificationOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(NotificationOrigin origin) {
        this.origin = origin;
    }

    public String getSenderNameSnapshot() {
        return senderNameSnapshot;
    }

    public void setSenderNameSnapshot(String senderNameSnapshot) {
        this.senderNameSnapshot = senderNameSnapshot;
    }

    public NotificationAudience getAudience() {
        return audience;
    }

    public void setAudience(NotificationAudience audience) {
        this.audience = audience;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
