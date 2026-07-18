package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonMinistryReadServiceImpl implements PersonMinistryReadService {

    private final PersonMinistryRepository personMinistryRepository;
    private final PersonRepository personRepository;

    public PersonMinistryReadServiceImpl(
            PersonMinistryRepository personMinistryRepository,
            PersonRepository personRepository
    ) {
        this.personMinistryRepository = personMinistryRepository;
        this.personRepository = personRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Person> findActivePeopleByMinistry(MinistryType ministryType, Pageable pageable) {
        validateMinistryType(ministryType);
        PageRequest safePageable = safePageable(pageable);

        Page<Long> idPage = personMinistryRepository.findActivePersonIdsByMinistryType(ministryType, safePageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), safePageable, idPage.getTotalElements());
        }

        List<Long> ids = idPage.getContent();
        Map<Long, Person> peopleById = personRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        List<Person> content = ids.stream()
                .map(peopleById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(content, safePageable, idPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Set<MinistryType>> findActiveMinistriesByPersonIds(Collection<Long> personIds) {
        if (personIds == null) {
            throw new BusinessException("Ids de pessoas sao obrigatorios");
        }

        List<Long> distinctIds = personIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, EnumSet<MinistryType>> mutableResult = new LinkedHashMap<>();
        distinctIds.forEach(personId -> mutableResult.put(personId, EnumSet.noneOf(MinistryType.class)));

        personMinistryRepository.findActiveMinistryTypesByPersonIds(distinctIds)
                .forEach(row -> mutableResult.get(row.getPersonId()).add(row.getMinistryType()));

        Map<Long, Set<MinistryType>> result = new LinkedHashMap<>();
        mutableResult.forEach((personId, ministries) -> result.put(personId, immutableEnumSet(ministries)));
        return Collections.unmodifiableMap(result);
    }

    private void validateMinistryType(MinistryType ministryType) {
        if (ministryType == null) {
            throw new BusinessException("Funcao ministerial e obrigatoria");
        }
    }

    private PageRequest safePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            throw new BusinessException("Paginacao e obrigatoria");
        }
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() <= 0) {
            throw new BusinessException("Paginacao invalida");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private Set<MinistryType> immutableEnumSet(EnumSet<MinistryType> ministries) {
        if (ministries.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(ministries));
    }
}
