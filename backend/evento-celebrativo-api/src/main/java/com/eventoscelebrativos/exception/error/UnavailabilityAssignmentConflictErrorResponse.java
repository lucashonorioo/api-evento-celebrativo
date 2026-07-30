package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;

import java.time.Instant;
import java.util.List;

public class UnavailabilityAssignmentConflictErrorResponse extends ErrorResponse {

    private final List<EventAssignmentConflictDTO> conflicts;

    public UnavailabilityAssignmentConflictErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            List<EventAssignmentConflictDTO> conflicts
    ) {
        super(timestamp, status, error, errorCode, path);
        this.conflicts = conflicts;
    }

    public List<EventAssignmentConflictDTO> getConflicts() {
        return conflicts;
    }
}
