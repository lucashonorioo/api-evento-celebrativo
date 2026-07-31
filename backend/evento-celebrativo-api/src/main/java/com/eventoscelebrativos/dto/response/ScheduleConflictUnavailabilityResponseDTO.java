package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public final class ScheduleConflictUnavailabilityResponseDTO {

    private final Long id;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;

    public ScheduleConflictUnavailabilityResponseDTO(Long id, LocalDateTime startAt, LocalDateTime endAt) {
        this.id = id;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }
}
