package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.service.EventAssignmentConsistencyIssueType;

public record EventAssignmentAuditIssueDTO(
        EventAssignmentConsistencyIssueType issueType,
        Long eventId,
        Long personId,
        EventAssignmentType legacyType,
        EventAssignmentType parallelType
) {
}
