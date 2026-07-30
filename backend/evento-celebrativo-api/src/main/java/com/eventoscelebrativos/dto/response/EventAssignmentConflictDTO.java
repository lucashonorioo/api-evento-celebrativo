package com.eventoscelebrativos.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class EventAssignmentConflictDTO {

    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private List<String> assignments;

    public EventAssignmentConflictDTO() {
    }

    public EventAssignmentConflictDTO(
            Long eventId,
            String eventName,
            LocalDate eventDate,
            LocalTime eventTime,
            List<String> assignments
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
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

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public List<String> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<String> assignments) {
        this.assignments = assignments;
    }
}
