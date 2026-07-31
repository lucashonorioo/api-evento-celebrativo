package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.dto.response.StartedAssignmentConflictDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

public class UnavailabilityConflictWithStartedAssignmentException extends ErrorResponseException {

    private final List<StartedAssignmentConflictDTO> conflicts;

    public UnavailabilityConflictWithStartedAssignmentException(List<StartedAssignmentConflictDTO> conflicts) {
        super(
                "Não é possível registrar uma indisponibilidade que conflite com um evento já iniciado.",
                HttpStatus.CONFLICT,
                "UNAVAILABILITY_CONFLICT_WITH_STARTED_ASSIGNMENT"
        );
        this.conflicts = conflicts;
    }

    public List<StartedAssignmentConflictDTO> getConflicts() {
        return conflicts;
    }
}
