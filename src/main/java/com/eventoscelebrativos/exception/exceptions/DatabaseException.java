package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class DatabaseException extends ErrorResponseException{
    public DatabaseException(String msg) {
        super(msg, HttpStatus.CONFLICT, "DATABASE_RULE_VIOLATION");
    }
}
