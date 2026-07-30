package com.eventoscelebrativos.exception.error;

import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;

import java.time.Instant;
import java.util.List;

public class PersonUnavailableForEventErrorResponse extends ErrorResponse {

    private final List<PersonUnavailabilityEventConflictDTO> conflicts;

    public PersonUnavailableForEventErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String path,
            List<PersonUnavailabilityEventConflictDTO> conflicts
    ) {
        super(timestamp, status, error, errorCode, path);
        this.conflicts = conflicts;
    }

    public List<PersonUnavailabilityEventConflictDTO> getConflicts() {
        return conflicts;
    }
}
