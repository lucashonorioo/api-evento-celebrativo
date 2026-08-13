package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Invariante bidirecional entre a responsabilidade PASTOR e o ministerio PRIEST: lancada tanto ao
 * tentar ativar PASTOR sem PersonMinistry(PRIEST).active=true quanto ao tentar desativar PRIEST de
 * uma Person que e PASTOR ativo.
 */
public class PastorPriestMinistryRequiredException extends ErrorResponseException {

    public PastorPriestMinistryRequiredException() {
        super(
                "PASTOR exige PersonMinistry(PRIEST) ativo; nao e possivel ativar PASTOR sem PRIEST ativo, "
                        + "nem desativar PRIEST enquanto a pessoa for PASTOR ativo.",
                HttpStatus.CONFLICT,
                "PASTOR_PRIEST_MINISTRY_REQUIRED"
        );
    }
}
