package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.dto.response.StartedAssignmentConflictDTO;

import java.time.Instant;
import java.util.List;

public class UnavailabilityConflictWithStartedAssignmentErrorResponse extends ErrorResponse {

    private final List<StartedAssignmentConflictDTO> conflicts;

    public UnavailabilityConflictWithStartedAssignmentErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            List<StartedAssignmentConflictDTO> conflicts
    ) {
        super(timestamp, status, error, errorCode, path);
        this.conflicts = conflicts;
    }

    public List<StartedAssignmentConflictDTO> getConflicts() {
        return conflicts;
    }
}
