package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

public class TemporalPrecisionNotSupportedException extends ErrorResponseException {

    public TemporalPrecisionNotSupportedException() {
        super(
                "Frações de segundo não são suportadas. Use o formato yyyy-MM-dd'T'HH:mm:ss.",
                HttpStatus.BAD_REQUEST,
                "TEMPORAL_PRECISION_NOT_SUPPORTED"
        );
    }
}
