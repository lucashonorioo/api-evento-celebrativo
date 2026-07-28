package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventAssignmentType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CurrentUserScheduleResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private Boolean massOrCelebration;
    private Long locationId;
    private String locationName;
    private List<EventAssignmentType> assignments = new ArrayList<>();

    public CurrentUserScheduleResponseDTO() {
    }

    public CurrentUserScheduleResponseDTO(
            Long eventId,
            String eventName,
            LocalDate eventDate,
            LocalTime eventTime,
            Boolean massOrCelebration,
            Long locationId,
            String locationName,
            List<EventAssignmentType> assignments
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.massOrCelebration = massOrCelebration;
        this.locationId = locationId;
        this.locationName = locationName;
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

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public List<EventAssignmentType> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<EventAssignmentType> assignments) {
        this.assignments = assignments;
    }
}
