package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova o contrato seguro de {@link ScheduleConflictNotificationService#reconcile} (auditoria,
 * secao 2, hardening): a precondicao "mutex ROLE_ADMIN ja adquirido nesta transacao" nao fica
 * apenas documentada em Javadoc, e sim verificada em runtime contra o estado real da transacao
 * Spring atual via {@code AdminRoleMutexService#requireLockedInCurrentTransaction}, antes de
 * qualquer leitura de {@code active_source_key}.
 * <p>
 * Um guard de tipo marcador (versao anterior) parecia oferecer essa garantia em tempo de
 * compilacao, mas nao provava nada em runtime: por ser uma interface publica sem membros, qualquer
 * classe em qualquer pacote podia implementa-la ({@code class Forjado implements AdminRoleMutexGuard {}})
 * e chamar reconcile sem jamais ter passado por {@code lockAdminRole()}. Vincular a posse do mutex a
 * transacao Spring atual (nao a um objeto Java devolvido ao chamador) fecha essa brecha.
 * <p>
 * Os 6 pontos de chamada de producao (PersonUnavailabilityServiceImpl#create/update/delete e
 * CelebrationEventServiceImpl#updateEvent/updateEventScale/createEventWithScale) sao verificados
 * ponta a ponta, sempre adquirindo o mutex antes de reconciliar, por
 * {@code ScheduleConflictNotificationIntegrationTest} (11 testes) e
 * {@code ScheduleConflictConcurrencyMySqlIntegrationTest} (10 cenarios reais em MySQL) - todos
 * exercitam exclusivamente os services publicos, nunca reconcile() diretamente, entao continuam
 * compilando e passando somente porque cada chamador ja adquire o mutex na ordem certa
 * (mutex -> pessoas/eventos), sem inverter para Person -> ROLE_ADMIN.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class ScheduleConflictReconcileContractTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    @Autowired
    private AdminRoleMutexService adminRoleMutexService;

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
    private PlatformTransactionManager transactionManager;

    @Test
    void reconcileShouldFailBeforeTouchingNotificationWhenMutexWasNeverLockedInThisTransaction() {
        ConflictFixture fixture = buildConflictFixture();

        assertThrows(IllegalStateException.class,
                () -> scheduleConflictNotificationService.reconcile(
                        fixture.event().getId(), fixture.person().getId(), fixture.start()),
                "reconcile() sem o mutex adquirido nesta transacao deve falhar");

        assertFalse(findNotification(fixture).isPresent(),
                "A falha deve ocorrer antes de qualquer leitura/escrita em Notification (nenhuma notificacao pode ter sido criada)");
    }

    @Test
    void reconcileShouldSucceedEndToEndAfterLockAdminRoleInTheSameTransaction() {
        ConflictFixture fixture = buildConflictFixture();

        adminRoleMutexService.lockAdminRole();
        scheduleConflictNotificationService.reconcile(fixture.event().getId(), fixture.person().getId(), fixture.start());

        assertTrue(findNotification(fixture).isPresent(),
                "reconcile() com o mutex genuinamente adquirido nesta transacao deve funcionar normalmente de ponta a ponta");
    }

    @Test
    void reconcileShouldRejectMutexStateLeftOverFromADifferentAlreadyCompletedTransaction() {
        ConflictFixture fixture = buildConflictFixture();

        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> adminRoleMutexService.lockAdminRole());
        // A transacao REQUIRES_NEW acima ja foi concluida (commit); seu registro de posse do mutex
        // foi removido no afterCompletion e nao pode ser reaproveitado pela transacao deste teste,
        // mesmo rodando na mesma thread.

        assertThrows(IllegalStateException.class,
                () -> scheduleConflictNotificationService.reconcile(
                        fixture.event().getId(), fixture.person().getId(), fixture.start()),
                "O mutex adquirido e liberado em outra transacao (ja concluida) nao pode ser aceito por esta transacao");

        assertFalse(findNotification(fixture).isPresent());
    }

    private ConflictFixture buildConflictFixture() {
        Person person = savePerson("Contract Person " + UUID.randomUUID());
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime end = start.plusHours(1);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Contract Event " + UUID.randomUUID(), start, end, true));
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
        personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, start, end, null));
        return new ConflictFixture(event, person, start);
    }

    private java.util.Optional<com.eventoscelebrativos.model.Notification> findNotification(ConflictFixture fixture) {
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + fixture.event().getId() + ":" + fixture.person().getId();
        return notificationRepository.findByActiveSourceKeyForUpdate(activeSourceKey);
    }

    private Person savePerson(String name) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(true);
        return personRepository.saveAndFlush(person);
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3493" + String.format("%07d", suffix);
    }

    private record ConflictFixture(CelebrationEvent event, Person person, LocalDateTime start) {
    }
}
