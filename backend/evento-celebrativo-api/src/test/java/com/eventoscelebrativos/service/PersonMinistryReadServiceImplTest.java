package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.PersonMinistryReadServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.unitMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class PersonMinistryReadServiceImplTest {

    private final PersonMinistryRepository personMinistryRepository = mock(PersonMinistryRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final LegacyMinistryTypeResolver legacyMinistryTypeResolver = mock(LegacyMinistryTypeResolver.class);
    private final PersonMinistryReadServiceImpl service =
            new PersonMinistryReadServiceImpl(
                    personMinistryRepository,
                    personRepository,
                    legacyMinistryTypeResolver
            );

    @Test
    void shouldNotQueryRepositoryWhenBatchIsEmpty() {
        Map<Long, Set<MinistryType>> result = service.findActiveMinistriesByPersonIds(List.of());

        assertTrue(result.isEmpty());
        verify(personMinistryRepository, never()).findActiveMinistriesByPersonIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldIgnoreDuplicatedPersonIdsAndReturnEmptySetForPeopleWithoutMinistries() {
        when(personMinistryRepository.findActiveMinistriesByPersonIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        Map<Long, Set<MinistryType>> result = service.findActiveMinistriesByPersonIds(List.of(2L, 1L, 2L));

        assertEquals(Set.of(), result.get(1L));
        assertEquals(Set.of(), result.get(2L));
        assertEquals(List.of(1L, 2L), result.keySet().stream().toList());

        verify(personMinistryRepository).findActiveMinistriesByPersonIds(argThat(ids ->
                ids.stream().toList().equals(List.of(1L, 2L))));
    }

    @Test
    void shouldFindAllActivePeopleByMinistryUsingSingleRepositoryQuery() {
        List<Person> people = List.of(person(1L), person(2L));
        Long readerMinistryId = unitMinistry(MinistryType.READER).getId();

        when(legacyMinistryTypeResolver.requireMinistry(MinistryType.READER))
                .thenReturn(unitMinistry(MinistryType.READER));
        when(personMinistryRepository.findActivePeopleByMinistryId(readerMinistryId)).thenReturn(people);

        assertEquals(people, service.findAllActivePeopleByMinistry(MinistryType.READER));
        verify(personMinistryRepository).findActivePeopleByMinistryId(readerMinistryId);
        verify(personRepository, never()).findAllByIdIn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFindActivePeopleByMinistryIdUsingCanonicalQueries() {
        Long readerMinistryId = unitMinistry(MinistryType.READER).getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Long> idPage = new PageImpl<>(List.of(2L, 1L), pageable, 2);
        Person first = person(1L);
        Person second = person(2L);

        when(personMinistryRepository.findActivePersonIdsByMinistryId(readerMinistryId, pageable)).thenReturn(idPage);
        when(personRepository.findAllByIdIn(List.of(2L, 1L))).thenReturn(List.of(first, second));

        Page<Person> result = service.findActivePeopleByMinistryId(readerMinistryId, pageable);

        assertEquals(List.of(second, first), result.getContent());
        assertEquals(2, result.getTotalElements());
        verifyNoInteractions(legacyMinistryTypeResolver);
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> service.findAllActivePeopleByMinistry(null));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistryId(null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistryId(0L, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> service.findAllActivePeopleByMinistryId(null));
        assertThrows(BusinessException.class, () -> service.findAllActivePeopleByMinistryId(0L));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(MinistryType.READER, null));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(MinistryType.READER, Pageable.unpaged()));
        assertThrows(BusinessException.class, () -> service.findActiveMinistriesByPersonIds(null));
        assertThrows(BusinessException.class, () -> service.findActiveCoordinatedMinistriesByPersonId(null));
    }

    @Test
    void shouldReturnEmptySetWhenPersonCoordinatesNothing() {
        when(personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(1L)).thenReturn(List.of());

        assertEquals(Set.of(), service.findActiveCoordinatedMinistriesByPersonId(1L));
    }

    @Test
    void shouldReturnCoordinatedMinistriesForPerson() {
        var reader = unitMinistry(MinistryType.READER);
        var commentator = unitMinistry(MinistryType.COMMENTATOR);
        when(personMinistryRepository.findActiveCoordinatedMinistriesByPersonId(1L))
                .thenReturn(List.of(reader, commentator));
        when(legacyMinistryTypeResolver.requireMinistryType(reader)).thenReturn(MinistryType.READER);
        when(legacyMinistryTypeResolver.requireMinistryType(commentator)).thenReturn(MinistryType.COMMENTATOR);

        assertEquals(
                Set.of(MinistryType.READER, MinistryType.COMMENTATOR),
                service.findActiveCoordinatedMinistriesByPersonId(1L)
        );
    }

    private Person person(Long id) {
        Person person = new Person("Person " + id, "34981" + String.format("%06d", id), LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private PersonMinistryRepository.PersonMinistryCatalogView view(Long personId, MinistryType ministryType) {
        return new PersonMinistryRepository.PersonMinistryCatalogView() {
            @Override
            public Long getPersonId() {
                return personId;
            }

            @Override
            public Long getMinistryId() {
                return unitMinistry(ministryType).getId();
            }

            @Override
            public String getMinistryNormalizedName() {
                return unitMinistry(ministryType).getNormalizedName();
            }
        };
    }
}
