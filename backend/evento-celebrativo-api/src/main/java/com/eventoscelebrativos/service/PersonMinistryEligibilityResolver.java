package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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

    public PersonMinistryEligibilityResolver(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
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

        Map<Long, EnumSet<MinistryType>> activeMinistriesByPersonId = new HashMap<>();
        personMinistryRepository.findActiveMinistryTypesByPersonIds(allPersonIds)
                .forEach(row -> activeMinistriesByPersonId
                        .computeIfAbsent(row.getPersonId(), id -> EnumSet.noneOf(MinistryType.class))
                        .add(row.getMinistryType()));

        List<ScaleParticipantEligibility> results = new ArrayList<>();
        for (Map.Entry<MinistryType, List<Long>> entry : personIdsByMinistry.entrySet()) {
            MinistryType ministryType = entry.getKey();
            Set<Long> requestedIds = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (Long personId : requestedIds) {
                Person person = peopleById.get(personId);
                boolean personFound = person != null;
                boolean ministryAssigned = personFound && activeMinistriesByPersonId
                        .getOrDefault(personId, EnumSet.noneOf(MinistryType.class))
                        .contains(ministryType);

                results.add(new ScaleParticipantEligibility(personId, ministryType, personFound, ministryAssigned, person));
            }
        }
        return List.copyOf(results);
    }
}
