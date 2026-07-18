package com.eventoscelebrativos.service;

import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.impl.PersonMinistryConsistencyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonMinistryConsistencyServiceImplTest {

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PersonMinistryRepository personMinistryRepository = mock(PersonMinistryRepository.class);
    private final MinistryTypeResolver ministryTypeResolver = mock(MinistryTypeResolver.class);
    private final PersonMinistryConsistencyServiceImpl service = new PersonMinistryConsistencyServiceImpl(
            personRepository,
            personMinistryRepository,
            ministryTypeResolver
    );

    @Test
    void shouldRejectInvalidBatchSize() {
        assertThrows(BusinessException.class, () -> service.audit(0));
        assertThrows(BusinessException.class, () -> service.audit(-1));
    }

    @Test
    void shouldReportUnsupportedLegacyPersonTypeWhenResolverCannotMapPerson() {
        Person person = mock(Person.class);
        when(person.getId()).thenReturn(1L);
        when(person.getName()).thenReturn("Unsupported Person");
        when(person.getPersonType()).thenReturn("unsupported");

        PageRequest pageable = PageRequest.of(0, 10);
        when(personRepository.findPersonIdsForMinistryAudit(pageable))
                .thenReturn(new PageImpl<>(List.of(1L), pageable, 1));
        when(personRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(person));
        when(personMinistryRepository.findAllMinistryStatusesByPersonIds(List.of(1L)))
                .thenReturn(Collections.emptyList());
        when(ministryTypeResolver.resolve(person)).thenThrow(new BusinessException("Tipo nao suportado"));

        PersonMinistryConsistencyReport report = service.audit(10);

        assertEquals(1, report.totalPeopleChecked());
        assertEquals(0, report.consistentPeople());
        assertEquals(1, report.inconsistentPeople());
        assertEquals(1, report.unsupportedLegacyPersonType());
        assertEquals(PersonMinistryConsistencyIssueType.UNSUPPORTED_LEGACY_PERSON_TYPE,
                report.issues().get(0).issueType());
    }
}
