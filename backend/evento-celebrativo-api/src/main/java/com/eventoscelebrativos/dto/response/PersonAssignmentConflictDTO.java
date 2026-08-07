package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class PersonAssignmentConflictDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String assignmentType;

    public PersonAssignmentConflictDTO() {
    }

    public PersonAssignmentConflictDTO(
            Long eventId,
            String eventName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String assignmentType
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.assignmentType = assignmentType;
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

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }
}
