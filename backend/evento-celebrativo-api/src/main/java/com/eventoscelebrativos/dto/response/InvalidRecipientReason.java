package com.eventoscelebrativos.dto.response;

/**
 * Motivo pelo qual um personId informado em um envio DIRECT nao e um destinatario valido.
 */
public enum InvalidRecipientReason {
    PERSON_NOT_FOUND,
    USER_ACCOUNT_NOT_FOUND,
    USER_ACCOUNT_DISABLED,
    PERSON_INACTIVE,
    USER_ACCOUNT_ROLE_INVALID
}
