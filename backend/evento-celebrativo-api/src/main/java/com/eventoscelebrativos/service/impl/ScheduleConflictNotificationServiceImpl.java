package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.NotificationDeliveryService;
import com.eventoscelebrativos.service.ScheduleConflictNotificationService;
import com.eventoscelebrativos.service.SystemNotificationCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ver {@link ScheduleConflictNotificationService}. A mensagem gerada e um snapshot (secao 10): uma
 * vez enviada para uma ocorrencia ativa, ela nunca e reescrita enquanto o conflito continuar - so
 * uma nova ocorrencia (apos resolucao da anterior) gera mensagem nova.
 */
@Service
public class ScheduleConflictNotificationServiceImpl implements ScheduleConflictNotificationService {

    private static final String REFERENCE_TYPE = "CELEBRATION_EVENT";
    private static final String SOURCE_TYPE = "SCHEDULE_UNAVAILABILITY_CONFLICT";
    private static final String TITLE = "Conflito de escala detectado";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final NotificationRepository notificationRepository;
    private final CelebrationEventRepository celebrationEventRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final PersonUnavailabilityRepository personUnavailabilityRepository;
    private final PersonRepository personRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final AdminRoleMutexService adminRoleMutexService;

    public ScheduleConflictNotificationServiceImpl(
            NotificationRepository notificationRepository,
            CelebrationEventRepository celebrationEventRepository,
            EventAssignmentRepository eventAssignmentRepository,
            PersonUnavailabilityRepository personUnavailabilityRepository,
            PersonRepository personRepository,
            NotificationDeliveryService notificationDeliveryService,
            AdminRoleMutexService adminRoleMutexService
    ) {
        this.notificationRepository = notificationRepository;
        this.celebrationEventRepository = celebrationEventRepository;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.personUnavailabilityRepository = personUnavailabilityRepository;
        this.personRepository = personRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.adminRoleMutexService = adminRoleMutexService;
    }

    @Override
    @Transactional
    public void reconcile(Long eventId, Long personId, LocalDateTime currentSecond) {
        // Precondicao real (nao apenas documentada): sem essa garantia de ordem, o gap lock desta
        // leitura FOR UPDATE (linha abaixo) sobre active_source_key ainda inexistente pode formar
        // ciclo de deadlock com o lock de ROLE_ADMIN adquirido depois dentro de deliverBroadcast,
        // como comprovado sob MySQL real ao chamar reconcile sem o mutex previamente travado.
        adminRoleMutexService.requireLockedInCurrentTransaction();

        String sourceKey = eventId + ":" + personId;
        String activeSourceKey = SOURCE_TYPE + ":" + sourceKey;

        Optional<Notification> active = notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKey);

        CelebrationEvent event = celebrationEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", eventId));
        boolean assigned = eventAssignmentRepository.existsByEvent_IdAndPerson_Id(eventId, personId);
        List<PersonUnavailability> overlapping =
                personUnavailabilityRepository.findOverlapping(personId, event.getStartAt(), event.getEndAt());
        boolean conflictExists = assigned && !overlapping.isEmpty() && event.getEndAt().isAfter(currentSecond);

        if (conflictExists && active.isEmpty()) {
            Person person = personRepository.findById(personId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
            String message = buildMessage(person.getName(), event, overlapping);
            SystemNotificationCommand command = new SystemNotificationCommand(
                    NotificationAudience.ADMIN,
                    NotificationCategory.SCHEDULE_CONFLICT,
                    TITLE,
                    message,
                    null,
                    null,
                    REFERENCE_TYPE,
                    eventId,
                    SOURCE_TYPE,
                    sourceKey
            );
            notificationDeliveryService.sendSystemNotificationForConflictReconciliation(command, activeSourceKey, currentSecond);
        } else if (!conflictExists) {
            active.ifPresent(notification -> notification.resolve(currentSecond));
        }
    }

    private String buildMessage(String personName, CelebrationEvent event, List<PersonUnavailability> overlapping) {
        List<PersonUnavailability> sorted = overlapping.stream()
                .sorted(Comparator.comparing(PersonUnavailability::getStartAt)
                        .thenComparing(PersonUnavailability::getEndAt)
                        .thenComparing(PersonUnavailability::getId))
                .toList();

        String header = "%s foi escalado(a) para %s (evento #%d), de %s a %s, em %s, e possui indisponibilidade "
                .formatted(
                        personName,
                        event.getNameMassOrEvent(),
                        event.getId(),
                        formatDateTime(event.getStartAt()),
                        formatDateTime(event.getEndAt()),
                        locationName(event)
                )
                + "sobreposta nos seguintes períodos: ";

        StringBuilder body = new StringBuilder();
        int includedCount = 0;
        for (PersonUnavailability unavailability : sorted) {
            String interval = formatDateTime(unavailability.getStartAt()) + " a " + formatDateTime(unavailability.getEndAt());
            String candidate = (includedCount == 0 ? "" : "; ") + interval;
            int omittedIfStopHere = sorted.size() - includedCount - 1;
            String trailerIfStopHere = omittedIfStopHere > 0 ? omittedSuffix(omittedIfStopHere) : ".";
            if (header.length() + body.length() + candidate.length() + trailerIfStopHere.length() > MAX_MESSAGE_LENGTH) {
                break;
            }
            body.append(candidate);
            includedCount++;
        }
        int omitted = sorted.size() - includedCount;
        String trailer = omitted > 0 ? omittedSuffix(omitted) : ".";
        return header + body + trailer;
    }

    private String omittedSuffix(int omittedCount) {
        return " (+" + omittedCount + " intervalo(s) omitido(s)).";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private String locationName(CelebrationEvent event) {
        List<Location> locations = event.getLocations();
        if (locations.isEmpty()) {
            return "Local não informado";
        }
        return locations.stream()
                .min(Comparator.comparing(Location::getId, Comparator.nullsLast(Long::compareTo)))
                .map(Location::getChurchName)
                .orElse("Local não informado");
    }
}
