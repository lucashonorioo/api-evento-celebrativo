package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class ParishProfileNotConfiguredException extends ErrorResponseException {

    public ParishProfileNotConfiguredException() {
        super(
                "O perfil institucional da paróquia ainda não foi configurado",
                HttpStatus.NOT_FOUND,
                "PARISH_PROFILE_NOT_CONFIGURED"
        );
    }
}
