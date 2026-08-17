package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;

public interface MinistryAuthorizationService {

    /**
     * Decide, para a Person autenticada atual, se ela pode administrar o MinistryType informado.
     * ROLE_ADMIN sempre pode (override global). ROLE_OPERATOR pode somente se coordenar
     * (PersonMinistry.active=true e coordinator=true) exatamente esse MinistryType; participar do
     * ministério sem coordenação não concede a capacidade. ministryType nulo ou ausência de
     * ROLE_ADMIN/ROLE_OPERATOR falham de forma segura (false).
     */
    boolean canManageMinistry(MinistryType ministryType);
}
