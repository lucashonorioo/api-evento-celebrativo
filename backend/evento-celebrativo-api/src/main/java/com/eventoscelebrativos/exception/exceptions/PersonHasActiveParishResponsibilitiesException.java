package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class PersonHasActiveParishResponsibilitiesException extends ErrorResponseException {

    public PersonHasActiveParishResponsibilitiesException() {
        super(
                "Nao e possivel desativar a pessoa enquanto ela possuir responsabilidade paroquial ativa. "
                        + "Remova as responsabilidades antes de desativar a pessoa.",
                HttpStatus.CONFLICT,
                "PERSON_HAS_ACTIVE_PARISH_RESPONSIBILITIES"
        );
    }
}
