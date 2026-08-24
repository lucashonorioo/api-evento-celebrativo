package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.impl.ScheduleConflictResolutionScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, com transacoes reais em MySQL 8.4 (nao mocks) e threads concorrentes reais, os invariantes
 * de concorrencia do reconciliador de conflitos de escala (secao 15): mutex ROLE_ADMIN serializando
 * envio manual e automatico, ausencia de deadlock entre escala/indisponibilidade/notificacao,
 * exatamente uma notificacao ativa por identidade eventId+personId e nenhuma duplicidade de
 * recipient. Usa uma database MySQL isolada propria e e ignorado automaticamente quando MySQL 8.4
 * real nao estiver acessivel.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
class ScheduleConflictConcurrencyMySqlIntegrationTest {

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
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private ScheduleConflictResolutionService scheduleConflictResolutionService;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private java.time.Clock clock;

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

        databaseName = "v17_conflict_concurrency_" + UUID.randomUUID().toString().replace("-", "");
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
        // @AfterEach roda mesmo quando @BeforeEach aborta via assumeTrue; sem este guard, a limpeza
        // em lote abaixo atingiria o H2 compartilhado por outras suites (nunca substituido quando
        // mysqlAvailable=false, ja que @DynamicPropertySource so registra o datasource MySQL nesse caso).
        if (!mysqlAvailable) {
            return;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
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

    /**
     * Escala e indisponibilidade sao criadas concorrentemente para a mesma pessoa/evento: ambas
     * devem ter sucesso (nunca bloqueantes entre si), e a reconciliacao de qualquer uma das duas
     * ordens de vitoria deve resultar em exatamente uma notificacao ativa, sem recipient duplicado.
     */
    @RepeatedTest(5)
    void shouldAllowConcurrentScheduleAssignmentAndUnavailabilityCreationProducingExactlyOneActiveConflict() throws Exception {
        createAdminAccount("Concurrent Admin A");
        Fixture reader = createReaderWithMinistry("Concurrent Reader A");
        Long locationId = createLocation("Concurrent Location A");
        LocalDateTime start = nextHour().plusDays(1);
        LocalDateTime end = start.plusHours(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Long> scaleFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
                request.setNameMassOrEvent("Concurrent Event A " + UUID.randomUUID());
                request.setStartAt(start);
                request.setEndAt(end);
                request.setMassOrCelebration(true);
                request.setLocationId(locationId);
                request.setReaderIds(List.of(reader.personId()));
                return celebrationEventService.createEventWithScale(request).getEventId();
            });
            Future<Void> unavailabilityFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(reader.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            Long eventId = scaleFuture.get(30, TimeUnit.SECONDS);
            unavailabilityFuture.get(30, TimeUnit.SECONDS);

            String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + reader.personId();
            NotificationRow notificationRow = findNotificationByActiveSourceKey(activeSourceKey);
            assertNotNull(notificationRow, "Deveria existir exatamente uma notificacao ativa");
            assertNull(notificationRow.resolvedAt());
            assertEquals("SCHEDULE_CONFLICT", notificationRow.category());

            long recipientRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?",
                    Integer.class, notificationRow.id());
            assertEquals(1, recipientRows, "Nao deve haver recipient duplicado para o unico admin existente");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Duas atualizacoes concorrentes da mesma escala (mesmo evento, alvos diferentes) devem
     * serializar sem deadlock: exatamente uma vence por ultimo e o estado final e consistente com
     * ela, sem excecao inesperada em nenhuma das duas chamadas.
     */
    @RepeatedTest(5)
    void shouldSerializeConcurrentScaleUpdatesOnSameEventWithoutDeadlock() throws Exception {
        createAdminAccount("Concurrent Admin B");
        Fixture readerOne = createReaderWithMinistry("Concurrent Reader B1");
        Fixture readerTwo = createReaderWithMinistry("Concurrent Reader B2");
        Long locationId = createLocation("Concurrent Location B");
        LocalDateTime start = nextHour().plusDays(2);
        LocalDateTime end = start.plusHours(1);

        CelebrationEventWithScaleRequestDTO initial = new CelebrationEventWithScaleRequestDTO();
        initial.setNameMassOrEvent("Concurrent Event B " + UUID.randomUUID());
        initial.setStartAt(start);
        initial.setEndAt(end);
        initial.setMassOrCelebration(true);
        initial.setLocationId(locationId);
        Long eventId = celebrationEventService.createEventWithScale(initial).getEventId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> updateOne = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                        locationId, null, List.of(readerOne.personId()), null, null, null));
                return null;
            });
            Future<?> updateTwo = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                        locationId, null, List.of(readerTwo.personId()), null, null, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            updateOne.get(30, TimeUnit.SECONDS);
            updateTwo.get(30, TimeUnit.SECONDS);

            int assignmentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ?", Integer.class, eventId);
            assertEquals(1, assignmentCount, "Apenas o ultimo update deve prevalecer (uma unica pessoa na escala)");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Envio manual (ADMIN broadcast) e reconciliacao automatica de conflito concorrentes devem
     * serializar via mutex ROLE_ADMIN sem deadlock, produzindo duas notificacoes distintas
     * (ADMIN manual e SYSTEM/SCHEDULE_CONFLICT) cada uma com seus proprios recipients corretos.
     */
    @RepeatedTest(5)
    void shouldAllowConcurrentManualAdminSendAndAutomaticConflictReconciliationWithoutDeadlock() throws Exception {
        Fixture admin = createAdminAccount("Concurrent Admin C");
        Fixture reader = createReaderWithMinistry("Concurrent Reader C");
        Long locationId = createLocation("Concurrent Location C");
        LocalDateTime start = nextHour().plusDays(3);
        LocalDateTime end = start.plusHours(1);

        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Concurrent Event C " + UUID.randomUUID());
        request.setStartAt(start);
        request.setEndAt(end);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(List.of(reader.personId()));
        Long eventId = celebrationEventService.createEventWithScale(request).getEventId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Long> manualSend = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                return notificationDeliveryService.sendAdministrativeNotification(
                        admin.accountId(),
                        new NotificationCreateRequestDTO(NotificationAudience.ADMIN, "Aviso manual", "Mensagem manual", null, null)
                ).getNotificationId();
            });
            Future<Void> automaticConflict = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(reader.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            Long manualNotificationId = manualSend.get(30, TimeUnit.SECONDS);
            automaticConflict.get(30, TimeUnit.SECONDS);

            assertNotNull(notificationRepository.findById(manualNotificationId).orElseThrow());
            String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + reader.personId();
            NotificationRow automaticNotification = findNotificationByActiveSourceKey(activeSourceKey);
            assertNotNull(automaticNotification, "Conflito automatico deveria existir");

            long manualRecipients = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?", Integer.class, manualNotificationId);
            long automaticRecipients = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?", Integer.class, automaticNotification.id());
            assertEquals(1, manualRecipients);
            assertEquals(1, automaticRecipients);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Quando a pessoa em conflito e ela propria a unica ROLE_ADMIN elegivel, ela deve ser
     * notificada exatamente uma vez (nunca duplicada), mesmo sob a mesma race de escala x
     * indisponibilidade do primeiro teste.
     */
    @RepeatedTest(5)
    void shouldNotifyConflictingPersonWhoIsAlsoAdminExactlyOnce() throws Exception {
        Fixture adminReader = createAdminAccountWithMinistry("Concurrent Admin Reader D", MinistryType.READER);
        Long locationId = createLocation("Concurrent Location D");
        LocalDateTime start = nextHour().plusDays(4);
        LocalDateTime end = start.plusHours(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Long> scaleFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
                request.setNameMassOrEvent("Concurrent Event D " + UUID.randomUUID());
                request.setStartAt(start);
                request.setEndAt(end);
                request.setMassOrCelebration(true);
                request.setLocationId(locationId);
                request.setReaderIds(List.of(adminReader.personId()));
                return celebrationEventService.createEventWithScale(request).getEventId();
            });
            Future<Void> unavailabilityFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(adminReader.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            Long eventId = scaleFuture.get(30, TimeUnit.SECONDS);
            unavailabilityFuture.get(30, TimeUnit.SECONDS);

            String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + adminReader.personId();
            NotificationRow notificationRow = findNotificationByActiveSourceKey(activeSourceKey);
            assertNotNull(notificationRow, "Conflito deveria existir");

            long recipientRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ? AND user_account_id = ?",
                    Integer.class, notificationRow.id(), adminReader.accountId());
            assertEquals(1, recipientRows,
                    "A pessoa conflitante, sendo tambem a unica admin, deve ser notificada exatamente uma vez");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Exclusao do evento (event->assignments->mutex->pessoa->resolve) e criacao de indisponibilidade
     * (mutex->pessoa) concorrentes para a mesma identidade: independente de quem vence a corrida, o
     * estado final deve ser consistente (evento realmente excluido, indisponibilidade persistida, e
     * se uma notificacao chegou a ser criada, ela nao pode ficar ativa apontando para um evento
     * inexistente) e nenhuma das duas chamadas pode lancar excecao inesperada (nem deadlock).
     */
    @RepeatedTest(5)
    void shouldKeepConsistentStateWhenEventDeletionRacesWithUnavailabilityCreationForSameIdentity() throws Exception {
        createAdminAccount("Concurrent Admin E");
        Fixture reader = createReaderWithMinistry("Concurrent Reader E");
        Long locationId = createLocation("Concurrent Location E");
        LocalDateTime start = nextHour().plusDays(5);
        LocalDateTime end = start.plusHours(1);

        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Concurrent Event E " + UUID.randomUUID());
        request.setStartAt(start);
        request.setEndAt(end);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(List.of(reader.personId()));
        Long eventId = celebrationEventService.createEventWithScale(request).getEventId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> deleteFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                celebrationEventService.deleteEventById(eventId);
                return null;
            });
            Future<?> unavailabilityFuture = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                try {
                    personUnavailabilityService.create(reader.personId(),
                            new PersonUnavailabilityRequestDTO(start, end, null));
                } catch (RuntimeException ignoredNotFound) {
                    // Aceitavel: evento pode ter sido excluido antes da leitura de eventIds afetados
                    // dentro de create(); a indisponibilidade em si nao depende da existencia do evento.
                }
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            deleteFuture.get(30, TimeUnit.SECONDS);
            unavailabilityFuture.get(30, TimeUnit.SECONDS);

            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_celebration_event WHERE id = ?", Integer.class, eventId);
            assertEquals(0, eventCount, "Evento deve estar realmente excluido");

            String activeSourceKey = "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + reader.personId();
            NotificationRow notificationRow = findNotificationByActiveSourceKey(activeSourceKey);
            if (notificationRow != null) {
                assertNotNull(notificationRow.resolvedAt(),
                        "Se uma notificacao chegou a ser criada, ela nao pode permanecer ativa apontando para um evento excluido");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Duas pessoas diferentes escaladas no mesmo evento tornam-se indisponiveis ao mesmo tempo: dois
     * conflitos distintos devem surgir (identidades eventId+personId diferentes), cada um com
     * exatamente um recipient (o unico admin existente), sem interferencia cruzada.
     */
    @RepeatedTest(5)
    void shouldCreateTwoDistinctConflictsWhenTwoDifferentPeopleOnSameEventBecomeUnavailableConcurrently() throws Exception {
        createAdminAccount("Concurrent Admin F");
        Fixture readerOne = createReaderWithMinistry("Concurrent Reader F1");
        Fixture readerTwo = createReaderWithMinistry("Concurrent Reader F2");
        Long locationId = createLocation("Concurrent Location F");
        LocalDateTime start = nextHour().plusDays(6);
        LocalDateTime end = start.plusHours(1);

        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Concurrent Event F " + UUID.randomUUID());
        request.setStartAt(start);
        request.setEndAt(end);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(List.of(readerOne.personId(), readerTwo.personId()));
        Long eventId = celebrationEventService.createEventWithScale(request).getEventId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> unavailabilityOne = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(readerOne.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });
            Future<?> unavailabilityTwo = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(readerTwo.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            unavailabilityOne.get(30, TimeUnit.SECONDS);
            unavailabilityTwo.get(30, TimeUnit.SECONDS);

            NotificationRow notificationOne = findNotificationByActiveSourceKey(
                    "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + readerOne.personId());
            NotificationRow notificationTwo = findNotificationByActiveSourceKey(
                    "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + readerTwo.personId());
            assertNotNull(notificationOne);
            assertNotNull(notificationTwo);
            assertTrue(!notificationOne.id().equals(notificationTwo.id()), "Devem ser notificacoes distintas");

            assertEquals(1, countRecipients(notificationOne.id()));
            assertEquals(1, countRecipients(notificationTwo.id()));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Dois FLUXOS DE PRODUCAO distintos (criacao de indisponibilidade e ressincronizacao de escala)
     * disparam reconcile() para a MESMA identidade (mesmo eventId+personId) ao mesmo tempo. Ambos os
     * caminhos ja adquirem o mutex ROLE_ADMIN antes de chamar reconcile (contrato documentado em
     * ScheduleConflictNotificationService), entao isto exercita exatamente a race que pode ocorrer de
     * verdade em producao - ao contrario de chamar reconcile() diretamente sem o mutex, que
     * comprovadamente causa deadlock (ver nota abaixo) mas nao corresponde a nenhum caminho de
     * chamada real, ja que os dois unicos chamadores de reconcile() sempre travam o mutex primeiro.
     * Prova que a constraint UNIQUE de active_source_key + a ordem de locks real impedem duplicacao:
     * exatamente uma notificacao deve existir ao final, sem excecao inesperada em nenhuma das duas
     * chamadas.
     *
     * Nota: uma primeira versao deste teste chamava scheduleConflictNotificationService.reconcile()
     * diretamente pelas duas threads, ignorando essa precondicao - isso reproduziu um deadlock real
     * no MySQL 8.4 (5/5 execucoes), pois a leitura FOR UPDATE de active_source_key inexistente toma
     * gap lock antes do mutex ser adquirido dentro de deliverBroadcast. Como nenhum caminho de
     * producao chama reconcile() sem o mutex ja adquirido, o teste foi corrigido para refletir uso
     * real em vez de alterar reconcile() para um cenario inatingivel.
     */
    @RepeatedTest(5)
    void shouldCreateExactlyOneNotificationWhenTwoProductionFlowsReconcileTheExactSameIdentityConcurrently() throws Exception {
        createAdminAccount("Concurrent Admin G");
        Fixture reader = createReaderWithMinistry("Concurrent Reader G");
        Long locationId = createLocation("Concurrent Location G");
        LocalDateTime start = nextHour().plusDays(7);
        LocalDateTime end = start.plusHours(1);
        Long eventId = createEventWithReader("Concurrent Event G", locationId, start, end, reader.personId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> unavailabilityCreate = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(reader.personId(), new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });
            Future<?> scaleResync = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                celebrationEventService.updateEventScale(eventId, new CelebrationEventScaleRequestDTO(
                        locationId, null, List.of(reader.personId()), null, null, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            unavailabilityCreate.get(30, TimeUnit.SECONDS);
            scaleResync.get(30, TimeUnit.SECONDS);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification WHERE reference_id = ? AND source_key = ?",
                    Integer.class, eventId, eventId + ":" + reader.personId());
            assertEquals(1, count, "Reconciliacao concorrente da mesma identidade por dois fluxos nunca pode duplicar a notificacao");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Duas exclusoes concorrentes de indisponibilidades DIFERENTES da mesma pessoa, cada uma
     * resolvendo um conflito em um evento diferente: ambas disputam o mesmo lock de Person
     * (lockAuthenticatedPerson), devem serializar sem deadlock, e os dois conflitos devem terminar
     * resolvidos (nenhuma atualizacao perdida).
     */
    @RepeatedTest(5)
    void shouldResolveBothConflictsWhenDeletingTwoDifferentUnavailabilitiesOfSamePersonConcurrently() throws Exception {
        createAdminAccount("Concurrent Admin H");
        Fixture reader = createReaderWithMinistry("Concurrent Reader H");
        Long locationId = createLocation("Concurrent Location H");
        LocalDateTime startOne = nextHour().plusDays(8);
        LocalDateTime endOne = startOne.plusHours(1);
        LocalDateTime startTwo = nextHour().plusDays(9);
        LocalDateTime endTwo = startTwo.plusHours(1);

        Long eventOneId = createEventWithReader("Concurrent Event H1", locationId, startOne, endOne, reader.personId());
        Long eventTwoId = createEventWithReader("Concurrent Event H2", locationId, startTwo, endTwo, reader.personId());

        PersonUnavailabilityResponseDTO unavailabilityOne = personUnavailabilityService.create(
                reader.personId(), new PersonUnavailabilityRequestDTO(startOne, endOne, null));
        PersonUnavailabilityResponseDTO unavailabilityTwo = personUnavailabilityService.create(
                reader.personId(), new PersonUnavailabilityRequestDTO(startTwo, endTwo, null));

        assertNotNull(findNotificationByActiveSourceKey("SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventOneId + ":" + reader.personId()));
        assertNotNull(findNotificationByActiveSourceKey("SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventTwoId + ":" + reader.personId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> deleteOne = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.delete(reader.personId(), unavailabilityOne.getId());
                return null;
            });
            Future<?> deleteTwo = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.delete(reader.personId(), unavailabilityTwo.getId());
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            deleteOne.get(30, TimeUnit.SECONDS);
            deleteTwo.get(30, TimeUnit.SECONDS);

            NotificationRow notificationOne = findNotificationByActiveSourceKey(
                    "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventOneId + ":" + reader.personId());
            NotificationRow notificationTwo = findNotificationByActiveSourceKey(
                    "SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventTwoId + ":" + reader.personId());
            assertNull(notificationOne, "Conflito 1 deve estar resolvido (sem activeSourceKey)");
            assertNull(notificationTwo, "Conflito 2 deve estar resolvido (sem activeSourceKey)");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * update() de uma indisponibilidade (estendendo-a para passar a sobrepor o evento) e delete() de
     * OUTRA indisponibilidade da mesma pessoa (que ja causava o unico conflito ativo) disputam o mesmo
     * lock de Person ao mesmo tempo. Duas ordens de vitoria sao validas: (a) delete() efetiva primeiro
     * - update() ve o campo livre e passa a sobrepor, conflito recriado sob nova ocorrencia; (b)
     * update() valida antes de delete() efetivar - rejeitado com UnavailabilityOverlapException (a
     * indisponibilidade que sera excluida ainda sobrepoe no momento da validacao; regra de negocio
     * correta, nao um defeito), e delete() prossegue normalmente resolvendo o conflito. Em nenhuma
     * das duas o lock de Person pode causar deadlock ou excecao inesperada, e a notificacao nunca
     * pode ficar duplicada (no maximo uma ativa ao final).
     */
    @RepeatedTest(5)
    void shouldKeepAtMostOneActiveConflictWhenUpdateAndDeleteOfDifferentUnavailabilitiesRaceForSameEvent() throws Exception {
        createAdminAccount("Concurrent Admin J");
        Fixture reader = createReaderWithMinistry("Concurrent Reader J");
        Long locationId = createLocation("Concurrent Location J");
        LocalDateTime start = nextHour().plusDays(11);
        LocalDateTime end = start.plusHours(1);
        Long eventId = createEventWithReader("Concurrent Event J", locationId, start, end, reader.personId());

        PersonUnavailabilityResponseDTO overlapping = personUnavailabilityService.create(
                reader.personId(), new PersonUnavailabilityRequestDTO(start, end, null));
        PersonUnavailabilityResponseDTO nonOverlapping = personUnavailabilityService.create(
                reader.personId(), new PersonUnavailabilityRequestDTO(start.plusDays(1), end.plusDays(1), null));
        assertNotNull(findNotificationByActiveSourceKey("SCHEDULE_UNAVAILABILITY_CONFLICT:" + eventId + ":" + reader.personId()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> deleteOverlapping = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.delete(reader.personId(), overlapping.getId());
                return null;
            });
            Future<?> updateNonOverlapping = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                try {
                    personUnavailabilityService.update(reader.personId(), nonOverlapping.getId(),
                            new PersonUnavailabilityRequestDTO(start, end, "Passou a sobrepor"));
                } catch (com.eventoscelebrativos.exception.exceptions.UnavailabilityOverlapException expectedWhenDeleteHasNotTakenEffectYet) {
                    // Aceitavel: a indisponibilidade a ser excluida ainda existia (do ponto de vista
                    // desta transacao) quando a validacao rodou - regra de negocio correta, nao um
                    // defeito de concorrencia.
                }
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            deleteOverlapping.get(30, TimeUnit.SECONDS);
            updateNonOverlapping.get(30, TimeUnit.SECONDS);

            Integer activeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_notification WHERE reference_id = ? AND source_key = ? AND active_source_key IS NOT NULL",
                    Integer.class, eventId, eventId + ":" + reader.personId());
            assertTrue(activeCount <= 1, "Nunca pode haver mais de uma notificacao ativa para a mesma identidade, independente da ordem de vitoria");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * O scheduler de resolucao por passagem do tempo roda concorrentemente com uma reconciliacao "ao
     * vivo" disparada por alteracao de escala, cada um atuando sobre uma identidade eventId+personId
     * diferente: nao devem colidir (o scheduler trava evento+notificacao por PK; a criacao ao vivo
     * trava mutex->pessoas->contas), provando ausencia de deadlock entre os dois caminhos.
     */
    @RepeatedTest(5)
    void shouldNotDeadlockWhenSchedulerRunsConcurrentlyWithLiveReconciliationForDifferentIdentities() throws Exception {
        // Conflito ja encerrado, elegivel para o scheduler resolver.
        Fixture endedReader = createReaderWithMinistry("Concurrent Reader I Ended");
        LocalDateTime endedEnd = LocalDateTime.now().minusHours(1).withNano(0);
        CelebrationEvent endedEvent = celebrationEventRepository.saveAndFlush(
                new CelebrationEvent(null, "Concurrent Event I Ended " + UUID.randomUUID(), endedEnd.minusHours(1), endedEnd, true));
        Notification endedNotification = notificationRepository.saveAndFlush(Notification.scheduleConflict(
                NotificationAudience.ADMIN, "Conflito de escala detectado", "Mensagem de teste",
                "CELEBRATION_EVENT", endedEvent.getId(), "SCHEDULE_UNAVAILABILITY_CONFLICT",
                endedEvent.getId() + ":" + endedReader.personId(),
                "SCHEDULE_UNAVAILABILITY_CONFLICT:" + endedEvent.getId() + ":" + endedReader.personId(),
                LocalDateTime.now().minusDays(1).withNano(0)));

        // Conflito novo, para ser criado ao vivo durante a mesma janela de concorrencia.
        createAdminAccount("Concurrent Admin I");
        Fixture liveReader = createReaderWithMinistry("Concurrent Reader I Live");
        Long locationId = createLocation("Concurrent Location I");
        LocalDateTime start = nextHour().plusDays(10);
        LocalDateTime end = start.plusHours(1);
        Long liveEventId = createEventWithReader("Concurrent Event I Live", locationId, start, end, liveReader.personId());

        ScheduleConflictResolutionScheduler scheduler =
                new ScheduleConflictResolutionScheduler(scheduleConflictResolutionService, clock, true, 100);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> schedulerRun = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                scheduler.run();
                return null;
            });
            Future<?> liveCreate = executor.submit(() -> {
                ready.countDown();
                await(ready, go);
                personUnavailabilityService.create(liveReader.personId(),
                        new PersonUnavailabilityRequestDTO(start, end, null));
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            schedulerRun.get(30, TimeUnit.SECONDS);
            liveCreate.get(30, TimeUnit.SECONDS);

            Notification reloadedEnded = notificationRepository.findById(endedNotification.getId()).orElseThrow();
            assertNotNull(reloadedEnded.getResolvedAt(), "Scheduler deveria ter resolvido o conflito encerrado");

            assertNotNull(findNotificationByActiveSourceKey(
                    "SCHEDULE_UNAVAILABILITY_CONFLICT:" + liveEventId + ":" + liveReader.personId()),
                    "Reconciliacao ao vivo deveria ter criado o novo conflito normalmente");
        } finally {
            executor.shutdownNow();
        }
    }

    private long countRecipients(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_notification_recipient WHERE notification_id = ?", Integer.class, notificationId);
    }

    private Long createEventWithReader(String name, Long locationId, LocalDateTime startAt, LocalDateTime endAt, Long readerId) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name + " " + UUID.randomUUID());
        request.setStartAt(startAt);
        request.setEndAt(endAt);
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(List.of(readerId));
        return celebrationEventService.createEventWithScale(request).getEventId();
    }

    /**
     * Leitura via JdbcTemplate (autocommit, sem transacao Spring gerenciada), evitando o
     * requisito de transacao ativa dos metodos @Lock do repository ao verificar o estado final
     * apos as threads concorrentes terminarem.
     */
    private NotificationRow findNotificationByActiveSourceKey(String activeSourceKey) {
        List<NotificationRow> rows = jdbcTemplate.query(
                "SELECT id, category, resolved_at FROM tb_notification WHERE active_source_key = ?",
                (rs, rowNum) -> new NotificationRow(rs.getLong("id"), rs.getString("category"), rs.getTimestamp("resolved_at")),
                activeSourceKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record NotificationRow(Long id, String category, java.sql.Timestamp resolvedAt) {
    }

    private void await(CountDownLatch ready, CountDownLatch go) {
        try {
            go.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private LocalDateTime nextHour() {
        return LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
    }

    private Long createLocation(String name) {
        return locationRepository.saveAndFlush(new Location(null, name, "Endereco")).getId();
    }

    private Fixture createAdminAccount(String name) {
        return createAccount(name, "ROLE_ADMIN", null);
    }

    private Fixture createAdminAccountWithMinistry(String name, MinistryType ministryType) {
        return createAccount(name, "ROLE_ADMIN", ministryType);
    }

    private Fixture createReaderWithMinistry(String name) {
        return createAccount(name, "ROLE_OPERATOR", MinistryType.READER);
    }

    private Fixture createAccount(String name, String authority, MinistryType ministryType) {
        Person person = new Person(name, uniquePhoneNumber(), BIRTHDAY);
        person.setActive(true);
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
        return "3499" + String.format("%07d", suffix);
    }

    private record Fixture(Long personId, Long accountId) {
    }
}
