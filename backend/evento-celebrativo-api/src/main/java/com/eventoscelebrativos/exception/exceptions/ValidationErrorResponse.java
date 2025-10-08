package com.eventoscelebrativos.exception.exceptions;

import com.eventoscelebrativos.exception.error.ErrorResponse;
import com.eventoscelebrativos.exception.error.FieldMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ValidationErrorResponse extends ErrorResponse {

    List<FieldMessage> fieldErrors = new ArrayList<>();

    public ValidationErrorResponse(Instant timestamp, int status, String error, String errorCode, String path) {
        super(timestamp, status, error, errorCode, path);
    }

    public void addError(String fieldName, String message){
        fieldErrors.add(new FieldMessage(fieldName, message));
    }

    public List<FieldMessage> getFieldErrors() {
        return fieldErrors;
    }
}
