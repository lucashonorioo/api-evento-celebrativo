package com.eventoscelebrativos.exception.handler;

import com.eventoscelebrativos.exception.error.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.lenient;

/**
 * Prova o mapeamento de GlobalExceptionHandler.handleDataIntegrityViolationException usando uma
 * cadeia de causas realista (DataIntegrityViolationException -> Hibernate ConstraintViolationException
 * -> SQLIntegrityConstraintViolationException), no mesmo formato produzido pelo driver JDBC/Hibernate
 * em uma violacao real de constraint, sem depender de banco real nem de MockMvc.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private WebRequest webRequest;

    @Test
    void shouldConvertEventAssignmentUniqueConstraintViolationToMultipleAssignmentsConflict() {
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/eventos/1/escala");
        DataIntegrityViolationException exception = eventAssignmentConstraintViolation();

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(exception, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("MULTIPLE_ASSIGNMENTS_FOR_PERSON_IN_EVENT", response.getBody().getErrorCode());
        assertNotEquals("DATABASE_RULE_VIOLATION", response.getBody().getErrorCode());
    }

    @Test
    void shouldKeepGenericDatabaseRuleViolationForUnrelatedConstraint() {
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/eventos/1");
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails "
                        + "(`evento`.`tb_event_assignment`, CONSTRAINT `fk_tb_event_assignment_event` "
                        + "FOREIGN KEY (`event_id`) REFERENCES `tb_celebration_event` (`id`))"
        );
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement", sqlException, "fk_tb_event_assignment_event");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement", hibernateException);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(exception, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATABASE_RULE_VIOLATION", response.getBody().getErrorCode());
    }

    @Test
    void shouldKeepUnavailabilityOverlapMappingUnaffectedByNewConstraint() {
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/pessoas/me/indisponibilidades");
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '5-2026-08-10' for key "
                        + "'tb_person_unavailability.uk_tb_person_unavailability_person_range'"
        );
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement", sqlException, "uk_tb_person_unavailability_person_range");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement", hibernateException);

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(exception, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("UNAVAILABILITY_OVERLAP", response.getBody().getErrorCode());
    }

    /**
     * Prova o fallback generico do handler para deadlock/timeout de lock pessimista transitorio (ex.:
     * contencao real de linha ja existente sob PESSIMISTIC_WRITE). Sem este handler, a excecao
     * vazava como 500 cru em vez de um erro de dominio estavel.
     * <p>
     * Nao cobre mais o conflito de Person.phoneNumber/UserAccount.username: esse cenario, antes
     * dependente de deadlock genuino por gap lock do InnoDB (SELECT ... FOR UPDATE sobre um valor
     * unico ainda inexistente), agora e resolvido sem lock pessimista sobre o valor novo e sem
     * depender de deadlock - ver PersonPhoneNumberContentionConcurrencyMySqlIntegrationTest.
     */
    @Test
    void shouldConvertPessimisticLockingFailureToStableConcurrentUpdateConflict() {
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/pessoas/1");
        PessimisticLockingFailureException exception = new CannotAcquireLockException(
                "could not execute statement", new RuntimeException("Deadlock found when trying to get lock"));

        ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockingFailure(exception, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONCURRENT_UPDATE_CONFLICT", response.getBody().getErrorCode());
    }

    private DataIntegrityViolationException eventAssignmentConstraintViolation() {
        SQLIntegrityConstraintViolationException sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '10-5' for key 'tb_event_assignment.uk_tb_event_assignment_event_person'"
        );
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "could not execute statement", sqlException, "uk_tb_event_assignment_event_person");
        return new DataIntegrityViolationException("could not execute statement", hibernateException);
    }
}
