package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.response.EventAssignmentAuditResponseDTO;

import java.time.LocalDate;

public interface EventAssignmentOperationalAuditService {

    EventAssignmentAuditResponseDTO audit(
            LocalDate startDate,
            LocalDate endDate,
            Long eventId,
            int page,
            int size,
            boolean includeDetails
    );
}
