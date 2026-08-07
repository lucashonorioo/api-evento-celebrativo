package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class PersonPhoneNumberConflictException extends ErrorResponseException {

    public PersonPhoneNumberConflictException(String msg) {
        super(msg, HttpStatus.CONFLICT, "PERSON_PHONE_NUMBER_CONFLICT");
    }
}
