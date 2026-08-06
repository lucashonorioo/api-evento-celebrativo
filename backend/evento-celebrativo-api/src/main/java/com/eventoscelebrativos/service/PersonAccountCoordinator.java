package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonAccessRequest;
import com.eventoscelebrativos.model.Person;

/**
 * Unica fonte de provisionamento e sincronizacao de {@link com.eventoscelebrativos.model.UserAccount}
 * a partir do cadastro de {@link Person}. Substitui o antigo UserAccountSynchronizationService: Person
 * nunca carrega senha ou perfis, entao nao ha mais "estado legado" para sincronizar - apenas decisoes
 * de provisionamento (na criacao) e propagacao de telefone/username (na atualizacao).
 */
public interface PersonAccountCoordinator {

    /**
     * Interpreta createAccess/password/accessRole em um plano imutavel, sem efeitos colaterais.
     */
    PersonAccessProvisioningPlan interpretCreationRequest(PersonAccessRequest request);

    /**
     * Aplica o plano de acesso a uma {@link Person} ja persistida (com Id), criando UserAccount e
     * UserAccountRole quando o plano solicitar acesso. Nao faz nada quando o plano nao solicita acesso.
     */
    void provisionAccess(Person person, PersonAccessRequest request);

    /**
     * Apos uma atualizacao cadastral (ex.: troca de telefone), propaga o novo telefone para
     * UserAccount.username quando a pessoa possuir conta, travando Person (responsabilidade do
     * chamador) e UserAccount, validando unicidade e incrementando tokenVersion uma unica vez quando
     * o username realmente mudou. Pessoa sem conta: no-op (nao cria conta).
     */
    void synchronizeAccountAfterPersonUpdate(Person person);
}
