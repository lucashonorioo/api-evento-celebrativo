package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ErrorResponseException {

    public BadRequestException(String msg) {
        super(msg, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }
}
