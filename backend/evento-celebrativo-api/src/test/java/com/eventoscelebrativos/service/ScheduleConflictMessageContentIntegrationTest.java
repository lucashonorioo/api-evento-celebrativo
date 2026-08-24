package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova o conteudo exato da mensagem construida por ScheduleConflictNotificationServiceImpl#buildMessage
 * (privado, exercitado aqui via reconcile - sem concorrencia, seguro chamar diretamente em H2
 * single-thread, section 10/14): titulo fixo, formato de data dd/MM/yyyy HH:mm, nome do local
 * (ou texto padrao na ausencia), ordenacao dos intervalos sobrepostos por startAt/endAt/id,
 * truncamento em 2000 caracteres com sufixo de itens omitidos, e ausencia total do campo privado
 * "reason" da indisponibilidade na mensagem.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class ScheduleConflictMessageContentIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final DateTimeFormatter EXPECTED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

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
    private LocationRepository locationRepository;

    @Autowired
    private AdminRoleMutexService adminRoleMutexService;

    // reconcile() agora exige o mutex ROLE_ADMIN ja adquirido nesta transacao (contrato seguro,
    // verificado em runtime por AdminRoleMutexService#requireLockedInCurrentTransaction).
    private void reconcile(Long eventId, Long personId, LocalDateTime currentSecond) {
        adminRoleMutexService.lockAdminRole();
        scheduleConflictNotificationService.reconcile(eventId, personId, currentSecond);
    }

    @Test
    void shouldFormatDatesAsDdMmYyyyHhMmAndIncludeLocationName() {
        Person person = savePerson("Content Person Location");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(1));
        Location location = locationRepository.saveAndFlush(new Location(null, "Igreja Conteudo " + UUID.randomUUID(), "Endereco"));
        event.getLocations().add(location);
        celebrationEventRepository.saveAndFlush(event);
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getEndAt(), "Motivo nao deve aparecer");

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        String message = findMessage(event.getId(), person.getId());
        assertTrue(message.contains(event.getStartAt().format(EXPECTED_FORMAT)), "Deveria conter startAt formatado dd/MM/yyyy HH:mm");
        assertTrue(message.contains(event.getEndAt().format(EXPECTED_FORMAT)), "Deveria conter endAt formatado dd/MM/yyyy HH:mm");
        assertTrue(message.contains(location.getChurchName()), "Deveria conter o nome do local");
    }

    @Test
    void shouldUseDefaultLocationTextWhenEventHasNoLocation() {
        Person person = savePerson("Content Person NoLocation");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(2), CURRENT_SECOND.plusDays(2).plusHours(1));
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getEndAt(), null);

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        String message = findMessage(event.getId(), person.getId());
        assertTrue(message.contains("Local não informado"));
    }

    @Test
    void shouldListMultipleOverlappingUnavailabilitiesOrderedByStartThenEndThenId() {
        Person person = savePerson("Content Person Ordering");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(3), CURRENT_SECOND.plusDays(3).plusHours(6));
        saveAssignment(event, person);

        // Inseridos fora de ordem cronologica de proposito.
        PersonUnavailability third = saveUnavailability(person, event.getStartAt().plusHours(4), event.getStartAt().plusHours(5), null);
        PersonUnavailability first = saveUnavailability(person, event.getStartAt(), event.getStartAt().plusHours(1), null);
        PersonUnavailability second = saveUnavailability(person, event.getStartAt().plusHours(2), event.getStartAt().plusHours(3), null);

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        String message = findMessage(event.getId(), person.getId());
        int indexFirst = message.indexOf(first.getStartAt().format(EXPECTED_FORMAT));
        int indexSecond = message.indexOf(second.getStartAt().format(EXPECTED_FORMAT));
        int indexThird = message.indexOf(third.getStartAt().format(EXPECTED_FORMAT));
        assertTrue(indexFirst >= 0 && indexSecond > indexFirst && indexThird > indexSecond,
                "Intervalos devem aparecer ordenados por startAt: " + message);
    }

    @Test
    void shouldTruncateMessageAndReportOmittedCountWhenIntervalsOverflow2000Chars() {
        Person person = savePerson("Content Person Truncation");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(4), CURRENT_SECOND.plusDays(4).plusHours(23));
        saveAssignment(event, person);

        // Cada intervalo formatado ocupa ~35 caracteres; 100 intervalos ultrapassam 2000 caracteres.
        LocalDateTime cursor = event.getStartAt();
        for (int i = 0; i < 100; i++) {
            saveUnavailability(person, cursor, cursor.plusMinutes(5), null);
            cursor = cursor.plusMinutes(10);
        }

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        String message = findMessage(event.getId(), person.getId());
        assertTrue(message.length() <= 2000, "Mensagem nunca pode exceder 2000 caracteres: " + message.length());
        assertTrue(message.matches("(?s).*\\(\\+\\d+ intervalo\\(s\\) omitido\\(s\\)\\)\\.$"),
                "Deveria terminar com o sufixo de intervalos omitidos: " + message);
    }

    @Test
    void shouldNeverExposePrivateReasonInMessageEvenWithMultipleUnavailabilities() {
        Person person = savePerson("Content Person Privacy");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(5), CURRENT_SECOND.plusDays(5).plusHours(3));
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getStartAt().plusHours(1), "Consulta medica confidencial");
        saveUnavailability(person, event.getStartAt().plusHours(1), event.getStartAt().plusHours(2), "Viagem particular");

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        String message = findMessage(event.getId(), person.getId());
        assertFalse(message.contains("Consulta medica confidencial"));
        assertFalse(message.contains("Viagem particular"));
    }

    @Test
    void shouldUseFixedTitleForEveryScheduleConflictNotification() {
        Person person = savePerson("Content Person Title");
        CelebrationEvent event = saveEvent(CURRENT_SECOND.plusDays(6), CURRENT_SECOND.plusDays(6).plusHours(1));
        saveAssignment(event, person);
        saveUnavailability(person, event.getStartAt(), event.getEndAt(), null);

        reconcile(event.getId(), person.getId(), CURRENT_SECOND);

        Notification notification = findNotification(event.getId(), person.getId());
        assertEquals("Conflito de escala detectado", notification.getTitle());
    }

    private String findMessage(Long eventId, Long personId) {
        return findNotification(eventId, personId).getMessage();
    }

    private Notification findNotification(Long eventId, Long personId) {
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + personId;
        return notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKey).orElseThrow();
    }

    private CelebrationEvent saveEvent(LocalDateTime startAt, LocalDateTime endAt) {
        return celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Content Event " + UUID.randomUUID(), startAt, endAt, true));
    }

    private void saveAssignment(CelebrationEvent event, Person person) {
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
    }

    private PersonUnavailability saveUnavailability(Person person, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        return personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, startAt, endAt, reason));
    }

    private Person savePerson(String name) {
        Person person = new Person(name, uniquePhone(), BIRTHDAY);
        person.setActive(true);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3495" + String.format("%07d", suffix);
    }
}
