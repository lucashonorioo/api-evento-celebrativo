package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class EventAssignmentConflictDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private List<String> assignments;

    public EventAssignmentConflictDTO() {
    }

    public EventAssignmentConflictDTO(
            Long eventId,
            String eventName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<String> assignments
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.assignments = assignments;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
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

    public List<String> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<String> assignments) {
        this.assignments = assignments;
    }
}
