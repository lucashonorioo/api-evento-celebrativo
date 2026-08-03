package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;

import java.time.LocalDateTime;

public class AdminNotificationSummaryResponseDTO {

    private Long notificationId;
    private NotificationOrigin origin;
    private NotificationAudience audience;
    private NotificationCategory category;
    private String title;
    private Long senderPersonId;
    private String senderNameSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private long recipientCount;
    private long readCount;

    public AdminNotificationSummaryResponseDTO() {
    }

    public AdminNotificationSummaryResponseDTO(
            Long notificationId,
            NotificationOrigin origin,
            NotificationAudience audience,
            NotificationCategory category,
            String title,
            Long senderPersonId,
            String senderNameSnapshot,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            long recipientCount,
            long readCount
    ) {
        this.notificationId = notificationId;
        this.origin = origin;
        this.audience = audience;
        this.category = category;
        this.title = title;
        this.senderPersonId = senderPersonId;
        this.senderNameSnapshot = senderNameSnapshot;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.recipientCount = recipientCount;
        this.readCount = readCount;
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

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getSenderPersonId() {
        return senderPersonId;
    }

    public void setSenderPersonId(Long senderPersonId) {
        this.senderPersonId = senderPersonId;
    }

    public String getSenderNameSnapshot() {
        return senderNameSnapshot;
    }

    public void setSenderNameSnapshot(String senderNameSnapshot) {
        this.senderNameSnapshot = senderNameSnapshot;
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

    public long getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(long recipientCount) {
        this.recipientCount = recipientCount;
    }

    public long getReadCount() {
        return readCount;
    }

    public void setReadCount(long readCount) {
        this.readCount = readCount;
    }
}
