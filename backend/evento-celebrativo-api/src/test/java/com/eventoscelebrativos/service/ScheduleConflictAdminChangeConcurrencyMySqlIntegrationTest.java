package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountEnabledRequestDTO;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
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
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com MySQL 8.4 real e os services reais de ciclo de vida
 * ({@link UserAccountLifecycleService}) e de conflito de escala
 * ({@link PersonUnavailabilityService}/{@link CelebrationEventService}), o comportamento de
 * criacao de uma notificacao SCHEDULE_CONFLICT concorrendo com cada uma das 3 alteracoes
 * administrativas pedidas na auditoria final: desabilitacao de UserAccount, desativacao de Person e
 * mudanca de role ROLE_ADMIN->ROLE_OPERATOR. Cada cenario tem duas variantes deterministicas
 * (ordem controlada por CountDownLatch entre duas threads/conexoes reais, nunca sleep como
 * sincronizacao principal): a alteracao administrativa confirmando (commitando) antes da
 * resolucao de destinatarios, e o envio confirmando antes da alteracao administrativa.
 * <p>
 * Bloqueio real do mutex ROLE_ADMIN sob MySQL ja e comprovado por
 * {@code AdminRoleMutexServiceMySqlIntegrationTest}; aqui o foco e o invariante de negocio -
 * elegibilidade correta no instante do snapshot, protecao do ultimo administrador efetivo e
 * ausencia de recipient duplicado ou de destinatario invalido - sob as duas ordens possiveis.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ScheduleConflictAdminChangeConcurrencyMySqlIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

    @Autowired
    private UserAccountLifecycleService userAccountLifecycleService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void provisionIsolatedDatabase() throws SQLException {
        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty("mysql.validation.password", System.getenv("MYSQL_VALIDATION_PASSWORD"));

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password)) {
            mysqlAvailable = connection.isValid(3);
        } catch (SQLException e) {
            mysqlAvailable = false;
            return;
        }

        databaseName = "v17_admin_change_concurrency_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        if (!mysqlAvailable) {
            return;
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.notifications.schedule-conflict-resolution.enabled", () -> "false");
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    @AfterAll
    static void dropIsolatedDatabase() {
        if (!mysqlAvailable || databaseName == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        } catch (SQLException ignored) {
            // best-effort cleanup
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @AfterEach
    void cleanup() {
        if (!mysqlAvailable) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM tb_notification_recipient");
            jdbcTemplate.update("DELETE FROM tb_notification");
            jdbcTemplate.update("DELETE FROM tb_event_assignment");
            jdbcTemplate.update("DELETE FROM tb_event_location");
            jdbcTemplate.update("DELETE FROM tb_celebration_event");
            jdbcTemplate.update("DELETE FROM tb_person_unavailability");
            jdbcTemplate.update("DELETE FROM tb_person_ministry");
            jdbcTemplate.update("DELETE FROM tb_user_account_role");
            jdbcTemplate.update("DELETE FROM tb_user_account");            jdbcTemplate.update("DELETE FROM tb_person");
            jdbcTemplate.update("DELETE FROM tb_location");
        });
    }

    // ---------------------------------------------------------------------------------------
    // 1. Desabilitacao de UserAccount
    // ---------------------------------------------------------------------------------------

    @RepeatedTest(5)
    void shouldExcludeTargetAdminWhenAccountDisableConfirmsBeforeNotificationSend() throws Exception {
        Scenario scenario = buildScenario("K1A");

        runInOrder(
                () -> disableAccount(scenario.actingAdmin(), scenario.targetAdmin()),
                () -> createConflict(scenario)
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification, "Conflito deveria ter sido criado normalmente apos a desabilitacao");
        assertRecipientAbsent(notification.id(), scenario.targetAdmin().accountId(),
                "Administrador desabilitado antes da resolucao de destinatarios nao pode ser recipient");
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertExactlyOneActiveNotification(scenario);
        assertNoDuplicateRecipients(notification.id());
        assertFalse(userAccountRepository.findById(scenario.targetAdmin().accountId()).orElseThrow().isEnabled());
    }

    @RepeatedTest(5)
    void shouldKeepTargetAdminAsHistoricalRecipientWhenSendConfirmsBeforeAccountDisable() throws Exception {
        Scenario scenario = buildScenario("K1B");

        runInOrder(
                () -> createConflict(scenario),
                () -> disableAccount(scenario.actingAdmin(), scenario.targetAdmin())
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification);
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.targetAdmin());
        assertEquals(2, countRecipients(notification.id()),
                "Snapshot deve preservar os dois destinatarios elegiveis no instante do envio");
        assertExactlyOneActiveNotification(scenario);
        assertFalse(userAccountRepository.findById(scenario.targetAdmin().accountId()).orElseThrow().isEnabled(),
                "A alteracao administrativa, ocorrendo depois, ainda deve ter sucesso");
        assertEquals(2, countRecipients(notification.id()),
                "Historico da notificacao nao pode ser recalculado apos a alteracao administrativa");
    }

    // ---------------------------------------------------------------------------------------
    // 2. Desativacao de Person
    // ---------------------------------------------------------------------------------------

    @RepeatedTest(5)
    void shouldExcludeTargetAdminWhenPersonDeactivationConfirmsBeforeNotificationSend() throws Exception {
        Scenario scenario = buildScenario("K2A");

        runInOrder(
                () -> deactivatePerson(scenario.actingAdmin(), scenario.targetAdmin()),
                () -> createConflict(scenario)
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification);
        assertRecipientAbsent(notification.id(), scenario.targetAdmin().accountId(),
                "Pessoa desativada antes da resolucao de destinatarios nao pode ser recipient");
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertExactlyOneActiveNotification(scenario);
        assertNoDuplicateRecipients(notification.id());
        assertFalse(personRepository.findById(scenario.targetAdmin().personId()).orElseThrow().isActive());
    }

    @RepeatedTest(5)
    void shouldKeepTargetAdminAsHistoricalRecipientWhenSendConfirmsBeforePersonDeactivation() throws Exception {
        Scenario scenario = buildScenario("K2B");

        runInOrder(
                () -> createConflict(scenario),
                () -> deactivatePerson(scenario.actingAdmin(), scenario.targetAdmin())
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification);
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.targetAdmin());
        assertEquals(2, countRecipients(notification.id()));
        assertExactlyOneActiveNotification(scenario);
        assertFalse(personRepository.findById(scenario.targetAdmin().personId()).orElseThrow().isActive(),
                "A alteracao administrativa, ocorrendo depois, ainda deve ter sucesso");
        assertEquals(2, countRecipients(notification.id()),
                "Historico da notificacao nao pode ser recalculado apos a alteracao administrativa");
    }

    // ---------------------------------------------------------------------------------------
    // 3. Mudanca de role ROLE_ADMIN -> ROLE_OPERATOR
    // ---------------------------------------------------------------------------------------

    @RepeatedTest(5)
    void shouldExcludeTargetAdminWhenRoleChangeConfirmsBeforeNotificationSend() throws Exception {
        Scenario scenario = buildScenario("K3A");

        runInOrder(
                () -> demoteToOperator(scenario.actingAdmin(), scenario.targetAdmin()),
                () -> createConflict(scenario)
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification);
        assertRecipientAbsent(notification.id(), scenario.targetAdmin().accountId(),
                "Administrador rebaixado a ROLE_OPERATOR antes da resolucao de destinatarios nao pode ser recipient de audience ADMIN");
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertExactlyOneActiveNotification(scenario);
        assertNoDuplicateRecipients(notification.id());
        assertTrue(hasRole(scenario.targetAdmin().accountId(), "ROLE_OPERATOR"));
        assertFalse(hasRole(scenario.targetAdmin().accountId(), "ROLE_ADMIN"));
    }

    @RepeatedTest(5)
    void shouldKeepTargetAdminAsHistoricalRecipientWhenSendConfirmsBeforeRoleChange() throws Exception {
        Scenario scenario = buildScenario("K3B");

        runInOrder(
                () -> createConflict(scenario),
                () -> demoteToOperator(scenario.actingAdmin(), scenario.targetAdmin())
        );

        NotificationRow notification = findNotification(scenario);
        assertNotNull(notification);
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.actingAdmin());
        assertRecipientPresentWithValidSnapshot(notification.id(), scenario.targetAdmin());
        assertEquals(2, countRecipients(notification.id()));
        assertExactlyOneActiveNotification(scenario);
        assertTrue(hasRole(scenario.targetAdmin().accountId(), "ROLE_OPERATOR"),
                "A alteracao administrativa, ocorrendo depois, ainda deve ter sucesso");
        assertEquals(2, countRecipients(notification.id()),
                "Historico da notificacao nao pode ser recalculado apos a alteracao administrativa");
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /**
     * Executa duas operacoes reais em threads/conexoes separadas, forcando a ordem de confirmacao
     * (commit) via CountDownLatch: a primeira roda ate concluir e sinaliza; so entao a segunda
     * comeca. Cada uma roda por completo em sua propria transacao gerenciada pelo service real
     * chamado (nunca sleep como mecanismo de sincronizacao).
     */
    private void runInOrder(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstDone = new CountDownLatch(1);
        try {
            Future<?> firstFuture = executor.submit(() -> {
                try {
                    first.run();
                } finally {
                    firstDone.countDown();
                }
                return null;
            });
            Future<?> secondFuture = executor.submit(() -> {
                assertTrue(firstDone.await(30, TimeUnit.SECONDS), "Primeira operacao nao concluiu a tempo");
                second.run();
                return null;
            });
            firstFuture.get(30, TimeUnit.SECONDS);
            secondFuture.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void disableAccount(Fixture actingAdmin, Fixture targetAdmin) {
        runAuthenticatedAs(actingAdmin, () -> {
            UserAccountEnabledRequestDTO request = new UserAccountEnabledRequestDTO();
            request.setEnabled(false);
            userAccountLifecycleService.updateAccountEnabled(targetAdmin.personId(), request);
        });
    }

    private void deactivatePerson(Fixture actingAdmin, Fixture targetAdmin) {
        runAuthenticatedAs(actingAdmin, () -> {
            PersonActiveRequestDTO request = new PersonActiveRequestDTO();
            request.setActive(false);
            userAccountLifecycleService.updatePersonActive(targetAdmin.personId(), request);
        });
    }

    private void demoteToOperator(Fixture actingAdmin, Fixture targetAdmin) {
        runAuthenticatedAs(actingAdmin, () -> userAccountLifecycleService.updateRole(targetAdmin.personId(), "ROLE_OPERATOR"));
    }

    private void runAuthenticatedAs(Fixture actingAdmin, Runnable action) {
        try {
            authenticateAsAdmin(actingAdmin);
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAsAdmin(Fixture admin) {
        Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(admin.accountId(), admin.personId(), "admin-" + admin.personId(), 0L, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities));
    }

    private void createConflict(Scenario scenario) {
        personUnavailabilityService.create(scenario.reader().personId(),
                new PersonUnavailabilityRequestDTO(scenario.start(), scenario.end(), null));
    }

    private Scenario buildScenario(String tag) {
        // R__load_test_fixtures.sql semeia contas com ROLE_ADMIN elegivel (ex.: pessoa 14, unica
        // role); neutraliza-las evita que a resolucao de destinatarios ADMIN inclua "ruido" de
        // fixture nas contagens exatas deste teste (a primeira repeticao de cada metodo via um
        // banco MySQL isolado recem-criado ainda enxerga essas linhas antes do primeiro cleanup).
        jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE");

        Fixture actingAdmin = createAccount(tag + " Acting Admin", "ROLE_ADMIN", null);
        Fixture targetAdmin = createAccount(tag + " Target Admin", "ROLE_ADMIN", null);
        Fixture reader = createAccount(tag + " Reader", "ROLE_OPERATOR", MinistryType.READER);
        Long locationId = createLocation(tag + " Location");
        LocalDateTime start = nextHour().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(tag + " Event " + UUID.randomUUID());
        request.setStartAt(start);
        request.setEndAt(end);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(List.of(reader.personId()));
        Long eventId = celebrationEventService.createEventWithScale(request).getEventId();

        return new Scenario(actingAdmin, targetAdmin, reader, eventId, start, end);
    }

    private NotificationRow findNotification(Scenario scenario) {
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + scenario.eventId() + ":" + scenario.reader().personId();
        List<NotificationRow> rows = jdbcTemplate.query(
                "SELECT id FROM tb_notification WHERE active_source_key = ?",
                (rs, rowNum) -> new NotificationRow(rs.getLong("id")), activeSourceKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void assertExactlyOneActiveNotification(Scenario scenario) {
        String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + scenario.eventId() + ":" + scenario.reader().personId();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_notification WHERE active_source_key = ?", Integer.class, activeSourceKey);
        assertEquals(1, count, "Deve haver exatamente uma notificacao ativa para a identidade");
    }

    private void assertNoDuplicateRecipients(Long notificationId) {
        Integer distinctAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_account_id) FROM tb_notification_recipient WHERE notification_id = ?",
                Integer.class, notificationId);
        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?", Integer.class, notificationId);
        assertEquals(distinctAccounts, totalRows, "Nenhum recipient duplicado para a mesma conta");
    }

    private void assertRecipientAbsent(Long notificationId, Long accountId, String message) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ? AND user_account_id = ?",
                Integer.class, notificationId, accountId);
        assertEquals(0, count, message);
    }

    private void assertRecipientPresentWithValidSnapshot(Long notificationId, Fixture admin) {
        List<String> snapshots = jdbcTemplate.query(
                "SELECT recipient_name_snapshot FROM tb_notification_recipient WHERE notification_id = ? AND user_account_id = ?",
                (rs, rowNum) -> rs.getString("recipient_name_snapshot"), notificationId, admin.accountId());
        assertEquals(1, snapshots.size(), "Destinatario esperado ausente ou duplicado");
        assertNotNull(snapshots.get(0));
        assertFalse(snapshots.get(0).isBlank(), "Snapshot invalido (vazio) persistido");
    }

    private long countRecipients(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?", Integer.class, notificationId);
    }

    private boolean hasRole(Long accountId, String authority) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tb_user_account_role uar
                JOIN tb_role r ON r.id = uar.role_id
                WHERE uar.user_account_id = ? AND r.authority = ?
                """, Integer.class, accountId, authority);
        return count != null && count > 0;
    }

    private LocalDateTime nextHour() {
        return LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
    }

    private Long createLocation(String name) {
        return locationRepository.saveAndFlush(new Location(null, name + " " + UUID.randomUUID(), "Endereco")).getId();
    }

    private Fixture createAccount(String name, String authority, MinistryType ministryType) {
        Person person = new Person(name, uniquePhoneNumber(), BIRTHDAY);
        person.activate();
        Person savedPerson = personRepository.saveAndFlush(person);

        if (ministryType != null) {
            personMinistryRepository.saveAndFlush(new PersonMinistry(savedPerson, ministryType));
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByAuthority(authority).orElseThrow();
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));

        return new Fixture(savedPerson.getId(), savedAccount.getId());
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3492" + String.format("%07d", suffix);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record NotificationRow(Long id) {
    }

    private record Fixture(Long personId, Long accountId) {
    }

    private record Scenario(Fixture actingAdmin, Fixture targetAdmin, Fixture reader, Long eventId, LocalDateTime start, LocalDateTime end) {
    }
}
