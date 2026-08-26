package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com Spring context e banco H2 reais, que {@link ScheduleConflictNotificationServiceImpl#reconcile}
 * e status-aware (secao 25/26/27 da feature celebration-cancellation-lifecycle): um evento CANCELLED
 * nunca produz conflito operacional; cancelar resolve imediatamente qualquer ocorrencia ativa;
 * reconciliar novamente enquanto CANCELLED nao recria nada; reativar reavalia e cria uma NOVA
 * ocorrencia se a sobreposicao ainda existir; remover a sobreposicao enquanto CANCELLED impede que a
 * reativacao recrie o conflito.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class ScheduleConflictCancelledEventIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

    @Autowired
    private CelebrationEventLifecycleService celebrationEventLifecycleService;

    @Autowired
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private AdminRoleMutexService adminRoleMutexService;

    private void reconcile(Long eventId, Long personId) {
        adminRoleMutexService.lockAdminRole();
        scheduleConflictNotificationService.reconcile(eventId, personId, CURRENT_SECOND);
    }

    @Test
    void cancelResolvesActiveConflictAndReconcileNeverRecreatesItWhileCancelled() {
        Person person = savePerson("Cancel Resolves Conflict");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(30), CURRENT_SECOND.plusDays(30).plusHours(1));
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getEndAt());

        reconcile(event.getId(), person.getId());
        assertTrue(activeConflictExists(event.getId(), person.getId()), "Conflito deveria estar ativo antes do cancelamento");

        celebrationEventLifecycleService.cancel(event.getId());
        assertFalse(activeConflictExists(event.getId(), person.getId()), "Cancelar deve resolver o conflito ativo");

        reconcile(event.getId(), person.getId());
        assertFalse(activeConflictExists(event.getId(), person.getId()), "Reconciliar evento ainda CANCELLED nunca recria o conflito");
    }

    @Test
    void reactivateRecreatesConflictWhenOverlapStillExists() {
        Person person = savePerson("Reactivate Recreates Conflict");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(31), CURRENT_SECOND.plusDays(31).plusHours(1));
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getEndAt());

        reconcile(event.getId(), person.getId());
        assertTrue(activeConflictExists(event.getId(), person.getId()));

        celebrationEventLifecycleService.cancel(event.getId());
        assertFalse(activeConflictExists(event.getId(), person.getId()));

        celebrationEventLifecycleService.reactivate(event.getId());
        assertTrue(activeConflictExists(event.getId(), person.getId()), "Reativar deve recriar o conflito se a sobreposicao ainda existir");
    }

    @Test
    void reactivateDoesNotRecreateConflictWhenOverlapWasRemovedWhileCancelled() {
        Person person = savePerson("Reactivate No Conflict After Removal");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(32), CURRENT_SECOND.plusDays(32).plusHours(1));
        saveAssignment(event, person);
        PersonUnavailability unavailability = saveUnavailability(person, event.getStartAt(), event.getEndAt());

        reconcile(event.getId(), person.getId());
        assertTrue(activeConflictExists(event.getId(), person.getId()));

        celebrationEventLifecycleService.cancel(event.getId());
        assertFalse(activeConflictExists(event.getId(), person.getId()));

        personUnavailabilityRepository.delete(unavailability);
        personUnavailabilityRepository.flush();

        celebrationEventLifecycleService.reactivate(event.getId());
        assertFalse(activeConflictExists(event.getId(), person.getId()),
                "Sem sobreposicao restante, reativar nao deve criar um novo conflito");
    }

    private boolean activeConflictExists(Long eventId, Long personId) {
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + personId;
        Optional<Notification> notification = notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKey);
        return notification.isPresent() && notification.get().getResolvedAt() == null;
    }

    private CelebrationEvent saveEvent(LocalDateTime startAt, LocalDateTime endAt) {
        return celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Cancelled Conflict Event " + UUID.randomUUID(), startAt, endAt, true));
    }

    private void saveAssignment(CelebrationEvent event, Person person) {
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
    }

    private PersonUnavailability saveUnavailability(Person person, LocalDateTime startAt, LocalDateTime endAt) {
        return personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, startAt, endAt, null));
    }

    // Person precisa de PersonMinistry(READER).active=true: reactivate() revalida a escala preservada
    // durante o cancelamento (secao 32/38) via PersonMinistryEligibilityResolver antes de reconciliar.
    private Person savePerson(String name) {
        Person person = new Person(name, uniquePhone(), BIRTHDAY);
        person.activate();
        Person saved = personRepository.saveAndFlush(person);
        personMinistryRepository.saveAndFlush(personMinistry(saved, MinistryType.READER, ministryRepository));
        return saved;
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }
}
