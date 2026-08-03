package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class AdminNotificationRecipientResponseDTO {

    private Long recipientPersonId;
    private String recipientNameSnapshot;
    private LocalDateTime readAt;

    public AdminNotificationRecipientResponseDTO() {
    }

    public AdminNotificationRecipientResponseDTO(Long recipientPersonId, String recipientNameSnapshot, LocalDateTime readAt) {
        this.recipientPersonId = recipientPersonId;
        this.recipientNameSnapshot = recipientNameSnapshot;
        this.readAt = readAt;
    }

    public Long getRecipientPersonId() {
        return recipientPersonId;
    }

    public void setRecipientPersonId(Long recipientPersonId) {
        this.recipientPersonId = recipientPersonId;
    }

    public String getRecipientNameSnapshot() {
        return recipientNameSnapshot;
    }

    public void setRecipientNameSnapshot(String recipientNameSnapshot) {
        this.recipientNameSnapshot = recipientNameSnapshot;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
