package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class AdminUnavailabilityRangeDTO {

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public AdminUnavailabilityRangeDTO() {
    }

    public AdminUnavailabilityRangeDTO(LocalDateTime startAt, LocalDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
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
}
