package com.eventoscelebrativos.dto.response;

public record EventAssignmentAuditSummaryDTO(
        int eventsChecked,
        int consistentEvents,
        int inconsistentEvents,
        int legacyParticipants,
        int parallelAssignments,
        int totalIssues,
        int missingParallelAssignments,
        int extraParallelAssignments,
        int assignmentTypeMismatches,
        int duplicateParallelAssignments,
        int multiplePriests,
        int unknownLegacyPersonTypes
) {
}
