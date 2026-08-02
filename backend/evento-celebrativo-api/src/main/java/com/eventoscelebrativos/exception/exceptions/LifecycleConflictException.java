package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class LifecycleConflictException extends ErrorResponseException {

    public LifecycleConflictException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}
