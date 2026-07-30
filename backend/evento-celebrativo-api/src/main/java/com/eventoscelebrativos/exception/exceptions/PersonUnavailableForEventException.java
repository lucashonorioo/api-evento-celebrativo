package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.dto.response.PersonUnavailabilityEventConflictDTO;
import org.springframework.http.HttpStatus;

import java.util.List;

public class PersonUnavailableForEventException extends ErrorResponseException {

    private final List<PersonUnavailabilityEventConflictDTO> conflicts;

    public PersonUnavailableForEventException(List<PersonUnavailabilityEventConflictDTO> conflicts) {
        super("Uma ou mais pessoas estão indisponíveis na data do evento.", HttpStatus.CONFLICT, "PERSON_UNAVAILABLE_FOR_EVENT");
        this.conflicts = conflicts;
    }

    public List<PersonUnavailabilityEventConflictDTO> getConflicts() {
        return conflicts;
    }
}
