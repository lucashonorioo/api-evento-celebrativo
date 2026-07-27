package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;

import java.util.Set;

/**
 * Fonte oficial de escrita dos CRUDs ministeriais: Person + PersonMinistry.
 * A classificação ministerial é sempre informada explicitamente pelo chamador
 * (nunca inferida por instanceof, person_type ou repository de subtipo).
 */
public interface PersonMinistryCommandService {

    Person create(Person person, MinistryType ministryType);

    Person requireActiveMinistryPerson(Long personId, MinistryType ministryType, String entityLabel);

    Person save(Person person);

    void removeMinistry(Long personId, MinistryType ministryType, String entityLabel);

    /**
     * Aplica atomicamente o conjunto desejado de ministérios de uma pessoa já existente:
     * adiciona vínculos inexistentes, reativa vínculos inativos, preserva vínculos inalterados
     * e desativa vínculos ativos ausentes do conjunto desejado. Nenhuma mudança é aplicada se
     * qualquer vínculo a desativar possuir {@link com.eventoscelebrativos.model.EventAssignment}
     * do mesmo tipo.
     */
    PersonMinistrySyncResult syncMinistries(Long personId, Set<MinistryType> desiredMinistries);
}
