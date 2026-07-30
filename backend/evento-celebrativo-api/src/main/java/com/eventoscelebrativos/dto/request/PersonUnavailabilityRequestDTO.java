package com.eventoscelebrativos.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class PersonUnavailabilityRequestDTO {

    @NotNull(message = "O campo startAt é obrigatório")
    private LocalDateTime startAt;

    @NotNull(message = "O campo endAt é obrigatório")
    private LocalDateTime endAt;

    private String reason;

    public PersonUnavailabilityRequestDTO() {
    }

    public PersonUnavailabilityRequestDTO(LocalDateTime startAt, LocalDateTime endAt, String reason) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
