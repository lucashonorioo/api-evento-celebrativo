package com.eventoscelebrativos.exception.handler;

import com.eventoscelebrativos.exception.error.ErrorResponse;
import com.eventoscelebrativos.exception.error.MultipleAssignmentsForPersonInEventErrorResponse;
import com.eventoscelebrativos.exception.error.NotificationInvalidRecipientsErrorResponse;
import com.eventoscelebrativos.exception.error.PersonUnavailableForEventErrorResponse;
import com.eventoscelebrativos.exception.error.UnavailabilityConflictWithStartedAssignmentErrorResponse;
import com.eventoscelebrativos.exception.exceptions.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String UNAVAILABILITY_UNIQUE_CONSTRAINT = "uk_tb_person_unavailability_person_range";
    private static final String EVENT_ASSIGNMENT_UNIQUE_CONSTRAINT = "uk_tb_event_assignment_event_person";

    @ExceptionHandler(UnavailabilityConflictWithStartedAssignmentException.class)
    public ResponseEntity<UnavailabilityConflictWithStartedAssignmentErrorResponse> handleUnavailabilityConflictWithStartedAssignment(
            UnavailabilityConflictWithStartedAssignmentException ex, WebRequest webRequest
    ) {
        UnavailabilityConflictWithStartedAssignmentErrorResponse errorResponse = new UnavailabilityConflictWithStartedAssignmentErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                webRequest.getDescription(false),
                ex.getConflicts()
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(PersonUnavailableForEventException.class)
    public ResponseEntity<PersonUnavailableForEventErrorResponse> handlePersonUnavailableForEvent(
            PersonUnavailableForEventException ex, WebRequest webRequest
    ) {
        PersonUnavailableForEventErrorResponse errorResponse = new PersonUnavailableForEventErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                webRequest.getDescription(false),
                ex.getConflicts()
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(MultipleAssignmentsForPersonInEventException.class)
    public ResponseEntity<MultipleAssignmentsForPersonInEventErrorResponse> handleMultipleAssignmentsForPersonInEvent(
            MultipleAssignmentsForPersonInEventException ex, WebRequest webRequest
    ) {
        MultipleAssignmentsForPersonInEventErrorResponse errorResponse = new MultipleAssignmentsForPersonInEventErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                webRequest.getDescription(false),
                ex.getEventId(),
                ex.getPersonId(),
                ex.getConflictingAssignmentTypes()
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(NotificationInvalidRecipientsException.class)
    public ResponseEntity<NotificationInvalidRecipientsErrorResponse> handleNotificationInvalidRecipients(
            NotificationInvalidRecipientsException ex, WebRequest webRequest
    ) {
        NotificationInvalidRecipientsErrorResponse errorResponse = new NotificationInvalidRecipientsErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                webRequest.getDescription(false),
                ex.getInvalidRecipients()
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e, WebRequest webRequest){
        HttpStatus status = HttpStatus.CONFLICT;
        String rootCauseMessage = rootCauseMessage(e);
        if (rootCauseMessage != null && rootCauseMessage.toLowerCase().contains(UNAVAILABILITY_UNIQUE_CONSTRAINT)) {
            ErrorResponse errorResponse = new ErrorResponse(
                    Instant.now(),
                    status.value(),
                    "O período informado se sobrepõe a uma indisponibilidade já cadastrada.",
                    "UNAVAILABILITY_OVERLAP",
                    webRequest.getDescription(false)
            );
            return ResponseEntity.status(status).body(errorResponse);
        }
        if (rootCauseMessage != null && rootCauseMessage.toLowerCase().contains(EVENT_ASSIGNMENT_UNIQUE_CONSTRAINT)) {
            ErrorResponse errorResponse = new ErrorResponse(
                    Instant.now(),
                    status.value(),
                    "A pessoa não pode exercer mais de uma função no mesmo evento.",
                    "MULTIPLE_ASSIGNMENTS_FOR_PERSON_IN_EVENT",
                    webRequest.getDescription(false)
            );
            return ResponseEntity.status(status).body(errorResponse);
        }
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                "Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.",
                "DATABASE_RULE_VIOLATION",
                webRequest.getDescription(false)
        );
        return ResponseEntity.status(status).body(errorResponse);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

}
