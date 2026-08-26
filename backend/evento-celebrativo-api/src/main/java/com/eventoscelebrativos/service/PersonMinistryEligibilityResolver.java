package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fonte oficial de elegibilidade ministerial para a escrita de escalas: responde se uma pessoa
 * possui, em {@code tb_person_ministry}, o {@link MinistryType} solicitado - sem consultar
 * repositories de subtipo legado nem {@code person_type}.
 */
@Component
public class PersonMinistryEligibilityResolver {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final LegacyMinistryTypeResolver legacyMinistryTypeResolver;

    public PersonMinistryEligibilityResolver(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            LegacyMinistryTypeResolver legacyMinistryTypeResolver
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.legacyMinistryTypeResolver = legacyMinistryTypeResolver;
    }

    public List<ScaleParticipantEligibility> resolve(Map<MinistryType, List<Long>> personIdsByMinistry) {
        if (personIdsByMinistry == null || personIdsByMinistry.isEmpty()) {
            return List.of();
        }

        Set<Long> allPersonIds = personIdsByMinistry.values().stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allPersonIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Person> peopleById = personRepository.findAllByIdIn(allPersonIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        Map<MinistryType, Long> ministryIdByType = legacyMinistryTypeResolver
                .requireMinistries(personIdsByMinistry.keySet().stream().filter(Objects::nonNull).toList())
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getId()));

        Map<Long, Set<Long>> activeMinistryIdsByPersonId = new HashMap<>();
        personMinistryRepository.findActiveMinistriesByPersonIds(allPersonIds)
                .forEach(row -> activeMinistryIdsByPersonId
                        .computeIfAbsent(row.getPersonId(), id -> new LinkedHashSet<>())
                        .add(row.getMinistryId()));

        List<ScaleParticipantEligibility> results = new ArrayList<>();
        for (Map.Entry<MinistryType, List<Long>> entry : personIdsByMinistry.entrySet()) {
            MinistryType ministryType = entry.getKey();
            Long ministryId = ministryType == null ? null : ministryIdByType.get(ministryType);
            Set<Long> requestedIds = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (Long personId : requestedIds) {
                Person person = peopleById.get(personId);
                boolean personFound = person != null;
                boolean ministryAssigned = personFound && ministryId != null && activeMinistryIdsByPersonId
                        .getOrDefault(personId, Set.of())
                        .contains(ministryId);

                results.add(new ScaleParticipantEligibility(personId, ministryType, personFound, ministryAssigned, person));
            }
        }
        return List.copyOf(results);
    }
}
