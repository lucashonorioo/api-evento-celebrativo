package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Person;

/**
 * Mantem UserAccount (schema paralelo de conta de acesso) sincronizado com Person (fonte de
 * verdade legada) dentro da mesma transacao do caso de uso que originou a mudanca. Nao decide
 * autenticacao nem autorizacao; apenas espelha telefone, senha e roles.
 */
public interface UserAccountSynchronizationService {

    /**
     * Cria a UserAccount para uma Person recem-persistida pelos fluxos atuais. Deve ser chamado
     * na mesma transacao logo apos o save inicial da Person.
     */
    void synchronizeNewPerson(Person person);

    /**
     * Sincroniza a UserAccount de uma Person ja existente apos alteracao de telefone, senha
     * e/ou roles. A ausencia da conta representa uma inconsistencia e provoca falha/rollback.
     */
    void synchronizeExistingPerson(Person person);
}
