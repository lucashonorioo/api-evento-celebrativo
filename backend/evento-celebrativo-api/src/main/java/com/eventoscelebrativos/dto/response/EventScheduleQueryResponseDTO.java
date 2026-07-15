package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EventScheduleQueryResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;
    private Long locationId;
    private String churchName;
    private EventScheduleType assignmentType;
    private List<EventScheduleAssignmentResponseDTO> assignments = new ArrayList<>();

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

    public Boolean getMassOrCelebration() {
        return massOrCelebration;
    }

    public void setMassOrCelebration(Boolean massOrCelebration) {
        this.massOrCelebration = massOrCelebration;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public EventScheduleType getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(EventScheduleType assignmentType) {
        this.assignmentType = assignmentType;
    }

    public List<EventScheduleAssignmentResponseDTO> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<EventScheduleAssignmentResponseDTO> assignments) {
        this.assignments = assignments;
    }
}
