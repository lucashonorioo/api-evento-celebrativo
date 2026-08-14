package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Coordenacao so pode ser concedida sobre um vinculo PersonMinistry ja existente e ativo. Nao cria
 * o ministerio implicitamente nem permite conceder coordenacao sobre um vinculo inativo.
 */
public class MinistryCoordinationRequiresActiveMinistryException extends ErrorResponseException {

    public MinistryCoordinationRequiresActiveMinistryException() {
        super(
                "A coordenação exige vínculo ministerial ativo.",
                HttpStatus.CONFLICT,
                "MINISTRY_COORDINATION_REQUIRES_ACTIVE_MINISTRY"
        );
    }
}
