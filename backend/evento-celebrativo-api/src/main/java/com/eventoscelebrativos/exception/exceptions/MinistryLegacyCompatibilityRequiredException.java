package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class MinistryLegacyCompatibilityRequiredException extends ErrorResponseException {

    public MinistryLegacyCompatibilityRequiredException() {
        super(
                "Ministerio ainda nao pode ser vinculado a pessoas enquanto a coluna legacy ministry_type estiver ativa.",
                HttpStatus.CONFLICT,
                "MINISTRY_LEGACY_COMPATIBILITY_REQUIRED"
        );
    }
}
