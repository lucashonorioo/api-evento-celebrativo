package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.ParticipationStatus;

import java.time.LocalDateTime;

public record ParticipationResponseSnapshot(
        Long eventId,
        Long personId,
        ParticipationStatus status,
        String declineReason,
        LocalDateTime respondedAt
) {
}
