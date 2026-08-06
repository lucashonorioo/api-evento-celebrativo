package com.eventoscelebrativos.service;

/**
 * Modelo transitorio e nao persistente que carrega a decisao de provisionamento de acesso
 * interpretada a partir de um {@link com.eventoscelebrativos.dto.request.PersonAccessRequest}.
 * Nunca e associado a {@link com.eventoscelebrativos.model.Person}: existe somente durante o
 * processamento da requisicao de criacao, para orientar {@link PersonAccountCoordinator}.
 */
public record PersonAccessProvisioningPlan(boolean createAccess, String rawPassword, String roleAuthority) {

    public static PersonAccessProvisioningPlan withoutAccess() {
        return new PersonAccessProvisioningPlan(false, null, null);
    }

    public static PersonAccessProvisioningPlan withAccess(String rawPassword, String roleAuthority) {
        return new PersonAccessProvisioningPlan(true, rawPassword, roleAuthority);
    }
}
