package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.ParticipationStatus;

import java.time.LocalDateTime;

public class ParticipationResponseResponseDTO {

    private Long eventId;
    private ParticipationStatus status;
    private String declineReason;
    private LocalDateTime respondedAt;

    public ParticipationResponseResponseDTO() {
    }

    public ParticipationResponseResponseDTO(
            Long eventId,
            ParticipationStatus status,
            String declineReason,
            LocalDateTime respondedAt
    ) {
        this.eventId = eventId;
        this.status = status;
        this.declineReason = declineReason;
        this.respondedAt = respondedAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public ParticipationStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipationStatus status) {
        this.status = status;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }
}
