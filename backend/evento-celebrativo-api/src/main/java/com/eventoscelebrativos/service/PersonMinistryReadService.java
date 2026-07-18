package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface PersonMinistryReadService {

    Page<Person> findActivePeopleByMinistry(MinistryType ministryType, Pageable pageable);

    Map<Long, Set<MinistryType>> findActiveMinistriesByPersonIds(Collection<Long> personIds);
}
