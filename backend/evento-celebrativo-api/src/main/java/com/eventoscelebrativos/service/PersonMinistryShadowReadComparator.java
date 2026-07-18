package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PersonMinistryShadowReadComparator {

    public PersonMinistryShadowReadReport compare(
            MinistryType ministryType,
            Page<? extends Person> legacyPage,
            Page<? extends Person> parallelPage
    ) {
        return compare(
                ministryType,
                legacyPage,
                parallelPage,
                PersonMinistryShadowReadComparisonOptions.deterministicPage()
        );
    }

    public PersonMinistryShadowReadReport compare(
            MinistryType ministryType,
            Page<? extends Person> legacyPage,
            Page<? extends Person> parallelPage,
            PersonMinistryShadowReadComparisonOptions options
    ) {
        Objects.requireNonNull(ministryType, "ministryType must not be null");
        Objects.requireNonNull(legacyPage, "legacyPage must not be null");
        Objects.requireNonNull(parallelPage, "parallelPage must not be null");
        Objects.requireNonNull(options, "options must not be null");

        List<Long> legacyIds = idsFrom(legacyPage);
        List<Long> parallelIds = idsFrom(parallelPage);
        List<Long> missingInParallelIds = missingIds(legacyIds, parallelIds);
        List<Long> additionalInParallelIds = missingIds(parallelIds, legacyIds);
        boolean contentMismatch = !missingInParallelIds.isEmpty() || !additionalInParallelIds.isEmpty()
                || legacyIds.size() != parallelIds.size();
        boolean orderDiffers = !contentMismatch && !legacyIds.equals(parallelIds);
        List<PersonMinistryShadowReadIssueType> issues = new ArrayList<>();

        if (legacyPage.getTotalElements() != parallelPage.getTotalElements()) {
            issues.add(PersonMinistryShadowReadIssueType.TOTAL_ELEMENTS_MISMATCH);
        }

        if (options.comparePageMetadata() && (legacyPage.getNumber() != parallelPage.getNumber()
                || legacyPage.getSize() != parallelPage.getSize()
                || legacyPage.getTotalPages() != parallelPage.getTotalPages()
                || legacyPage.getNumberOfElements() != parallelPage.getNumberOfElements())) {
            issues.add(PersonMinistryShadowReadIssueType.PAGE_METADATA_MISMATCH);
        }

        if (contentMismatch) {
            issues.add(PersonMinistryShadowReadIssueType.CONTENT_MISMATCH);
        } else if (options.compareOrder() && orderDiffers) {
            issues.add(PersonMinistryShadowReadIssueType.ORDER_MISMATCH);
        }

        if (issues.isEmpty()) {
            issues.add(PersonMinistryShadowReadIssueType.MATCH);
        }

        return report(
                ministryType,
                legacyPage.getNumber(),
                legacyPage.getSize(),
                legacyIds,
                parallelIds,
                missingInParallelIds,
                additionalInParallelIds,
                legacyPage.getTotalElements(),
                parallelPage.getTotalElements(),
                legacyPage.getTotalPages(),
                parallelPage.getTotalPages(),
                options.compareOrder(),
                orderDiffers,
                options.comparePageMetadata(),
                issues
        );
    }

    public PersonMinistryShadowReadReport parallelReadFailure(
            MinistryType ministryType,
            Page<? extends Person> legacyPage
    ) {
        Objects.requireNonNull(ministryType, "ministryType must not be null");
        Objects.requireNonNull(legacyPage, "legacyPage must not be null");

        return report(
                ministryType,
                legacyPage.getNumber(),
                legacyPage.getSize(),
                idsFrom(legacyPage),
                List.of(),
                List.of(),
                List.of(),
                legacyPage.getTotalElements(),
                -1,
                legacyPage.getTotalPages(),
                -1,
                false,
                false,
                false,
                List.of(PersonMinistryShadowReadIssueType.PARALLEL_READ_FAILURE)
        );
    }

    private PersonMinistryShadowReadReport report(
            MinistryType ministryType,
            int pageNumber,
            int pageSize,
            List<Long> legacyIds,
            List<Long> parallelIds,
            List<Long> missingInParallelIds,
            List<Long> additionalInParallelIds,
            long legacyTotalElements,
            long parallelTotalElements,
            int legacyTotalPages,
            int parallelTotalPages,
            boolean orderCompared,
            boolean orderDiffers,
            boolean pageMetadataCompared,
            List<PersonMinistryShadowReadIssueType> issues
    ) {
        return new PersonMinistryShadowReadReport(
                ministryType,
                pageNumber,
                pageSize,
                legacyIds,
                parallelIds,
                missingInParallelIds,
                additionalInParallelIds,
                legacyTotalElements,
                parallelTotalElements,
                legacyTotalPages,
                parallelTotalPages,
                orderCompared,
                orderDiffers,
                pageMetadataCompared,
                issues,
                issues.size() == 1 && issues.contains(PersonMinistryShadowReadIssueType.MATCH)
        );
    }

    private List<Long> idsFrom(Page<? extends Person> page) {
        return page.getContent().stream()
                .map(Person::getId)
                .toList();
    }

    private List<Long> missingIds(List<Long> sourceIds, List<Long> targetIds) {
        Set<Long> target = new LinkedHashSet<>(targetIds);
        return sourceIds.stream()
                .filter(id -> !target.contains(id))
                .distinct()
                .toList();
    }
}
