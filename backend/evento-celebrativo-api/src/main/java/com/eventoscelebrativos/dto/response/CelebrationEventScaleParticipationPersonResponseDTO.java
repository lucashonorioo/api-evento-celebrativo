package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.ParticipationStatus;

import java.time.LocalDateTime;

public class CelebrationEventScaleParticipationPersonResponseDTO {

    private Long id;
    private String name;
    private ParticipationStatus participationStatus;
    private String declineReason;
    private LocalDateTime respondedAt;

    public CelebrationEventScaleParticipationPersonResponseDTO() {
    }

    public CelebrationEventScaleParticipationPersonResponseDTO(
            Long id,
            String name,
            ParticipationStatus participationStatus,
            String declineReason,
            LocalDateTime respondedAt
    ) {
        this.id = id;
        this.name = name;
        this.participationStatus = participationStatus;
        this.declineReason = declineReason;
        this.respondedAt = respondedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParticipationStatus getParticipationStatus() {
        return participationStatus;
    }

    public void setParticipationStatus(ParticipationStatus participationStatus) {
        this.participationStatus = participationStatus;
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
