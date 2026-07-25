package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonMinistryEligibilityResolverTest {

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PersonMinistryRepository personMinistryRepository = mock(PersonMinistryRepository.class);
    private final PersonMinistryEligibilityResolver resolver =
            new PersonMinistryEligibilityResolver(personRepository, personMinistryRepository);

    @Test
    void shouldResolveEligibilityForAllFiveMinistries() {
        Person priest = person(1L);
        Person reader = person(2L);
        Person commentator = person(3L);
        Person ministerOfTheWord = person(4L);
        Person eucharisticMinister = person(5L);

        Map<MinistryType, List<Long>> request = new EnumMap<>(MinistryType.class);
        request.put(MinistryType.PRIEST, List.of(1L));
        request.put(MinistryType.READER, List.of(2L));
        request.put(MinistryType.COMMENTATOR, List.of(3L));
        request.put(MinistryType.MINISTER_OF_THE_WORD, List.of(4L));
        request.put(MinistryType.EUCHARISTIC_MINISTER, List.of(5L));

        mockPeople(priest, reader, commentator, ministerOfTheWord, eucharisticMinister);
        mockMinistries(
                view(1L, MinistryType.PRIEST),
                view(2L, MinistryType.READER),
                view(3L, MinistryType.COMMENTATOR),
                view(4L, MinistryType.MINISTER_OF_THE_WORD),
                view(5L, MinistryType.EUCHARISTIC_MINISTER)
        );

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(5, result.size());
        assertTrue(result.stream().allMatch(ScaleParticipantEligibility::isEligible));
    }

    @Test
    void shouldResolveMultiplePeopleForTheSameMinistry() {
        Person first = person(1L);
        Person second = person(2L);
        Map<MinistryType, List<Long>> request = Map.of(MinistryType.READER, List.of(1L, 2L));

        mockPeople(first, second);
        mockMinistries(view(1L, MinistryType.READER), view(2L, MinistryType.READER));

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(ScaleParticipantEligibility::isEligible));
    }

    @Test
    void shouldResolvePersonWithSeveralActiveMinistries() {
        Person multiMinistryPerson = person(1L);
        Map<MinistryType, List<Long>> request = new EnumMap<>(MinistryType.class);
        request.put(MinistryType.READER, List.of(1L));
        request.put(MinistryType.COMMENTATOR, List.of(1L));

        mockPeople(multiMinistryPerson);
        mockMinistries(view(1L, MinistryType.READER), view(1L, MinistryType.COMMENTATOR));

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(ScaleParticipantEligibility::isEligible));
    }

    @Test
    void shouldMarkMinistryAsNotAssignedWhenPersonLacksIt() {
        Person reader = person(1L);
        Map<MinistryType, List<Long>> request = Map.of(MinistryType.PRIEST, List.of(1L));

        mockPeople(reader);
        mockMinistries(view(1L, MinistryType.READER));

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(1, result.size());
        ScaleParticipantEligibility eligibility = result.get(0);
        assertTrue(eligibility.personFound());
        assertFalse(eligibility.ministryAssigned());
        assertFalse(eligibility.isEligible());
    }

    @Test
    void shouldMarkPersonAsNotFoundWhenMissingFromPersonRepository() {
        Map<MinistryType, List<Long>> request = Map.of(MinistryType.PRIEST, List.of(99L));

        mockPeople();
        mockMinistries();

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(1, result.size());
        ScaleParticipantEligibility eligibility = result.get(0);
        assertFalse(eligibility.personFound());
        assertFalse(eligibility.ministryAssigned());
        assertFalse(eligibility.isEligible());
        assertEquals(99L, eligibility.personId());
    }

    @Test
    void shouldDeduplicateRepeatedIdsWithinTheSameMinistry() {
        Person reader = person(1L);
        Map<MinistryType, List<Long>> request = Map.of(MinistryType.READER, List.of(1L, 1L, 1L));

        mockPeople(reader);
        mockMinistries(view(1L, MinistryType.READER));

        List<ScaleParticipantEligibility> result = resolver.resolve(request);

        assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyListForEmptyRequest() {
        assertEquals(List.of(), resolver.resolve(Map.of()));
        assertEquals(List.of(), resolver.resolve(null));
        assertEquals(List.of(), resolver.resolve(Map.of(MinistryType.PRIEST, List.of())));

        verify(personRepository, never()).findAllByIdIn(anyCollection());
        verify(personMinistryRepository, never()).findActiveMinistryTypesByPersonIds(anyCollection());
    }

    @Test
    void shouldQueryPeopleAndMinistriesExactlyOnceRegardlessOfRequestSize() {
        Person first = person(1L);
        Person second = person(2L);
        Person third = person(3L);
        Map<MinistryType, List<Long>> request = new EnumMap<>(MinistryType.class);
        request.put(MinistryType.READER, List.of(1L, 2L));
        request.put(MinistryType.COMMENTATOR, List.of(3L));

        mockPeople(first, second, third);
        mockMinistries(view(1L, MinistryType.READER), view(2L, MinistryType.READER), view(3L, MinistryType.COMMENTATOR));

        resolver.resolve(request);

        verify(personRepository, times(1)).findAllByIdIn(anyCollection());
        verify(personMinistryRepository, times(1)).findActiveMinistryTypesByPersonIds(anyCollection());
    }

    @Test
    void shouldNotMutateReceivedCollections() {
        List<Long> readerIds = List.of(1L);
        Map<MinistryType, List<Long>> request = new LinkedHashMap<>();
        request.put(MinistryType.READER, readerIds);

        mockPeople(person(1L));
        mockMinistries(view(1L, MinistryType.READER));

        resolver.resolve(request);

        assertEquals(List.of(1L), readerIds);
        assertTrue(request.containsKey(MinistryType.READER));
    }

    private void mockPeople(Person... people) {
        when(personRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(people));
    }

    private void mockMinistries(PersonMinistryRepository.PersonMinistryTypeView... views) {
        when(personMinistryRepository.findActiveMinistryTypesByPersonIds(anyCollection())).thenReturn(List.of(views));
    }

    private Person person(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Person " + id);
        return reader;
    }

    private PersonMinistryRepository.PersonMinistryTypeView view(Long personId, MinistryType ministryType) {
        return new PersonMinistryRepository.PersonMinistryTypeView() {
            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public MinistryType getMinistryType() {
                return ministryType;
            }
        };
    }
}
