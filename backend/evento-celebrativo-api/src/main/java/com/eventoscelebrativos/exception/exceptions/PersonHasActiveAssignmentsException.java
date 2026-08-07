package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.dto.response.PersonAssignmentConflictDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

public class PersonHasActiveAssignmentsException extends ErrorResponseException {

    private final List<PersonAssignmentConflictDTO> assignments;

    public PersonHasActiveAssignmentsException(List<PersonAssignmentConflictDTO> assignments) {
        super(
                "Pessoa possui escala em andamento ou futura.",
                HttpStatus.CONFLICT,
                "PERSON_HAS_ACTIVE_ASSIGNMENTS"
        );
        this.assignments = assignments;
    }

    public List<PersonAssignmentConflictDTO> getAssignments() {
        return assignments;
    }
}
