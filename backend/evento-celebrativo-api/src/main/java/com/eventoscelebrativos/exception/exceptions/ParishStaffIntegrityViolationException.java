package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Estado interno impossivel ja persistido (ex.: mais de um PASTOR ativo), detectado na leitura ou
 * em uma tentativa de mutacao. Nao e um conflito de uma operacao solicitada (por isso HTTP 500, nao
 * 409): representa corrupcao de dados que precisa de intervencao administrativa (ex.: revokePastor
 * de uma das Persons envolvidas), nao uma tentativa automatica de reparo.
 */
public class ParishStaffIntegrityViolationException extends ErrorResponseException {

    public ParishStaffIntegrityViolationException() {
        super(
                "Inconsistência detectada na configuração da equipe paroquial.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PARISH_STAFF_INTEGRITY_VIOLATION"
        );
    }
}
