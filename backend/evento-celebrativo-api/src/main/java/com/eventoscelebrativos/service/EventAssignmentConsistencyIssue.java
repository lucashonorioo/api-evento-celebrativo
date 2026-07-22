package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EventAssignmentType;

public record EventAssignmentConsistencyIssue(
        EventAssignmentConsistencyIssueType issueType,
        Long eventId,
        Long assignmentId,
        Long personId,
        EventAssignmentType legacyType,
        EventAssignmentType parallelType,
        String legacyPersonType
) {
}
