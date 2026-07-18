package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonMinistryShadowReadExecutorTest {

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonMinistryShadowReadComparator personMinistryShadowReadComparator;

    @Test
    void shouldNotExecuteParallelReadWhenDisabled() {
        PersonMinistryShadowReadExecutor executor = executor();

        executor.execute(
                false,
                MinistryType.READER,
                List.of(person(1L)),
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadComparator);
    }

    @Test
    void shouldCompareEquivalentResultsWhenEnabled() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));
        Page<Person> parallelPage = page(List.of(person(1L)), PageRequest.of(0, 1), 1);
        PersonMinistryShadowReadReport report = report(
                List.of(1L),
                List.of(1L),
                List.of(),
                List.of(),
                List.of(PersonMinistryShadowReadIssueType.MATCH),
                true
        );

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);

        executor.execute(true, MinistryType.READER, legacyPeople, PersonMinistryShadowReadComparisonOptions.unorderedList());

        verify(personMinistryReadService).findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class));
        verify(personMinistryShadowReadComparator).compare(
                eq(MinistryType.READER),
                any(),
                eq(parallelPage),
                eq(PersonMinistryShadowReadComparisonOptions.unorderedList())
        );
    }

    @Test
    void shouldHandleMissingIdsWithoutPropagatingFailure() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L), person(2L));
        Page<Person> parallelPage = page(List.of(person(1L)), PageRequest.of(0, 2), 1);
        PersonMinistryShadowReadReport report = report(
                List.of(1L, 2L),
                List.of(1L),
                List.of(2L),
                List.of(),
                List.of(
                        PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH,
                        PersonMinistryShadowReadIssueType.CONTENT_MISMATCH
                ),
                false
        );

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);

        assertDoesNotThrow(() -> executor.execute(
                true,
                MinistryType.READER,
                legacyPeople,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        ));
    }

    @Test
    void shouldHandleAdditionalIdsWithoutPropagatingFailure() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));
        Page<Person> parallelPage = page(List.of(person(1L), person(2L)), PageRequest.of(0, 1), 2);
        PersonMinistryShadowReadReport report = report(
                List.of(1L),
                List.of(1L, 2L),
                List.of(),
                List.of(2L),
                List.of(
                        PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH,
                        PersonMinistryShadowReadIssueType.CONTENT_MISMATCH
                ),
                false
        );

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report);

        assertDoesNotThrow(() -> executor.execute(
                true,
                MinistryType.READER,
                legacyPeople,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        ));
    }

    @Test
    void shouldReloadFullParallelListWhenUnorderedComparisonHasMoreParallelRows() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));
        Page<Person> partialParallelPage = page(List.of(person(1L)), PageRequest.of(0, 1), 2);
        Page<Person> fullParallelPage = page(List.of(person(1L), person(2L)), PageRequest.of(0, 2), 2);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), pageableCaptor.capture()))
                .thenReturn(partialParallelPage, fullParallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(fullParallelPage), any()))
                .thenReturn(report(List.of(1L), List.of(1L, 2L), List.of(), List.of(2L),
                        List.of(
                                PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH,
                                PersonMinistryShadowReadIssueType.CONTENT_MISMATCH
                        ), false));

        executor.execute(true, MinistryType.READER, legacyPeople, PersonMinistryShadowReadComparisonOptions.unorderedList());

        verify(personMinistryReadService, times(2))
                .findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class));
        assertEquals(List.of(1, 2), pageableCaptor.getAllValues().stream().map(Pageable::getPageSize).toList());
        verify(personMinistryShadowReadComparator).compare(
                eq(MinistryType.READER),
                any(),
                eq(fullParallelPage),
                eq(PersonMinistryShadowReadComparisonOptions.unorderedList())
        );
    }

    @Test
    void shouldNotPropagateParallelReadFailure() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenThrow(new IllegalStateException("parallel read failed"));

        assertDoesNotThrow(() -> executor.execute(
                true,
                MinistryType.READER,
                legacyPeople,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        ));
        verify(personMinistryShadowReadComparator, never()).compare(any(), any(), any(), any());
    }

    @Test
    void shouldNotPropagateComparatorFailure() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));
        Page<Person> parallelPage = page(List.of(person(1L)), PageRequest.of(0, 1), 1);

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenThrow(new IllegalStateException("comparator failed"));

        assertDoesNotThrow(() -> executor.execute(
                true,
                MinistryType.READER,
                legacyPeople,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        ));
    }

    @Test
    void shouldUsePageSizeOneForEmptyLegacyList() {
        PersonMinistryShadowReadExecutor executor = executor();
        Page<Person> parallelPage = page(List.of(), PageRequest.of(0, 1), 0);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), pageableCaptor.capture()))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report(List.of(), List.of(), List.of(), List.of(),
                        List.of(PersonMinistryShadowReadIssueType.MATCH), true));

        executor.execute(true, MinistryType.READER, List.of(), PersonMinistryShadowReadComparisonOptions.unorderedList());

        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void shouldPassUnorderedComparisonOptionsToComparator() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = List.of(person(1L));
        Page<Person> parallelPage = page(List.of(person(1L)), PageRequest.of(0, 1), 1);
        ArgumentCaptor<PersonMinistryShadowReadComparisonOptions> optionsCaptor =
                ArgumentCaptor.forClass(PersonMinistryShadowReadComparisonOptions.class);

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), optionsCaptor.capture()))
                .thenReturn(report(List.of(1L), List.of(1L), List.of(), List.of(),
                        List.of(PersonMinistryShadowReadIssueType.MATCH), true));

        executor.execute(true, MinistryType.READER, legacyPeople, PersonMinistryShadowReadComparisonOptions.unorderedList());

        assertFalse(optionsCaptor.getValue().compareOrder());
        assertFalse(optionsCaptor.getValue().comparePageMetadata());
    }

    @Test
    void shouldPreserveOriginalLegacyList() {
        PersonMinistryShadowReadExecutor executor = executor();
        List<Reader> legacyPeople = new ArrayList<>(List.of(person(2L), person(1L)));
        Page<Person> parallelPage = page(List.of(person(1L), person(2L)), PageRequest.of(0, 2), 2);

        when(personMinistryReadService.findActivePeopleByMinistry(eq(MinistryType.READER), any(Pageable.class)))
                .thenReturn(parallelPage);
        when(personMinistryShadowReadComparator.compare(eq(MinistryType.READER), any(), eq(parallelPage), any()))
                .thenReturn(report(List.of(2L, 1L), List.of(1L, 2L), List.of(), List.of(),
                        List.of(PersonMinistryShadowReadIssueType.MATCH), true));

        executor.execute(true, MinistryType.READER, legacyPeople, PersonMinistryShadowReadComparisonOptions.unorderedList());

        assertEquals(List.of(2L, 1L), legacyPeople.stream().map(Reader::getId).toList());
    }

    private PersonMinistryShadowReadExecutor executor() {
        return new PersonMinistryShadowReadExecutor(personMinistryReadService, personMinistryShadowReadComparator);
    }

    private Page<Person> page(List<Person> people, PageRequest pageRequest, long totalElements) {
        return new PageImpl<>(people, pageRequest, totalElements);
    }

    private Reader person(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader " + id);
        return reader;
    }

    private PersonMinistryShadowReadReport report(
            List<Long> legacyIds,
            List<Long> parallelIds,
            List<Long> missingInParallelIds,
            List<Long> additionalInParallelIds,
            List<PersonMinistryShadowReadIssueType> issues,
            boolean matched
    ) {
        return new PersonMinistryShadowReadReport(
                MinistryType.READER,
                0,
                Math.max(legacyIds.size(), 1),
                legacyIds,
                parallelIds,
                missingInParallelIds,
                additionalInParallelIds,
                legacyIds.size(),
                parallelIds.size(),
                legacyIds.isEmpty() ? 0 : 1,
                parallelIds.isEmpty() ? 0 : 1,
                false,
                false,
                false,
                issues,
                matched
        );
    }
}
