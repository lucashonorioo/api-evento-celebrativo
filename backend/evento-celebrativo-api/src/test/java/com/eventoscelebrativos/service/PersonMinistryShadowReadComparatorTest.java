package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonMinistryShadowReadComparatorTest {

    private final PersonMinistryShadowReadComparator comparator = new PersonMinistryShadowReadComparator();

    @Test
    void shouldReportMatchWhenIdsAndMetadataAreEquivalent() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(1L), person(2L)), pageable, 2)
        );

        assertTrue(report.matched());
        assertEquals(List.of(PersonMinistryShadowReadIssueType.MATCH), report.issues());
    }

    @Test
    void shouldReportContentMismatchWhenIdsAreDifferent() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(1L), person(3L)), pageable, 2)
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertFalse(report.hasIssue(PersonMinistryShadowReadIssueType.ORDER_MISMATCH));
        assertEquals(List.of(2L), report.missingInParallelIds());
        assertEquals(List.of(3L), report.additionalInParallelIds());
    }

    @Test
    void shouldReportMissingIdInParallelAsContentMismatch() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(1L)), pageable, 1),
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertEquals(List.of(2L), report.missingInParallelIds());
        assertEquals(List.of(), report.additionalInParallelIds());
    }

    @Test
    void shouldReportAdditionalIdInParallelAsContentMismatch() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L)), pageable, 1),
                page(List.of(person(1L), person(2L)), pageable, 2),
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertEquals(List.of(), report.missingInParallelIds());
        assertEquals(List.of(2L), report.additionalInParallelIds());
    }

    @Test
    void shouldReportContentMismatchWhenQuantitiesAreEqualButIdsDiffer() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(1L), person(3L)), pageable, 2),
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertFalse(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
        assertEquals(List.of(2L), report.missingInParallelIds());
        assertEquals(List.of(3L), report.additionalInParallelIds());
    }

    @Test
    void shouldNotReportOrderMismatchWhenOrderIsNotCompared() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(2L), person(1L)), pageable, 2),
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        assertTrue(report.matched());
        assertEquals(List.of(PersonMinistryShadowReadIssueType.MATCH), report.issues());
        assertFalse(report.orderCompared());
        assertTrue(report.orderDiffers());
        assertFalse(report.pageMetadataCompared());
    }

    @Test
    void shouldReportOrderMismatchWhenDeterministicOrderIsCompared() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(2L), person(1L)), pageable, 2)
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.ORDER_MISMATCH));
        assertFalse(report.hasIssue(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH));
        assertTrue(report.orderCompared());
        assertTrue(report.orderDiffers());
    }

    @Test
    void shouldReportTotalElementsMismatch() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2),
                page(List.of(person(1L), person(2L)), pageable, 3)
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH));
    }

    @Test
    void shouldReportPageMetadataMismatch() {
        PersonMinistryShadowReadReport report = comparator.compare(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), PageRequest.of(0, 2), 2),
                page(List.of(person(1L), person(2L)), PageRequest.of(1, 2), 2)
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.PAGE_METADATA_MISMATCH));
    }

    @Test
    void shouldReportParallelReadFailureWithoutParallelIds() {
        PageRequest pageable = PageRequest.of(0, 2);

        PersonMinistryShadowReadReport report = comparator.parallelReadFailure(
                MinistryType.READER,
                page(List.of(person(1L), person(2L)), pageable, 2)
        );

        assertFalse(report.matched());
        assertTrue(report.hasIssue(PersonMinistryShadowReadIssueType.PARALLEL_READ_FAILURE));
        assertEquals(List.of(1L, 2L), report.legacyIds());
        assertEquals(List.of(), report.parallelIds());
        assertEquals(List.of(), report.missingInParallelIds());
        assertEquals(List.of(), report.additionalInParallelIds());
        assertEquals(-1, report.parallelTotalElements());
        assertFalse(report.orderCompared());
        assertFalse(report.pageMetadataCompared());
    }

    @Test
    void shouldNotExposeSensitiveDataInReportType() {
        List<String> componentNames = List.of(PersonMinistryShadowReadReport.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();

        assertFalse(componentNames.contains("password"));
        assertFalse(componentNames.contains("phoneNumber"));
        assertFalse(componentNames.contains("token"));
        assertFalse(componentNames.contains("roles"));
    }

    private Page<Person> page(List<Person> people, PageRequest pageable, long totalElements) {
        return new PageImpl<>(people, pageable, totalElements);
    }

    private Person person(Long id) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader " + id);
        return reader;
    }
}
