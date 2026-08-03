package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationDetailResponseDTO {

    private Long notificationId;
    private String title;
    private String message;
    private NotificationOrigin origin;
    private String senderNameSnapshot;
    private NotificationAudience audience;
    private NotificationCategory category;
    private List<MinistryType> ministryTypes;
    private String referenceType;
    private Long referenceId;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime readAt;

    public NotificationDetailResponseDTO() {
    }

    public NotificationDetailResponseDTO(
            Long notificationId,
            String title,
            String message,
            NotificationOrigin origin,
            String senderNameSnapshot,
            NotificationAudience audience,
            NotificationCategory category,
            List<MinistryType> ministryTypes,
            String referenceType,
            Long referenceId,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            LocalDateTime readAt
    ) {
        this.notificationId = notificationId;
        this.title = title;
        this.message = message;
        this.origin = origin;
        this.senderNameSnapshot = senderNameSnapshot;
        this.audience = audience;
        this.category = category;
        this.ministryTypes = ministryTypes;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
