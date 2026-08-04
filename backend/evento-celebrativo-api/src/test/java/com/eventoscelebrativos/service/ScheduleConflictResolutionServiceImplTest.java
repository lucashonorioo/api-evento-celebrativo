package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com contexto Spring real (H2) e Clock fixo (secao 13: nunca testar passagem real de
 * tempo), o comportamento de {@link ScheduleConflictResolutionService}: resolve conflitos cujo
 * evento ja encerrou (resolvedAt = event.endAt), preserva os ainda ativos, resolve com o instante
 * atual quando o evento referenciado nao existe mais (sem conteudo/PII no warning) e e idempotente.
 */
@SpringBootTest
@Import(ScheduleConflictResolutionServiceImplTest.FixedClockConfig.class)
class ScheduleConflictResolutionServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 12, 0, 0);

    @Autowired
    private ScheduleConflictResolutionService scheduleConflictResolutionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Test
    @Transactional
    void shouldResolveActiveConflictWhenEventAlreadyEnded() {
        LocalDateTime eventEndAt = CURRENT_SECOND.minusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Ended Resolution Event " + UUID.randomUUID(), eventEndAt.minusHours(1), eventEndAt, true));
        Notification notification = notificationRepository.saveAndFlush(activeConflict(event.getId(), 1L));

        scheduleConflictResolutionService.resolveOne(notification.getId(), event.getId(), CURRENT_SECOND);

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(eventEndAt, reloaded.getResolvedAt(), "resolvedAt deve ser event.endAt, nao currentSecond");
        assertNull(reloaded.getActiveSourceKey(), "activeSourceKey deve ser liberada ao resolver");
    }

    @Test
    @Transactional
    void shouldNotResolveWhenEventStillInFutureOrInProgress() {
        LocalDateTime eventEndAt = CURRENT_SECOND.plusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Active Resolution Event " + UUID.randomUUID(), CURRENT_SECOND.minusMinutes(30), eventEndAt, true));
        Notification notification = notificationRepository.saveAndFlush(activeConflict(event.getId(), 2L));

        scheduleConflictResolutionService.resolveOne(notification.getId(), event.getId(), CURRENT_SECOND);

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertNull(reloaded.getResolvedAt(), "Evento ainda nao encerrado nao deve ser resolvido");
        assertNotNull(reloaded.getActiveSourceKey());
    }

    @Test
    @Transactional
    void shouldResolveWithCurrentSecondWhenReferencedEventNoLongerExists() {
        Notification notification = notificationRepository.saveAndFlush(activeConflict(999_999_999L, 3L));

        scheduleConflictResolutionService.resolveOne(notification.getId(), 999_999_999L, CURRENT_SECOND);

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(CURRENT_SECOND, reloaded.getResolvedAt());
        assertNull(reloaded.getActiveSourceKey());
    }

    @Test
    @Transactional
    void shouldBeIdempotentWhenResolvingAlreadyResolvedNotification() {
        LocalDateTime eventEndAt = CURRENT_SECOND.minusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Idempotent Resolution Event " + UUID.randomUUID(), eventEndAt.minusHours(1), eventEndAt, true));
        Notification notification = notificationRepository.saveAndFlush(activeConflict(event.getId(), 4L));

        scheduleConflictResolutionService.resolveOne(notification.getId(), event.getId(), CURRENT_SECOND);
        scheduleConflictResolutionService.resolveOne(notification.getId(), event.getId(), CURRENT_SECOND.plusDays(1));

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(eventEndAt, reloaded.getResolvedAt(), "Segunda chamada nao deve alterar o resolvedAt ja definido");
    }

    @Test
    @Transactional
    void shouldFindOnlyActiveScheduleConflictsInBatch() {
        LocalDateTime eventEndAt = CURRENT_SECOND.minusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Batch Event " + UUID.randomUUID(), eventEndAt.minusHours(1), eventEndAt, true));
        Notification active = notificationRepository.saveAndFlush(activeConflict(event.getId(), 5L));
        Notification resolved = notificationRepository.saveAndFlush(activeConflict(event.getId(), 6L));
        resolved.resolve(CURRENT_SECOND);
        notificationRepository.saveAndFlush(resolved);

        List<ScheduleConflictResolutionService.ScheduleConflictReference> batch =
                scheduleConflictResolutionService.findActiveConflictsBatch(100);

        assertTrue(batch.stream().anyMatch(reference -> reference.notificationId().equals(active.getId())));
        assertTrue(batch.stream().noneMatch(reference -> reference.notificationId().equals(resolved.getId())));
    }

    private Notification activeConflict(Long eventId, Long personId) {
        String sourceKey = eventId + ":" + personId;
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + sourceKey;
        return Notification.scheduleConflict(
                NotificationAudience.ADMIN,
                "Conflito de escala detectado",
                "Mensagem de teste",
                "CELEBRATION_EVENT",
                eventId,
                "SCHEDULE_UNAVAILABILITY_CONFLICT",
                sourceKey,
                activeSourceKey,
                CURRENT_SECOND.minusDays(1)
        );
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CURRENT_SECOND.atZone(ZONE).toInstant(), ZONE);
        }
    }
}
