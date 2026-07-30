package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class UnavailabilityOverlapException extends ErrorResponseException {

    public UnavailabilityOverlapException(String msg) {
        super(msg, HttpStatus.CONFLICT, "UNAVAILABILITY_OVERLAP");
    }
}
