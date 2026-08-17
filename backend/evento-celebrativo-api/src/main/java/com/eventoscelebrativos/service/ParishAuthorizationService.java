package com.eventoscelebrativos.service;

public interface ParishAuthorizationService {

    /**
     * Decide, para a Person autenticada atual, se ela pode executar as operações administrativas
     * reservadas à secretaria paroquial. ROLE_ADMIN sempre pode (override global). ROLE_OPERATOR pode
     * somente se possuir ParishStaffAssignment(PARISH_SECRETARY).active=true. Ser PASTOR ou coordenar
     * um ministério não concede essa capacidade. Escopo deliberadamente restrito às operações futuras
     * de secretaria: não representa autoridade total sobre a paróquia (nome, diocese, contas, roles e
     * nomeação de PASTOR/secretaria continuam ADMIN-only).
     */
    boolean canPerformSecretaryOperations();
}
