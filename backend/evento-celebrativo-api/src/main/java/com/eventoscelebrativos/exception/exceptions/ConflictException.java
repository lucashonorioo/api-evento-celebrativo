package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends ErrorResponseException {

    public ConflictException(String msg) {
        super(msg, HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
    }
}
