package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.service.ScheduleConflictResolutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Dispara periodicamente {@link ScheduleConflictResolutionService}, cobrindo a resolucao de
 * conflitos de escala pela passagem natural do tempo (secao 13). Cada chamada a {@code resolveOne}
 * atravessa o proxy Spring deste bean para outro (o service), garantindo que cada item seja
 * resolvido em sua propria transacao curta, mesmo com varias instancias rodando em paralelo.
 */
@Component
public class ScheduleConflictResolutionScheduler {

    private final ScheduleConflictResolutionService scheduleConflictResolutionService;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;

    public ScheduleConflictResolutionScheduler(
            ScheduleConflictResolutionService scheduleConflictResolutionService,
            Clock clock,
            @Value("${app.notifications.schedule-conflict-resolution.enabled:true}") boolean enabled,
            @Value("${app.notifications.schedule-conflict-resolution.batch-size:100}") int batchSize
    ) {
        this.scheduleConflictResolutionService = scheduleConflictResolutionService;
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.notifications.schedule-conflict-resolution.fixed-delay:PT15M}")
    public void run() {
        if (!enabled) {
            return;
        }
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        for (var reference : scheduleConflictResolutionService.findActiveConflictsBatch(batchSize, currentSecond)) {
            scheduleConflictResolutionService.resolveOne(reference.notificationId(), reference.eventId(), currentSecond);
        }
    }
}
