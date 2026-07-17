package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;

public interface PersonMinistryCompatibilityService {

    PersonMinistry ensureMinistry(Person person, MinistryType ministryType);

    void deleteAllForPerson(Long personId);
}
