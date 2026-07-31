package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.model.EventAssignmentType;
import org.springframework.http.HttpStatus;

import java.util.Set;

public class MultipleAssignmentsForPersonInEventException extends ErrorResponseException {

    private final Long eventId;
    private final Long personId;
    private final Set<EventAssignmentType> conflictingAssignmentTypes;

    public MultipleAssignmentsForPersonInEventException(
            Long eventId,
            Long personId,
            Set<EventAssignmentType> conflictingAssignmentTypes
    ) {
        super(
                "A pessoa não pode exercer mais de uma função no mesmo evento.",
                HttpStatus.CONFLICT,
                "MULTIPLE_ASSIGNMENTS_FOR_PERSON_IN_EVENT"
        );
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
