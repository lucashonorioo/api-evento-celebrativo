package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;

import java.time.LocalDateTime;
import java.util.List;

public class AdminNotificationDetailResponseDTO {

    private Long notificationId;
    private NotificationOrigin origin;
    private NotificationAudience audience;
    private NotificationCategory category;
    private String title;
    private String message;
    private Long senderPersonId;
    private String senderNameSnapshot;
    private List<MinistryType> ministryTypes;
    private String referenceType;
    private Long referenceId;
    private String sourceType;
    private String sourceKey;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private long recipientCount;
    private long readCount;

    public AdminNotificationDetailResponseDTO() {
    }

    public AdminNotificationDetailResponseDTO(
            Long notificationId,
            NotificationOrigin origin,
            NotificationAudience audience,
            NotificationCategory category,
            String title,
            String message,
            Long senderPersonId,
            String senderNameSnapshot,
            List<MinistryType> ministryTypes,
            String referenceType,
            Long referenceId,
            String sourceType,
            String sourceKey,
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
        this.message = message;
        this.senderPersonId = senderPersonId;
        this.senderNameSnapshot = senderNameSnapshot;
        this.ministryTypes = ministryTypes;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public List<MinistryType> getMinistryTypes() {
        return ministryTypes;
    }

    public void setMinistryTypes(List<MinistryType> ministryTypes) {
        this.ministryTypes = ministryTypes;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
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
