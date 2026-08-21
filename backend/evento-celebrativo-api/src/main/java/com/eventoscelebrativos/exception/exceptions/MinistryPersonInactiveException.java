package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Adicionar ou reativar um vinculo PersonMinistry exige Person.active=true. O coordenador nao tem
 * autoridade para reativar a Person globalmente; essa e uma operacao administrativa separada.
 */
public class MinistryPersonInactiveException extends ErrorResponseException {

    public MinistryPersonInactiveException() {
        super(
                "Nao e possivel vincular ou reativar o ministerio: a pessoa esta inativa.",
                HttpStatus.CONFLICT,
                "MINISTRY_PERSON_INACTIVE"
        );
    }
}
