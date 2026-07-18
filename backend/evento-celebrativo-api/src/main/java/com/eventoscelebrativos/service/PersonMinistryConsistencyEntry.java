package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record PersonMinistryConsistencyEntry(
        Long personId,
        String personName,
        String legacyPersonType,
        MinistryType expectedMinistry,
        Set<MinistryType> activeMinistries,
        Set<MinistryType> additionalMinistries,
        PersonMinistryConsistencyIssueType issueType
) {

    public PersonMinistryConsistencyEntry {
        activeMinistries = immutableEnumSet(activeMinistries);
        additionalMinistries = immutableEnumSet(additionalMinistries);
    }

    public boolean hasIssue() {
        return issueType != null;
    }

    private static Set<MinistryType> immutableEnumSet(Set<MinistryType> ministries) {
        if (ministries == null || ministries.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(ministries));
    }
}
