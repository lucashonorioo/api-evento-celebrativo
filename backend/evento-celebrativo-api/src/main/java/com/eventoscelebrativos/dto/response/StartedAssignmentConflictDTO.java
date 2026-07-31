package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;

public class StartedAssignmentConflictDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime eventStartAt;
    private LocalDateTime eventEndAt;
    private String assignmentType;

    public StartedAssignmentConflictDTO() {
    }

    public StartedAssignmentConflictDTO(
            Long eventId,
            String eventName,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndAt,
            String assignmentType
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventStartAt = eventStartAt;
        this.eventEndAt = eventEndAt;
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

    public LocalDateTime getEventStartAt() {
        return eventStartAt;
    }

    public void setEventStartAt(LocalDateTime eventStartAt) {
        this.eventStartAt = eventStartAt;
    }

    public LocalDateTime getEventEndAt() {
        return eventEndAt;
    }

    public void setEventEndAt(LocalDateTime eventEndAt) {
        this.eventEndAt = eventEndAt;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }
}
