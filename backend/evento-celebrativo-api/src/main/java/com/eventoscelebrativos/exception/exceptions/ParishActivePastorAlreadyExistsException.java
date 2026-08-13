package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class ParishActivePastorAlreadyExistsException extends ErrorResponseException {

    public ParishActivePastorAlreadyExistsException() {
        super(
                "Ja existe uma pessoa diferente como PASTOR ativo. Remova a responsabilidade atual antes de nomear outra.",
                HttpStatus.CONFLICT,
                "PARISH_ACTIVE_PASTOR_ALREADY_EXISTS"
        );
    }
}
