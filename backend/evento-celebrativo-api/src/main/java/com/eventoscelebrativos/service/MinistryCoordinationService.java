package com.eventoscelebrativos.service;

public interface MinistryCoordinationService {

    /**
     * Concede ou reconfirma (idempotente) a coordenacao do ministerio para a Person. Exige
     * PersonMinistry(personId, ministryType).active=true; nao cria o vinculo ministerial.
     *
     * @param rawMinistryType nome do {@link com.eventoscelebrativos.model.MinistryType}, validado aqui
     */
    void grantCoordinator(Long personId, String rawMinistryType);

    /**
     * Remove (idempotente) a coordenacao do ministerio para a Person. Nao desativa o ministerio nem
     * remove a linha fisicamente.
     */
    void revokeCoordinator(Long personId, String rawMinistryType);
}
