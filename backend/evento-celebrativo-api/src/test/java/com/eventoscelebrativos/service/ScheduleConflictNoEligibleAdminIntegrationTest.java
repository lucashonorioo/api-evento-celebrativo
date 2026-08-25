package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.impl.AdminRoleMutexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova que, quando nao ha nenhum destinatario ROLE_ADMIN elegivel, a criacao de notificacao de
 * conflito falha explicitamente (nunca cria notificacao parcial ou "orfa") e - no fluxo completo via
 * {@link PersonUnavailabilityService#create} - a falha derruba a transacao inteira, sem deixar a
 * indisponibilidade persistida sem a notificacao correspondente. Cada teste desabilita todas as
 * contas de fixture (nove administradores elegiveis por padrao, ver R__load_test_fixtures.sql) e
 * cobre uma causa distinta e mutuamente exclusiva de inelegibilidade, espelhando exatamente os
 * predicados de UserAccountRepository#findEligibleAccounts.
 *
 * Os cinco primeiros testes chamam reconcile diretamente: nesse caminho nenhuma escrita ocorre antes
 * da falha (a checagem de elegibilidade acontece antes de qualquer INSERT), entao @Transactional com
 * rollback automatico do JUnit e suficiente. O sexto teste
 * ({@link #shouldRollBackEntireUnavailabilityCreationWhenNoEligibleAdminExists}) e diferente: ha uma
 * escrita real (a PersonUnavailability) antes da falha, entao precisa provar rollback fisico de
 * verdade - contar linhas na MESMA transacao do teste so mostraria o INSERT ainda pendente (visivel a
 * si mesma, nao commitado), o que seria um falso positivo. Por isso ele roda fora de qualquer
 * transacao ambiente do teste, deixando o proprio @Transactional de PersonUnavailabilityService#create
 * ser a fronteira transacional real, e limpa os fixtures manualmente no final.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ScheduleConflictNoEligibleAdminIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final String SOURCE_TYPE = "SCHEDULE_UNAVAILABILITY_CONFLICT";

    @Autowired
    private ScheduleConflictNotificationService scheduleConflictNotificationService;

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

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
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AdminRoleMutexService adminRoleMutexService;

    private TransactionTemplate requiresNewTransaction;
    private Long rollbackTestPersonId;

    private TransactionTemplate requiresNewTransaction() {
        if (requiresNewTransaction == null) {
            requiresNewTransaction = new TransactionTemplate(transactionManager);
            requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        return requiresNewTransaction;
    }

    @AfterEach
    void restoreFixtureAccountsEnabled() {
        // Os 5 testes @Transactional tem seu "disable all" desfeito automaticamente pelo rollback do
        // proprio JUnit/Spring ao final do metodo; nada a fazer aqui para eles (e, mais importante,
        // forcar aqui uma segunda transacao REQUIRES_NEW enquanto a transacao de teste pode estar
        // rollback-only causaria erro de suspensao/conexao). So o 6o teste roda sem transacao
        // ambiente e por isso precisa de restauracao manual explicita, em transacao propria.
        if (rollbackTestPersonId == null) {
            return;
        }
        Long personId = rollbackTestPersonId;
        rollbackTestPersonId = null;
        requiresNewTransaction().executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE tb_user_account SET enabled = TRUE");
            jdbcTemplate.update("DELETE FROM tb_notification WHERE reference_type = 'CELEBRATION_EVENT' "
                    + "AND source_key LIKE ?", "%:" + personId);
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE name_mass_or_event LIKE 'No Admin Rollback Event %'");
            jdbcTemplate.update("DELETE FROM tb_person_unavailability WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
        });
    }

    @Test
    @Transactional
    void shouldRejectWhenNoUserAccountExistsAtAll() {
        disableAllFixtureAccounts();

        assertReconcileRejectedWithNoEligibleAdmin(buildConflictScenario());
    }

    @Test
    @Transactional
    void shouldRejectWhenOnlyAdminAccountIsDisabled() {
        disableAllFixtureAccounts();
        createAccount("Admin Disabled", List.of("ROLE_ADMIN"), true, false);

        assertReconcileRejectedWithNoEligibleAdmin(buildConflictScenario());
    }

    @Test
    @Transactional
    void shouldRejectWhenOnlyAdminAccountHasInactivePerson() {
        disableAllFixtureAccounts();
        createAccount("Admin Inactive Person", List.of("ROLE_ADMIN"), false, true);

        assertReconcileRejectedWithNoEligibleAdmin(buildConflictScenario());
    }

    @Test
    @Transactional
    void shouldRejectWhenOnlyEligibleAccountHasOperatorRoleInsteadOfAdmin() {
        disableAllFixtureAccounts();
        createAccount("Operator Only", List.of("ROLE_OPERATOR"), true, true);

        assertReconcileRejectedWithNoEligibleAdmin(buildConflictScenario());
    }

    @Test
    void shouldRollBackEntireUnavailabilityCreationWhenNoEligibleAdminExists() {
        // Sem @Transactional no metodo: a fronteira transacional real e a de
        // PersonUnavailabilityServiceImpl#create (@Transactional REQUIRED cria sua propria transacao
        // de topo aqui, exatamente como em producao). Isso permite observar o COMMIT/ROLLBACK fisico
        // de verdade, em vez do estado apenas-visivel-a-si-mesma de uma transacao de teste ainda aberta.
        Long personId = requiresNewTransaction().execute(status -> {
            disableAllFixtureAccounts();
            Person person = savePerson("Rollback Person " + UUID.randomUUID());
            LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
            LocalDateTime endAt = startAt.plusHours(2);
            CelebrationEvent event = saveEvent(startAt.minusHours(1), endAt.plusHours(1), "No Admin Rollback Event " + UUID.randomUUID());
            saveAssignment(event, person);
            return person.getId();
        });
        rollbackTestPersonId = personId;

        long unavailabilitiesBefore = personUnavailabilityRepository.count();
        long notificationsBefore = notificationRepository.count();

        LocalDateTime startAt = LocalDateTime.now().plusDays(2).withNano(0);
        LocalDateTime endAt = startAt.plusHours(2);
        PersonUnavailabilityRequestDTO requestDTO = new PersonUnavailabilityRequestDTO(startAt, endAt, "Viagem");

        assertThrows(LifecycleConflictException.class,
                () -> personUnavailabilityService.create(personId, requestDTO));

        assertEquals(unavailabilitiesBefore, personUnavailabilityRepository.count(),
                "Nenhuma indisponibilidade deveria sobreviver ao commit quando a notificacao de conflito falha na mesma transacao de create()");
        assertEquals(notificationsBefore, notificationRepository.count());
        assertEquals(0, personUnavailabilityRepository.findOverlapping(personId, startAt.minusYears(1), endAt.plusYears(1)).size(),
                "A indisponibilidade que seria criada nao pode ter sobrevivido ao rollback");
    }

    private void assertReconcileRejectedWithNoEligibleAdmin(ConflictScenario scenario) {
        // reconcile() exige o mutex ROLE_ADMIN ja adquirido nesta transacao (contrato seguro); a
        // ausencia de admin elegivel so e detectada depois, dentro de deliverBroadcast.
        adminRoleMutexService.lockAdminRole();
        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> scheduleConflictNotificationService.reconcile(
                        scenario.event().getId(), scenario.person().getId(), LocalDateTime.now().withNano(0)));

        assertEquals("NOTIFICATION_NO_ELIGIBLE_RECIPIENTS", exception.getErrorCode());
        assertFalse(findNotification(scenario.event().getId(), scenario.person().getId()).isPresent(),
                "Nao deveria existir notificacao (nem parcial) quando nao ha admin elegivel");
    }

    private ConflictScenario buildConflictScenario() {
        Person person = savePerson("Conflict Person " + UUID.randomUUID());
        LocalDateTime startAt = LocalDateTime.now().plusDays(3).withNano(0);
        LocalDateTime endAt = startAt.plusHours(1);
        CelebrationEvent event = saveEvent(startAt, endAt);
        saveAssignment(event, person);
        personUnavailabilityRepository.saveAndFlush(new PersonUnavailability(person, startAt, endAt, null));
        return new ConflictScenario(event, person);
    }

    private void disableAllFixtureAccounts() {
        jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE");
    }

    private Optional<com.eventoscelebrativos.model.Notification> findNotification(Long eventId, Long personId) {
        return notificationRepository.findByActiveSourceKeyForUpdate(SOURCE_TYPE + ":" + eventId + ":" + personId);
    }

    private CelebrationEvent saveEvent(LocalDateTime startAt, LocalDateTime endAt) {
        return saveEvent(startAt, endAt, "No Admin Event " + UUID.randomUUID());
    }

    private CelebrationEvent saveEvent(LocalDateTime startAt, LocalDateTime endAt, String name) {
        return celebrationEventRepository.saveAndFlush(new CelebrationEvent(null, name, startAt, endAt, true));
    }

    private void saveAssignment(CelebrationEvent event, Person person) {
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
    }

    private Person savePerson(String name) {
        Person person = new Person(name, uniquePhone(), BIRTHDAY);
        person.activate();
        return personRepository.saveAndFlush(person);
    }

    private void createAccount(String name, List<String> authorities, boolean personActive, boolean accountEnabled) {
        Person person = new Person(name, uniquePhone(), BIRTHDAY);
        if (personActive) {
            person.activate();
        } else {
            person.deactivate();
        }
        Person savedPerson = personRepository.saveAndFlush(person);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        if (!accountEnabled) {
            savedAccount.disable(now);
            savedAccount = userAccountRepository.saveAndFlush(savedAccount);
        }
        for (String authority : authorities) {
            Role role = roleRepository.findByAuthority(authority).orElseThrow();
            userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));
        }
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }

    private record ConflictScenario(CelebrationEvent event, Person person) {
    }
}
