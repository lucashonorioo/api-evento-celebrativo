package com.eventoscelebrativos.exception.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Divergencia entre Person e UserAccount detectada durante a escrita dupla (conta ausente,
 * conta duplicada ou estado inconsistente). Provoca rollback da transacao do caso de uso legado;
 * nao deve ser tratada como erro de entrada do cliente.
 */
public class UserAccountConsistencyException extends ErrorResponseException {
    public UserAccountConsistencyException(String msg) {
        super(msg, HttpStatus.INTERNAL_SERVER_ERROR, "USER_ACCOUNT_CONSISTENCY_VIOLATION");
    }
}
