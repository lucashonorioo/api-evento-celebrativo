package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class MinistryInactiveException extends ErrorResponseException {

    public MinistryInactiveException() {
        super(
                "Ministerio inativo nao pode receber operacoes ministeriais.",
                HttpStatus.CONFLICT,
                "MINISTRY_INACTIVE"
        );
    }
}
