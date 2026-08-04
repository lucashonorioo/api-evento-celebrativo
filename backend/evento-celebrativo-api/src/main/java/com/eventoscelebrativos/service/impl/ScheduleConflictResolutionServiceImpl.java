package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.service.ScheduleConflictResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleConflictResolutionServiceImpl implements ScheduleConflictResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleConflictResolutionServiceImpl.class);
    private static final String REFERENCE_TYPE = "CELEBRATION_EVENT";

    private final NotificationRepository notificationRepository;
    private final CelebrationEventRepository celebrationEventRepository;

    public ScheduleConflictResolutionServiceImpl(
            NotificationRepository notificationRepository,
            CelebrationEventRepository celebrationEventRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.celebrationEventRepository = celebrationEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleConflictReference> findActiveConflictsBatch(int batchSize, LocalDateTime currentSecond) {
        return notificationRepository.findResolvableScheduleConflictsBatch(
                        REFERENCE_TYPE, currentSecond, PageRequest.of(0, batchSize)).stream()
                .map(notification -> new ScheduleConflictReference(notification.getId(), notification.getReferenceId()))
                .toList();
    }

    @Override
    @Transactional
    public void resolveOne(Long notificationId, Long eventId, LocalDateTime currentSecond) {
        Optional<CelebrationEvent> event = celebrationEventRepository.findByIdForUpdate(eventId);

        if (event.isEmpty()) {
            notificationRepository.findByIdForUpdate(notificationId).ifPresent(notification -> {
                notification.resolve(currentSecond);
                log.warn("Scheduler de resolucao de conflito de escala encontrou referencia a evento "
                        + "inexistente (eventId={}); notificacao resolvida com o instante atual.", eventId);
            });
            return;
        }

        LocalDateTime eventEndAt = event.get().getEndAt();
        if (!eventEndAt.isAfter(currentSecond)) {
            notificationRepository.findByIdForUpdate(notificationId)
                    .ifPresent(notification -> notification.resolve(eventEndAt));
        }
    }
}
