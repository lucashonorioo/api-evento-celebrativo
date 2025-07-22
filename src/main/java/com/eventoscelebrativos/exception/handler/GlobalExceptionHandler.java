package com.eventoscelebrativos.exception.handler;

import com.eventoscelebrativos.exception.error.ErrorResponse;
import com.eventoscelebrativos.exception.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleCustomExceptions(ErrorResponseException ex, WebRequest webRequest){
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                webRequest.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest webRequest){
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ValidationErrorResponse validationErrorResponse = new ValidationErrorResponse(
                Instant.now(),
                status.value(),
                "Dados inválidos.",
                "VALIDATION_ERROR",
                webRequest.getDescription(false)
        );
        for(FieldError fieldError : ex.getBindingResult().getFieldErrors()){
            validationErrorResponse.addError(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(status).body(validationErrorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest webRequest){
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                "Corpo da requisição inválido. Verifique o formato JSON e o tipo de dados.",
                "INVALID_REQUEST_BODY",
                webRequest.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ErrorResponse> handleDatabasesException(DatabaseException e, WebRequest webRequest){
        HttpStatus status = e.getStatus();
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                e.getMessage(),
                e.getErrorCode(),
                webRequest.getDescription(false)
        );
        return ResponseEntity.status(status).body(errorResponse);
    }


}
