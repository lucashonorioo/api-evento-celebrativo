package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class PersonUnavailabilityResponseDTO {

    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;

    public PersonUnavailabilityResponseDTO() {
    }

    public PersonUnavailabilityResponseDTO(Long id, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        this.id = id;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
