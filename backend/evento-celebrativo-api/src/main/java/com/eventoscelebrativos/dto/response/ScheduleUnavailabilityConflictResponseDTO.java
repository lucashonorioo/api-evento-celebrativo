package com.eventoscelebrativos.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public final class ScheduleUnavailabilityConflictResponseDTO {

    private final Long eventId;
    private final String eventName;
    private final LocalDateTime eventStartAt;
    private final LocalDateTime eventEndAt;
    private final Long personId;
    private final String personName;
    private final String assignmentType;
    private final List<ScheduleConflictUnavailabilityResponseDTO> unavailabilities;

    public ScheduleUnavailabilityConflictResponseDTO(
            Long eventId,
            String eventName,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndAt,
            Long personId,
            String personName,
            String assignmentType,
            List<ScheduleConflictUnavailabilityResponseDTO> unavailabilities
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventStartAt = eventStartAt;
        this.eventEndAt = eventEndAt;
        this.personId = personId;
        this.personName = personName;
        this.assignmentType = assignmentType;
        this.unavailabilities = unavailabilities;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDateTime getEventStartAt() {
        return eventStartAt;
    }

    public LocalDateTime getEventEndAt() {
        return eventEndAt;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getPersonName() {
        return personName;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public List<ScheduleConflictUnavailabilityResponseDTO> getUnavailabilities() {
        return unavailabilities;
    }
}
