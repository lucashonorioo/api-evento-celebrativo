package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryConsistencyEntry;
import com.eventoscelebrativos.service.PersonMinistryConsistencyIssueType;
import com.eventoscelebrativos.service.PersonMinistryConsistencyReport;
import com.eventoscelebrativos.service.PersonMinistryConsistencyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonMinistryConsistencyServiceImpl implements PersonMinistryConsistencyService {

    private final PersonRepository personRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final MinistryTypeResolver ministryTypeResolver;

    public PersonMinistryConsistencyServiceImpl(
            PersonRepository personRepository,
            PersonMinistryRepository personMinistryRepository,
            MinistryTypeResolver ministryTypeResolver
    ) {
        this.personRepository = personRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.ministryTypeResolver = ministryTypeResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public PersonMinistryConsistencyReport audit(int batchSize) {
        if (batchSize <= 0) {
            throw new BusinessException("O tamanho do lote deve ser maior que zero");
        }

        Summary summary = new Summary();
        int pageNumber = 0;
        Page<Long> idPage;

        do {
            PageRequest pageable = PageRequest.of(pageNumber, batchSize);
            idPage = personRepository.findPersonIdsForMinistryAudit(pageable);
            processBatch(idPage.getContent(), summary);
            pageNumber++;
        } while (idPage.hasNext());

        return summary.toReport();
    }

    private void processBatch(List<Long> ids, Summary summary) {
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, Person> peopleById = personRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        Map<Long, Map<MinistryType, Boolean>> ministriesByPersonId = findMinistriesByPersonId(ids);

        for (Long id : ids) {
            Person person = peopleById.get(id);
            if (person != null) {
                summary.add(auditPerson(person, ministriesByPersonId.getOrDefault(id, Collections.emptyMap())));
            }
        }
    }

    private Map<Long, Map<MinistryType, Boolean>> findMinistriesByPersonId(List<Long> ids) {
        Map<Long, Map<MinistryType, Boolean>> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new EnumMap<>(MinistryType.class)));

        personMinistryRepository.findAllMinistryStatusesByPersonIds(ids)
                .forEach(row -> result.get(row.getPersonId()).put(row.getMinistryType(), Boolean.TRUE.equals(row.getActive())));

        return result;
    }

    private PersonMinistryConsistencyEntry auditPerson(Person person, Map<MinistryType, Boolean> ministryStatuses) {
        EnumSet<MinistryType> activeMinistries = activeMinistries(ministryStatuses);
        MinistryType expectedMinistry = resolveExpectedMinistry(person);

        if (expectedMinistry == null) {
            return new PersonMinistryConsistencyEntry(
                    person.getId(),
                    person.getName(),
                    person.getPersonType(),
                    null,
                    activeMinistries,
                    activeMinistries,
                    PersonMinistryConsistencyIssueType.UNSUPPORTED_LEGACY_PERSON_TYPE
            );
        }

        EnumSet<MinistryType> additionalMinistries = EnumSet.copyOf(activeMinistries);
        additionalMinistries.remove(expectedMinistry);

        PersonMinistryConsistencyIssueType issueType = null;
        Boolean expectedActive = ministryStatuses.get(expectedMinistry);
        if (expectedActive == null) {
            issueType = PersonMinistryConsistencyIssueType.MISSING_EXPECTED_MINISTRY;
        } else if (Boolean.FALSE.equals(expectedActive)) {
            issueType = PersonMinistryConsistencyIssueType.EXPECTED_MINISTRY_INACTIVE;
        }

        return new PersonMinistryConsistencyEntry(
                person.getId(),
                person.getName(),
                person.getPersonType(),
                expectedMinistry,
                activeMinistries,
                additionalMinistries,
                issueType
        );
    }

    private MinistryType resolveExpectedMinistry(Person person) {
        try {
            return ministryTypeResolver.resolve(person);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private EnumSet<MinistryType> activeMinistries(Map<MinistryType, Boolean> ministryStatuses) {
        EnumSet<MinistryType> activeMinistries = EnumSet.noneOf(MinistryType.class);
        ministryStatuses.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(Objects::nonNull)
                .forEach(activeMinistries::add);
        return activeMinistries;
    }

    private static class Summary {
        private int totalPeopleChecked;
        private int consistentPeople;
        private int inconsistentPeople;
        private int missingExpectedMinistry;
        private int inactiveExpectedMinistry;
        private int unsupportedLegacyPersonType;
        private int peopleWithAdditionalMinistries;
        private final List<PersonMinistryConsistencyEntry> details = new ArrayList<>();
        private final List<PersonMinistryConsistencyEntry> issues = new ArrayList<>();

        private void add(PersonMinistryConsistencyEntry entry) {
            totalPeopleChecked++;
            if (entry.hasIssue()) {
                inconsistentPeople++;
                issues.add(entry);
                countIssue(entry.issueType());
            } else {
                consistentPeople++;
            }

            if (!entry.additionalMinistries().isEmpty()) {
                peopleWithAdditionalMinistries++;
            }

            if (entry.hasIssue() || !entry.additionalMinistries().isEmpty()) {
                details.add(entry);
            }
        }

        private void countIssue(PersonMinistryConsistencyIssueType issueType) {
            if (issueType == PersonMinistryConsistencyIssueType.MISSING_EXPECTED_MINISTRY) {
                missingExpectedMinistry++;
            } else if (issueType == PersonMinistryConsistencyIssueType.EXPECTED_MINISTRY_INACTIVE) {
                inactiveExpectedMinistry++;
            } else if (issueType == PersonMinistryConsistencyIssueType.UNSUPPORTED_LEGACY_PERSON_TYPE) {
                unsupportedLegacyPersonType++;
            }
        }

        private PersonMinistryConsistencyReport toReport() {
            return new PersonMinistryConsistencyReport(
                    totalPeopleChecked,
                    consistentPeople,
                    inconsistentPeople,
                    missingExpectedMinistry,
                    inactiveExpectedMinistry,
                    unsupportedLegacyPersonType,
                    peopleWithAdditionalMinistries,
                    details,
                    issues
            );
        }
    }
}
