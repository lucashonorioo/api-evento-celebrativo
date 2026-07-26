package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;

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
}
