package com.eventoscelebrativos.dto.request;

public class ParticipationResponseRequestDTO {

    private String status;
    private String declineReason;

    public ParticipationResponseRequestDTO() {
    }

    public ParticipationResponseRequestDTO(String status, String declineReason) {
        this.status = status;
        this.declineReason = declineReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }
}
