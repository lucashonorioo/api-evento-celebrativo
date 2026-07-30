package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.ParticipationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CurrentUserScheduleResponseDTO {

    private Long eventId;
    private String eventName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean massOrCelebration;
    private Long locationId;
    private String locationName;
    private List<EventAssignmentType> assignments = new ArrayList<>();
    private ParticipationStatus participationStatus;
    private String declineReason;
    private LocalDateTime respondedAt;

    public CurrentUserScheduleResponseDTO() {
    }

    public CurrentUserScheduleResponseDTO(
            Long eventId,
            String eventName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean massOrCelebration,
            Long locationId,
            String locationName,
            List<EventAssignmentType> assignments,
            ParticipationStatus participationStatus,
            String declineReason,
            LocalDateTime respondedAt
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.massOrCelebration = massOrCelebration;
        this.locationId = locationId;
        this.locationName = locationName;
        this.assignments = assignments;
        this.participationStatus = participationStatus;
        this.declineReason = declineReason;
        this.respondedAt = respondedAt;
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

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     */
    @Deprecated
    public LocalDate getEventDate() {
        return startAt == null ? null : startAt.toLocalDate();
    }

    /**
     * @deprecated derivado exclusivamente de {@link #getStartAt()}; use startAt/endAt.
     */
    @Deprecated
    public LocalTime getEventTime() {
        return startAt == null ? null : startAt.toLocalTime();
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

    public ParticipationStatus getParticipationStatus() {
        return participationStatus;
    }

    public void setParticipationStatus(ParticipationStatus participationStatus) {
        this.participationStatus = participationStatus;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }
}
