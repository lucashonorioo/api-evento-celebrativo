package com.eventoscelebrativos.service;

import java.util.List;

public record PersonMinistryConsistencyReport(
        int totalPeopleChecked,
        int consistentPeople,
        int inconsistentPeople,
        int missingExpectedMinistry,
        int inactiveExpectedMinistry,
        int unsupportedLegacyPersonType,
        int peopleWithAdditionalMinistries,
        List<PersonMinistryConsistencyEntry> details,
        List<PersonMinistryConsistencyEntry> issues
) {

    public PersonMinistryConsistencyReport {
        details = List.copyOf(details);
        issues = List.copyOf(issues);
    }
}
