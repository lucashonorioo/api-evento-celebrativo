package com.eventoscelebrativos.service;

public interface MinistryCoordinationService {

    /**
     * Concede ou reconfirma (idempotente) a coordenacao do ministerio persistente para a Person.
     * Exige PersonMinistry(personId, ministryId).active=true; nao cria o vinculo ministerial.
     */
    void grantCoordinator(Long personId, Long ministryId);

    /**
     * Remove (idempotente) a coordenacao do ministerio para a Person. Nao desativa o ministerio nem
     * remove a linha fisicamente.
     */
    void revokeCoordinator(Long personId, Long ministryId);
}
