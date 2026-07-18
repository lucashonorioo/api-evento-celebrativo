package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;

import java.util.List;

public record PersonMinistryShadowReadReport(
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
        List<PersonMinistryShadowReadIssueType> issues,
        boolean matched
) {

    public PersonMinistryShadowReadReport {
        legacyIds = List.copyOf(legacyIds);
        parallelIds = List.copyOf(parallelIds);
        missingInParallelIds = List.copyOf(missingInParallelIds);
        additionalInParallelIds = List.copyOf(additionalInParallelIds);
        issues = List.copyOf(issues);
    }

    public boolean hasIssue(PersonMinistryShadowReadIssueType issueType) {
        return issues.contains(issueType);
    }
}
