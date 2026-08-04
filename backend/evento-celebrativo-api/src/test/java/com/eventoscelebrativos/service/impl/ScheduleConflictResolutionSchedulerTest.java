package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.service.ScheduleConflictResolutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prova o comportamento de {@link ScheduleConflictResolutionScheduler} em si (nao apenas o service
 * subjacente, ja coberto por ScheduleConflictResolutionServiceImplTest): respeita o limite de lote
 * por execucao, progride por lotes sucessivos ate esgotar os conflitos ativos encerrados, nao faz
 * nada quando desabilitado, e produz um resultado final consistente quando duas "instancias" (ex.:
 * dois nos da aplicacao) chamam run() ao mesmo tempo sobre o mesmo lote - cada notificacao e
 * resolvida exatamente uma vez, sem excecao, gracas ao lock por PK e a idempotencia de
 * Notification#resolve. Clock sempre fixo (secao 13: nunca passagem real de tempo).
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Import(ScheduleConflictResolutionSchedulerTest.FixedClockConfig.class)
class ScheduleConflictResolutionSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 12, 0, 0);

    @Autowired
    private ScheduleConflictResolutionService scheduleConflictResolutionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdEventIds = new ArrayList<>();

    @AfterEach
    void cleanupCommittedFixtures() {
        if (createdEventIds.isEmpty()) {
            return;
        }
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> {
            for (Long eventId : createdEventIds) {
                jdbcTemplate.update("DELETE FROM tb_notification WHERE reference_id = ?", eventId);
                jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
            }
        });
        createdEventIds.clear();
    }

    @Test
    @Transactional
    void shouldResolveAtMostBatchSizePerRun() {
        List<Long> notificationIds = createEndedConflicts(5);
        ScheduleConflictResolutionScheduler scheduler =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, true, 2);

        scheduler.run();

        assertEquals(2, countResolved(notificationIds), "Um unico run() nao deve resolver mais que batchSize itens");
    }

    @Test
    @Transactional
    void shouldProgressThroughMultipleBatchesAcrossSuccessiveRuns() {
        List<Long> notificationIds = createEndedConflicts(5);
        ScheduleConflictResolutionScheduler scheduler =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, true, 2);

        scheduler.run();
        assertEquals(2, countResolved(notificationIds));

        scheduler.run();
        assertEquals(4, countResolved(notificationIds));

        scheduler.run();
        assertEquals(5, countResolved(notificationIds), "Terceiro lote deve fechar os 5 (2+2+1)");

        scheduler.run();
        assertEquals(5, countResolved(notificationIds), "Sem mais conflitos ativos, execucoes extras nao fazem nada");
    }

    @Test
    @Transactional
    void shouldDoNothingWhenDisabled() {
        List<Long> notificationIds = createEndedConflicts(2);
        ScheduleConflictResolutionScheduler scheduler =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, false, 100);

        scheduler.run();

        assertEquals(0, countResolved(notificationIds), "enabled=false nunca deve processar nada");
    }

    @Test
    void shouldResolveEachConflictExactlyOnceWhenTwoSchedulerInstancesRunConcurrentlyOverTheSameBatch() throws Exception {
        List<Long> notificationIds = createEndedConflictsCommitted(6);
        ScheduleConflictResolutionScheduler schedulerA =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, true, 100);
        ScheduleConflictResolutionScheduler schedulerB =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, true, 100);

        CountDownLatch bothReady = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> {
                bothReady.countDown();
                awaitLatch(bothReady);
                schedulerA.run();
            });
            Future<?> b = executor.submit(() -> {
                bothReady.countDown();
                awaitLatch(bothReady);
                schedulerB.run();
            });
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(6, countResolved(notificationIds),
                "Ambas as instancias juntas devem resolver todos os conflitos ativos, sem excecao e sem perda");
    }

    private long countResolved(List<Long> notificationIds) {
        return notificationIds.stream()
                .filter(id -> notificationRepository.findById(id).orElseThrow().getResolvedAt() != null)
                .count();
    }

    private List<Long> createEndedConflicts(int count) {
        List<Long> ids = new ArrayList<>();
        LocalDateTime eventEndAt = CURRENT_SECOND.minusHours(1);
        for (int i = 0; i < count; i++) {
            CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                    new CelebrationEvent(null, "Scheduler Batch Event " + UUID.randomUUID(), eventEndAt.minusHours(1), eventEndAt, true));
            Notification notification = notificationRepository.saveAndFlush(activeConflict(event.getId(), (long) i));
            ids.add(notification.getId());
        }
        return ids;
    }

    private List<Long> createEndedConflictsCommitted(int count) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return requiresNew.execute(status -> {
            List<Long> ids = new ArrayList<>();
            LocalDateTime eventEndAt = CURRENT_SECOND.minusHours(1);
            for (int i = 0; i < count; i++) {
                CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                        new CelebrationEvent(null, "Scheduler Concurrent Event " + UUID.randomUUID(), eventEndAt.minusHours(1), eventEndAt, true));
                createdEventIds.add(event.getId());
                Notification notification = notificationRepository.saveAndFlush(activeConflict(event.getId(), (long) i));
                ids.add(notification.getId());
            }
            return ids;
        });
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

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch nao foi liberada dentro do tempo esperado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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
