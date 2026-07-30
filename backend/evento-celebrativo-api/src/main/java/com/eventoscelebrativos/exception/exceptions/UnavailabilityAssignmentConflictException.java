package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.dto.response.EventAssignmentConflictDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

public class UnavailabilityAssignmentConflictException extends ErrorResponseException {

    private final List<EventAssignmentConflictDTO> conflicts;

    public UnavailabilityAssignmentConflictException(List<EventAssignmentConflictDTO> conflicts) {
        super("A indisponibilidade conflita com escalas existentes.", HttpStatus.CONFLICT, "UNAVAILABILITY_CONFLICT_WITH_ASSIGNMENT");
        this.conflicts = conflicts;
    }

    public List<EventAssignmentConflictDTO> getConflicts() {
        return conflicts;
    }
}
