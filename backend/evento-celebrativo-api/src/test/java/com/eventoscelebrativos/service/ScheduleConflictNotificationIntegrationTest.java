package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com contexto Spring real (H2) e os services reais de ciclo de vida, ministerio, escala e
 * indisponibilidade (nenhum mock), a reconciliacao ponta-a-ponta de conflitos de escala x
 * indisponibilidade (secoes 3, 9, 11, 12): criacao, deduplicacao, resolucao e reaparecimento,
 * usando Clock fixo (nunca passagem real de tempo).
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Import(ScheduleConflictNotificationIntegrationTest.FixedClockConfig.class)
@Transactional
class ScheduleConflictNotificationIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 10, 0, 0);
    private static final String REFERENCE_TYPE = "CELEBRATION_EVENT";
    private static final String SOURCE_TYPE = "SCHEDULE_UNAVAILABILITY_CONFLICT";

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private EntityManager entityManager;

    private Long adminAccountId;

    @Test
    void shouldCreateActiveConflictWhenSchedulingPersonAlreadyUnavailable() {
        createAdminAccount("Conflict Admin One");
        Person reader = createPersonWithMinistry("Conflict Reader One", MinistryType.READER);
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(2), "Motivo privado nao deve aparecer"));

        Long eventId = createEventWithReader(
                "Conflict Event One", CURRENT_SECOND.plusDays(1).plusMinutes(30), CURRENT_SECOND.plusDays(1).plusMinutes(90), reader.getId());

        Notification notification = findActiveConflict(eventId, reader.getId()).orElseThrow();
        assertEquals(NotificationOrigin.SYSTEM, notification.getOrigin());
        assertEquals(NotificationAudience.ADMIN, notification.getAudience());
        assertEquals(NotificationCategory.SCHEDULE_CONFLICT, notification.getCategory());
        assertEquals(REFERENCE_TYPE, notification.getReferenceType());
        assertEquals(eventId, notification.getReferenceId());
        assertEquals(SOURCE_TYPE, notification.getSourceType());
        assertEquals(eventId + ":" + reader.getId(), notification.getSourceKey());
        assertEquals("Conflito de escala detectado", notification.getTitle());
        assertNull(notification.getResolvedAt());
        assertFalse(notification.getMessage().contains("Motivo privado"), "Reason privado nunca deve aparecer na mensagem");

        assertTrue(notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(notification.getId(), adminAccountId)
                .isPresent(), "Admin deve ser destinatario da notificacao de conflito");
    }

    @Test
    void shouldCreateActiveConflictWhenUnavailabilityCreatedOverExistingAssignment() {
        createAdminAccount("Conflict Admin Two");
        Person reader = createPersonWithMinistry("Conflict Reader Two", MinistryType.READER);
        Long eventId = createEventWithReader(
                "Conflict Event Two", CURRENT_SECOND.plusDays(2), CURRENT_SECOND.plusDays(2).plusHours(1), reader.getId());

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(), "Sem indisponibilidade, nao ha conflito ainda");

        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(2), CURRENT_SECOND.plusDays(2).plusHours(3), null));

        Notification notification = findActiveConflict(eventId, reader.getId()).orElseThrow();
        assertNull(notification.getResolvedAt());
    }

    @Test
    void shouldResolveConflictWhenEventIsRescheduledIntoAlreadyEndedRange() {
        // Nao e possivel criar uma indisponibilidade com startAt no passado (invariante preservado),
        // entao a unica forma de exercitar "endAt <= currentSecond nao gera/mantem conflito" e
        // reagendando um evento com conflito ja ativo para um intervalo encerrado.
        createAdminAccount("Conflict Admin Three");
        Person reader = createPersonWithMinistry("Conflict Reader Three", MinistryType.READER);
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Three", CURRENT_SECOND.plusDays(1).plusMinutes(30), CURRENT_SECOND.plusDays(1).plusMinutes(90), reader.getId());
        Notification active = findActiveConflict(eventId, reader.getId()).orElseThrow();

        LocalDateTime endedStartAt = CURRENT_SECOND.minusHours(3);
        LocalDateTime endedEndAt = CURRENT_SECOND.minusHours(2);
        celebrationEventService.updateEvent(eventId, new CelebrationEventRequestDTO(
                "Conflict Event Three", endedStartAt, endedEndAt, true));

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(),
                "Evento com endAt <= currentSecond deve resolver o conflito ativo, nunca mante-lo");
        Notification reloaded = notificationRepository.findById(active.getId()).orElseThrow();
        assertNotNull(reloaded.getResolvedAt());
    }

    @Test
    void shouldNotDuplicateNotificationWhenConflictIsDetectedRepeatedly() {
        createAdminAccount("Conflict Admin Four");
        Person reader = createPersonWithMinistry("Conflict Reader Four", MinistryType.READER);
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(3), CURRENT_SECOND.plusDays(3).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Four", CURRENT_SECOND.plusDays(3).plusMinutes(30), CURRENT_SECOND.plusDays(3).plusMinutes(90), reader.getId());

        Notification first = findActiveConflict(eventId, reader.getId()).orElseThrow();

        // Segunda deteccao: altera apenas o local, mantendo a mesma pessoa/funcao/horario - a
        // reconciliacao roda de novo para o mesmo par eventId+personId.
        Location newLocation = locationRepository.saveAndFlush(new Location(null, "Nova Igreja " + UUID.randomUUID(), "Endereco"));
        celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                newLocation.getId(), null, List.of(reader.getId()), null, null, null));

        Notification second = findActiveConflict(eventId, reader.getId()).orElseThrow();
        assertEquals(first.getId(), second.getId(), "Deteccao repetida do mesmo conflito nao deve criar nova notificacao");
        assertEquals(first.getCreatedAt(), second.getCreatedAt(), "createdAt (snapshot) nao deve ser atualizado");
    }

    @Test
    void shouldResolveConflictWhenPersonIsRemovedFromSchedule() {
        createAdminAccount("Conflict Admin Five");
        Person reader = createPersonWithMinistry("Conflict Reader Five", MinistryType.READER);
        Location location = locationRepository.saveAndFlush(new Location(null, "Igreja Cinco " + UUID.randomUUID(), "Endereco"));
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(4), CURRENT_SECOND.plusDays(4).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Five", CURRENT_SECOND.plusDays(4).plusMinutes(30), CURRENT_SECOND.plusDays(4).plusMinutes(90), reader.getId());
        assertTrue(findActiveConflict(eventId, reader.getId()).isPresent());

        celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                location.getId(), null, List.of(), null, null, null));

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(), "Nao deve haver mais notificacao ativa para esta identidade");
    }

    @Test
    void shouldResolveAllActiveConflictsWhenEventIsDeleted() {
        createAdminAccount("Conflict Admin Six");
        Person reader = createPersonWithMinistry("Conflict Reader Six", MinistryType.READER);
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(5), CURRENT_SECOND.plusDays(5).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Six", CURRENT_SECOND.plusDays(5).plusMinutes(30), CURRENT_SECOND.plusDays(5).plusMinutes(90), reader.getId());
        Notification active = findActiveConflict(eventId, reader.getId()).orElseThrow();

        celebrationEventService.deleteEventById(eventId);
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(active.getId()).orElseThrow();
        assertEquals(CURRENT_SECOND, reloaded.getResolvedAt(), "Exclusao do evento resolve com o currentSecond do comando");
        assertNull(reloaded.getActiveSourceKey());
    }

    @Test
    void shouldCreateNewNotificationOnReappearanceAfterResolutionKeepingOldOneResolved() {
        createAdminAccount("Conflict Admin Seven");
        Person reader = createPersonWithMinistry("Conflict Reader Seven", MinistryType.READER);
        Location location = locationRepository.saveAndFlush(new Location(null, "Igreja Sete " + UUID.randomUUID(), "Endereco"));
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(6), CURRENT_SECOND.plusDays(6).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Seven", CURRENT_SECOND.plusDays(6).plusMinutes(30), CURRENT_SECOND.plusDays(6).plusMinutes(90), reader.getId());
        Notification firstOccurrence = findActiveConflict(eventId, reader.getId()).orElseThrow();

        celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                location.getId(), null, List.of(), null, null, null));
        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty());

        celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                location.getId(), null, List.of(reader.getId()), null, null, null));

        Notification secondOccurrence = findActiveConflict(eventId, reader.getId()).orElseThrow();
        assertNotEquals(firstOccurrence.getId(), secondOccurrence.getId(), "Reaparecimento deve criar uma nova Notification");
        assertNull(secondOccurrence.getResolvedAt());

        Notification reloadedFirst = notificationRepository.findById(firstOccurrence.getId()).orElseThrow();
        assertNotNull(reloadedFirst.getResolvedAt(), "A ocorrencia antiga permanece resolvida");

        long activeCount = notificationRepository.findAdminHistory(
                        null, null, null, null, null, NotificationCategory.SCHEDULE_CONFLICT, Boolean.FALSE,
                        org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .filter(n -> eventId.equals(n.getReferenceId()) && n.getSourceKey().equals(eventId + ":" + reader.getId()))
                .count();
        assertEquals(1, activeCount, "Deve haver exatamente uma ocorrencia ativa para esta identidade");
    }

    @Test
    void shouldResolveConflictWhenUnavailabilityIsDeleted() {
        createAdminAccount("Conflict Admin Eight");
        Person reader = createPersonWithMinistry("Conflict Reader Eight", MinistryType.READER);
        com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                reader.getId(), new PersonUnavailabilityRequestDTO(
                        CURRENT_SECOND.plusDays(7), CURRENT_SECOND.plusDays(7).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Eight", CURRENT_SECOND.plusDays(7).plusMinutes(30), CURRENT_SECOND.plusDays(7).plusMinutes(90), reader.getId());
        Notification active = findActiveConflict(eventId, reader.getId()).orElseThrow();

        personUnavailabilityService.delete(reader.getId(), unavailability.getId());

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(),
                "Excluir a indisponibilidade deve resolver o conflito ativo (reconcile disparado por delete())");
        Notification reloaded = notificationRepository.findById(active.getId()).orElseThrow();
        assertNotNull(reloaded.getResolvedAt());
    }

    @Test
    void shouldResolveConflictWhenUnavailabilityIsUpdatedToNoLongerOverlapTheEvent() {
        createAdminAccount("Conflict Admin Nine");
        Person reader = createPersonWithMinistry("Conflict Reader Nine", MinistryType.READER);
        com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                reader.getId(), new PersonUnavailabilityRequestDTO(
                        CURRENT_SECOND.plusDays(8), CURRENT_SECOND.plusDays(8).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Nine", CURRENT_SECOND.plusDays(8).plusMinutes(30), CURRENT_SECOND.plusDays(8).plusMinutes(90), reader.getId());
        Notification active = findActiveConflict(eventId, reader.getId()).orElseThrow();

        personUnavailabilityService.update(reader.getId(), unavailability.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(20), CURRENT_SECOND.plusDays(20).plusHours(2), null));

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(),
                "Editar a indisponibilidade para nao sobrepor mais o evento deve resolver o conflito (reconcile disparado por update())");
        Notification reloaded = notificationRepository.findById(active.getId()).orElseThrow();
        assertNotNull(reloaded.getResolvedAt());
    }

    @Test
    void shouldNotDuplicateNotificationWhenUnavailabilityIsUpdatedButStillOverlapsTheEvent() {
        createAdminAccount("Conflict Admin Ten");
        Person reader = createPersonWithMinistry("Conflict Reader Ten", MinistryType.READER);
        com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(
                reader.getId(), new PersonUnavailabilityRequestDTO(
                        CURRENT_SECOND.plusDays(9), CURRENT_SECOND.plusDays(9).plusHours(3), null));
        Long eventId = createEventWithReader(
                "Conflict Event Ten", CURRENT_SECOND.plusDays(9).plusMinutes(30), CURRENT_SECOND.plusDays(9).plusMinutes(90), reader.getId());
        Notification first = findActiveConflict(eventId, reader.getId()).orElseThrow();

        // Estreita o intervalo mas mantem sobreposicao com o evento (que vai de +30min a +90min do dia 9).
        personUnavailabilityService.update(reader.getId(), unavailability.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(9).plusMinutes(45), CURRENT_SECOND.plusDays(9).plusMinutes(75), "Motivo atualizado"));

        Notification second = findActiveConflict(eventId, reader.getId()).orElseThrow();
        assertEquals(first.getId(), second.getId(), "Ainda ha sobreposicao: deve reaproveitar a mesma notificacao ativa");
        assertEquals(first.getCreatedAt(), second.getCreatedAt());
    }

    @Test
    void shouldResolveConflictWhenEventIsRescheduledToADifferentFutureRangeThatNoLongerOverlaps() {
        createAdminAccount("Conflict Admin Eleven");
        Person reader = createPersonWithMinistry("Conflict Reader Eleven", MinistryType.READER);
        personUnavailabilityService.create(reader.getId(), new PersonUnavailabilityRequestDTO(
                CURRENT_SECOND.plusDays(10), CURRENT_SECOND.plusDays(10).plusHours(2), null));
        Long eventId = createEventWithReader(
                "Conflict Event Eleven", CURRENT_SECOND.plusDays(10).plusMinutes(30), CURRENT_SECOND.plusDays(10).plusMinutes(90), reader.getId());
        Notification active = findActiveConflict(eventId, reader.getId()).orElseThrow();

        // Evento continua no futuro (nao encerrado) mas movido para fora da janela da indisponibilidade.
        celebrationEventService.updateEvent(eventId, new CelebrationEventRequestDTO(
                "Conflict Event Eleven", CURRENT_SECOND.plusDays(30), CURRENT_SECOND.plusDays(30).plusHours(1), true));

        assertTrue(findActiveConflict(eventId, reader.getId()).isEmpty(),
                "Reagendar para um horario futuro que nao sobrepoe mais deve resolver, mesmo sem o evento ter encerrado");
        Notification reloaded = notificationRepository.findById(active.getId()).orElseThrow();
        assertNotNull(reloaded.getResolvedAt());
    }

    private void createAdminAccount(String name) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(true);
        Person savedPerson = personRepository.saveAndFlush(person);

        LocalDateTime now = CURRENT_SECOND;
        UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow();
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));
        entityManager.clear();
        this.adminAccountId = savedAccount.getId();
    }

    private Person createPersonWithMinistry(String name, MinistryType ministryType) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(true);
        Person savedPerson = personRepository.saveAndFlush(person);
        personMinistryRepository.saveAndFlush(new PersonMinistry(savedPerson, ministryType));
        entityManager.clear();
        return savedPerson;
    }

    private Long createEventWithReader(String name, LocalDateTime startAt, LocalDateTime endAt, Long readerId) {
        Location location = locationRepository.saveAndFlush(new Location(null, "Igreja " + UUID.randomUUID(), "Endereco"));
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name);
        request.setStartAt(startAt);
        request.setEndAt(endAt);
        request.setMassOrCelebration(true);
        request.setLocationId(location.getId());
        request.setReaderIds(List.of(readerId));
        return celebrationEventService.createEventWithScale(request).getEventId();
    }

    private Optional<Notification> findActiveConflict(Long eventId, Long personId) {
        return notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKeyOf(eventId, personId));
    }

    private String activeSourceKeyOf(Long eventId, Long personId) {
        return SOURCE_TYPE + ":" + eventId + ":" + personId;
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
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
