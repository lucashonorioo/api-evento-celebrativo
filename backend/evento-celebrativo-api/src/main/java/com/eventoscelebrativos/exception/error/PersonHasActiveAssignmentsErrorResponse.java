package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.dto.response.PersonAssignmentConflictDTO;

import java.time.Instant;
import java.util.List;

public class PersonHasActiveAssignmentsErrorResponse extends ErrorResponse {

    private final List<PersonAssignmentConflictDTO> assignments;

    public PersonHasActiveAssignmentsErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            List<PersonAssignmentConflictDTO> assignments
    ) {
        super(timestamp, status, error, errorCode, path);
        this.assignments = assignments;
    }

    public List<PersonAssignmentConflictDTO> getAssignments() {
        return assignments;
    }
}
