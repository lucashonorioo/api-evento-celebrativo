package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a matriz temporal de {@link ScheduleConflictNotificationService#reconcile} isoladamente,
 * chamando o service diretamente sobre dados montados via repository (contornando as validacoes de
 * entrada da API publica, que existem para proteger o usuario final, nao o dominio) para exercitar
 * exatamente as fronteiras pedidas: endAt antes/igual/depois de currentSecond, evento em andamento,
 * reaparecimento apos reagendamento para o futuro e a semantica semiaberta [startAt, endAt) ja
 * validada em profundidade por PersonUnavailabilityRepository (reconcile reaproveita
 * findOverlapping, nao duplica a formula).
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Import(ScheduleConflictTemporalBoundaryIntegrationTest.FixedClockConfig.class)
@Transactional
class ScheduleConflictTemporalBoundaryIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 12, 0, 0);
    private static final String SOURCE_TYPE = "SCHEDULE_UNAVAILABILITY_CONFLICT";

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
    private AdminRoleMutexService adminRoleMutexService;

    // reconcile() agora exige o mutex ROLE_ADMIN ja adquirido nesta transacao (contrato seguro,
    // verificado em runtime por AdminRoleMutexService#requireLockedInCurrentTransaction).
    private void reconcile(Long eventId, Long personId, LocalDateTime currentSecond) {
        adminRoleMutexService.lockAdminRole();
        scheduleConflictNotificationService.reconcile(eventId, personId, currentSecond);
    }

    @Test
    void shouldNotCreateWhenEventEndAtIsBeforeCurrentSecond() {
        Person person = savePerson("Boundary Person Before");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.minusHours(3), CURRENT_SECOND.minusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND.minusHours(3), CURRENT_SECOND.plusHours(1));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        assertActiveConflictAbsent(event.getId(), person.getId());
    }

    @Test
    void shouldNotCreateWhenEventEndAtEqualsCurrentSecondExactly() {
        Person person = savePerson("Boundary Person Equal");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.minusHours(1), CURRENT_SECOND);
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND.minusHours(1), CURRENT_SECOND.plusHours(1));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        assertActiveConflictAbsent(event.getId(), person.getId());
    }

    @Test
    void shouldCreateWhenEventIsCurrentlyInProgress() {
        Person person = savePerson("Boundary Person InProgress");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.minusHours(1), CURRENT_SECOND.plusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND, CURRENT_SECOND.plusHours(2));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        assertActiveConflictPresent(event.getId(), person.getId());
    }

    @Test
    void shouldCreateWhenEventIsFullyInTheFuture() {
        Person person = savePerson("Boundary Person Future");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(2));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        assertActiveConflictPresent(event.getId(), person.getId());
    }

    @Test
    void shouldCreateNewOccurrenceWhenEndedEventIsMovedForwardToTheFutureAgain() {
        Person person = savePerson("Boundary Person Reappear");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.minusHours(3), CURRENT_SECOND.minusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND.minusHours(3), CURRENT_SECOND.plusDays(2));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);
        assertActiveConflictAbsent(event.getId(), person.getId());

        event.setStartAt(CURRENT_SECOND.plusDays(1));
        event.setEndAt(CURRENT_SECOND.plusDays(1).plusHours(1));
        celebrationEventRepository.saveAndFlush(event);

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);
        assertActiveConflictPresent(event.getId(), person.getId());
    }

    @Test
    void shouldNotOverlapWhenUnavailabilityEndsExactlyWhenEventStarts() {
        // Semantica semiaberta [startAt, endAt): unavailability.endAt == event.startAt nao e overlap.
        Person person = savePerson("Boundary Person SemiOpen");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND, CURRENT_SECOND.plusDays(1));

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        assertActiveConflictAbsent(event.getId(), person.getId());
    }

    @Test
    void shouldPersistCreatedAtWithNanosNormalizedToZeroWhenReconciling() {
        Person person = savePerson("Boundary Person Nanos");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(1));
        saveAssignment(event, person);
        saveUnavailabilityBypassingServiceValidation(person, CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(2));

        // currentSecond aqui simula o valor ja normalizado pelos chamadores reais (todos capturam via
        // LocalDateTime.now(clock).withNano(0) antes de invocar reconcile); comprova que o valor
        // persistido em createdAt preserva essa normalizacao sem reintroduzir nanos.
        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        Notification notification = findNotification(event.getId(), person.getId()).orElseThrow();
        assertEquals(0, notification.getCreatedAt().getNano());
        assertEquals(CURRENT_SECOND, notification.getCreatedAt());
    }

    private void assertActiveConflictPresent(Long eventId, Long personId) {
        Optional<Notification> notification = findNotification(eventId, personId);
        assertTrue(notification.isPresent(), "Deveria existir uma notificacao ativa para " + eventId + ":" + personId);
        assertEquals(null, notification.get().getResolvedAt());
    }

    private void assertActiveConflictAbsent(Long eventId, Long personId) {
        assertFalse(findNotification(eventId, personId).isPresent(),
                "Nao deveria existir notificacao ativa para " + eventId + ":" + personId);
    }

    private Optional<Notification> findNotification(Long eventId, Long personId) {
        return notificationRepository.findByActiveSourceKeyForUpdate(SOURCE_TYPE + ":" + eventId + ":" + personId);
    }

    private CelebrationEvent saveEvent(LocalDateTime startAt, LocalDateTime endAt) {
        return celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Boundary Event " + UUID.randomUUID(), startAt, endAt, true));
    }

    private void saveAssignment(CelebrationEvent event, Person person) {
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
    }

    /**
     * Grava a indisponibilidade diretamente via repository, contornando
     * PersonUnavailabilityServiceImpl.validateTemporalRule (que so existe para proteger a API
     * publica contra startAt no passado) - necessario para montar fronteiras temporais especificas
     * (ex.: evento ja encerrado) que a API publica nunca permitiria criar diretamente.
     */
    private void saveUnavailabilityBypassingServiceValidation(Person person, LocalDateTime startAt, LocalDateTime endAt) {
        personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, startAt, endAt, null));
    }

    private Person savePerson(String name) {
        Person person = new Person(name, uniquePhone(), BIRTHDAY);
        person.setActive(true);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
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
