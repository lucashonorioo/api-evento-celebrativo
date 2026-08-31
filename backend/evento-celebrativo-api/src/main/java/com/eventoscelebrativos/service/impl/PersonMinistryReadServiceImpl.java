package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.LegacyMinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryMembershipView;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonMinistryReadServiceImpl implements PersonMinistryReadService {

    private final PersonMinistryRepository personMinistryRepository;
    private final PersonRepository personRepository;
    private final LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    public PersonMinistryReadServiceImpl(
            PersonMinistryRepository personMinistryRepository,
            PersonRepository personRepository,
            LegacyMinistryTypeResolver legacyMinistryTypeResolver
    ) {
        this.personMinistryRepository = personMinistryRepository;
        this.personRepository = personRepository;
        this.legacyMinistryTypeResolver = legacyMinistryTypeResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Person> findActivePeopleByMinistryId(Long ministryId, Pageable pageable) {
        validateMinistryId(ministryId);
        PageRequest safePageable = safePageable(pageable);

        Page<Long> idPage = personMinistryRepository.findActivePersonIdsByMinistryId(ministryId, safePageable);
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
    public List<Person> findAllActivePeopleByMinistryId(Long ministryId) {
        validateMinistryId(ministryId);
        return personMinistryRepository.findActivePeopleByMinistryId(ministryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Person> findActivePeopleByMinistry(MinistryType ministryType, Pageable pageable) {
        validateMinistryType(ministryType);
        PageRequest safePageable = safePageable(pageable);
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        return findActivePeopleByMinistryId(requireMinistryId(ministry), safePageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> findAllActivePeopleByMinistry(MinistryType ministryType) {
        validateMinistryType(ministryType);
        Ministry ministry = legacyMinistryTypeResolver.requireMinistry(ministryType);
        return findAllActivePeopleByMinistryId(requireMinistryId(ministry));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<PersonMinistryMembershipView>> findActiveMinistryMembershipsByPersonIds(Collection<Long> personIds) {
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

        Map<Long, List<PersonMinistryMembershipView>> result = new LinkedHashMap<>();
        distinctIds.forEach(personId -> result.put(personId, List.of()));

        Map<Long, List<PersonMinistryMembershipView>> grouped = personMinistryRepository
                .findActiveMinistriesByPersonIds(distinctIds)
                .stream()
                .collect(Collectors.groupingBy(
                        PersonMinistryRepository.PersonMinistryCatalogView::getPersonId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                row -> new PersonMinistryMembershipView(
                                        row.getMinistryId(),
                                        row.getMinistryName(),
                                        Boolean.TRUE.equals(row.getCoordinator())
                                ),
                                Collectors.toList()
                        )
                ));

        grouped.forEach((personId, memberships) -> result.put(personId, List.copyOf(memberships)));
        return Collections.unmodifiableMap(result);
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

        List<PersonMinistryRepository.PersonMinistryCatalogView> rows =
                personMinistryRepository.findActiveMinistriesByPersonIds(distinctIds);
        Map<Long, MinistryType> legacyTypesByMinistryId = legacyMinistryTypeResolver
                .findTypesByPersistentMinistryId(rows.stream()
                        .map(PersonMinistryRepository.PersonMinistryCatalogView::getMinistryId)
                        .toList());

        rows.forEach(row -> {
            MinistryType ministryType = legacyTypesByMinistryId.get(row.getMinistryId());
            if (ministryType != null) {
                mutableResult.get(row.getPersonId()).add(ministryType);
            }
        });

        Map<Long, Set<MinistryType>> result = new LinkedHashMap<>();
        mutableResult.forEach((personId, ministries) -> result.put(personId, immutableEnumSet(ministries)));
        return Collections.unmodifiableMap(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MinistryType> findActiveCoordinatedMinistriesByPersonId(Long personId) {
        if (personId == null) {
            throw new BusinessException("Id de pessoa e obrigatorio");
        }
        List<MinistryType> coordinated = personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(personId)
                .stream()
                .map(legacyMinistryTypeResolver::findMinistryType)
                .flatMap(Optional::stream)
                .toList();
        if (coordinated.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(coordinated));
    }

    private void validateMinistryType(MinistryType ministryType) {
        if (ministryType == null) {
            throw new BusinessException("Funcao ministerial e obrigatoria");
        }
    }

    private void validateMinistryId(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BusinessException("O Id do ministerio deve ser positivo e nao nulo");
        }
    }

    private Long requireMinistryId(Ministry ministry) {
        if (ministry == null || ministry.getId() == null || ministry.getId() <= 0) {
            throw new BusinessException("Funcao ministerial persistente e obrigatoria");
        }
        return ministry.getId();
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
