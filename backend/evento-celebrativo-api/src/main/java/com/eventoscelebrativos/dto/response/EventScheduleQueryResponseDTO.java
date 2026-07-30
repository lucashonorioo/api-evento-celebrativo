package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventScheduleType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventScheduleQueryResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    /** Termino previsto do compromisso para planejamento de disponibilidade e escala. */
    private LocalDateTime endAt;
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
