package com.eventoscelebrativos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventAssignmentAuditResponseDTO(
        EventAssignmentAuditSummaryDTO summary,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int numberOfElements,
        boolean empty,
        List<EventAssignmentAuditEventDTO> events
) {

    public EventAssignmentAuditResponseDTO {
        events = events == null ? null : List.copyOf(events);
    }
}
