package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.PersonMinistryReadServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class PersonMinistryReadServiceImplTest {

    private final PersonMinistryRepository personMinistryRepository = mock(PersonMinistryRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PersonMinistryReadServiceImpl service =
            new PersonMinistryReadServiceImpl(personMinistryRepository, personRepository);

    @Test
    void shouldNotQueryRepositoryWhenBatchIsEmpty() {
        Map<Long, Set<MinistryType>> result = service.findActiveMinistriesByPersonIds(List.of());

        assertTrue(result.isEmpty());
        verify(personMinistryRepository, never()).findActiveMinistryTypesByPersonIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldIgnoreDuplicatedPersonIdsAndReturnEmptySetForPeopleWithoutMinistries() {
        when(personMinistryRepository.findActiveMinistryTypesByPersonIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        Map<Long, Set<MinistryType>> result = service.findActiveMinistriesByPersonIds(List.of(2L, 1L, 2L));

        assertEquals(Set.of(), result.get(1L));
        assertEquals(Set.of(), result.get(2L));
        assertEquals(List.of(1L, 2L), result.keySet().stream().toList());

        verify(personMinistryRepository).findActiveMinistryTypesByPersonIds(argThat(ids ->
                ids.stream().toList().equals(List.of(1L, 2L))));
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(null, PageRequest.of(0, 10)));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(MinistryType.READER, null));
        assertThrows(BusinessException.class, () -> service.findActivePeopleByMinistry(MinistryType.READER, Pageable.unpaged()));
        assertThrows(BusinessException.class, () -> service.findActiveMinistriesByPersonIds(null));
    }
}
