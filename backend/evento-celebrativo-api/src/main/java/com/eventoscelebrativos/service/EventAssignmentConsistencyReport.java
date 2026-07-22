package com.eventoscelebrativos.service;

import java.util.List;

public record EventAssignmentConsistencyReport(
        Long eventId,
        int legacyAssignmentCount,
        int parallelAssignmentCount,
        List<EventAssignmentConsistencyIssue> issues
) {

    public EventAssignmentConsistencyReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean consistent() {
        return issues.isEmpty();
    }
}
