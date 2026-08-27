package com.eventoscelebrativos.service;

public interface MinistryAuthorizationService {

    /**
     * Decide, para a Person autenticada atual, se ela pode administrar o Ministry persistente informado.
     * ROLE_ADMIN sempre pode (override global). ROLE_OPERATOR pode somente se coordenar
     * (PersonMinistry.active=true e coordinator=true) exatamente esse Ministry; participar do
     * ministério sem coordenação não concede a capacidade. Ausência de ROLE_ADMIN/ROLE_OPERATOR
     * falha de forma segura (false).
     */
    boolean canManageMinistry(Long ministryId);
}
