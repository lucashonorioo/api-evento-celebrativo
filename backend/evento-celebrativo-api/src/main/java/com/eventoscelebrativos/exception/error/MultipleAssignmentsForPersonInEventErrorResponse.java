package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.model.EventAssignmentType;

import java.time.Instant;
import java.util.Set;

public class MultipleAssignmentsForPersonInEventErrorResponse extends ErrorResponse {

    private final Long eventId;
    private final Long personId;
    private final Set<EventAssignmentType> conflictingAssignmentTypes;

    public MultipleAssignmentsForPersonInEventErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            Long eventId,
            Long personId,
            Set<EventAssignmentType> conflictingAssignmentTypes
    ) {
        super(timestamp, status, error, errorCode, path);
        this.eventId = eventId;
        this.personId = personId;
        this.conflictingAssignmentTypes = conflictingAssignmentTypes;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getPersonId() {
        return personId;
    }

    public Set<EventAssignmentType> getConflictingAssignmentTypes() {
        return conflictingAssignmentTypes;
    }
}
