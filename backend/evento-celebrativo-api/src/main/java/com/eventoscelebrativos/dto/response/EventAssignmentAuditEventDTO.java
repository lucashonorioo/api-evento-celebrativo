package com.eventoscelebrativos.dto.response;

import java.util.List;

public record EventAssignmentAuditEventDTO(
        Long eventId,
        boolean consistent,
        int legacyParticipantCount,
        int parallelAssignmentCount,
        int issueCount,
        List<EventAssignmentAuditIssueDTO> issues
) {

    public EventAssignmentAuditEventDTO {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
