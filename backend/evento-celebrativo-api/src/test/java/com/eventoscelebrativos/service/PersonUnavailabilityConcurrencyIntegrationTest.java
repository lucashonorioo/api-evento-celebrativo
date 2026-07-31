package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ErrorResponseException;
import com.eventoscelebrativos.exception.exceptions.PersonUnavailableForEventException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prova, com transacoes reais e threads concorrentes, que o invariante "nao pode existir
 * EventAssignment para uma pessoa em uma data coberta por PersonUnavailability dessa pessoa"
 * se mantem sob concorrencia MySQL 8.4 real, independentemente de qual operacao vence a corrida.
 * Cada execucao usa uma database isolada. Sem MySQL 8.4 acessivel, os seis testes sao ignorados.
 */
@SpringBootTest
@Import(PersonUnavailabilityConcurrencyIntegrationTest.FixedClockConfig.class)
class PersonUnavailabilityConcurrencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");
    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String databaseName;
    private static boolean mysqlAvailable;

    @BeforeAll
    static void provisionIsolatedMySqlDatabase() throws SQLException {
        host = System.getProperty("mysql.validation.host", "localhost");
        port = System.getProperty("mysql.validation.port", "3307");
        username = System.getProperty("mysql.validation.username", "root");
        password = System.getProperty(
                "mysql.validation.password",
                System.getenv("MYSQL_VALIDATION_PASSWORD")
        );

        if (password == null || password.isBlank()) {
            mysqlAvailable = false;
            return;
        }

        try (Connection connection = DriverManager.getConnection(bootstrapUrl(), username, password);
             Statement statement = connection.createStatement()) {
            String version = queryVersion(statement);
            mysqlAvailable = connection.isValid(3) && version.startsWith("8.4.");
            if (!mysqlAvailable) {
                return;
            }
            databaseName = "v11_concurrency_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        } catch (SQLException exception) {
            mysqlAvailable = false;
        }
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        if (!mysqlAvailable) {
            return;
        }
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo"
        );
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("app.time-zone", () -> "America/Sao_Paulo");
    }

    @AfterAll
    static void dropIsolatedMySqlDatabase() {
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
    void requireMySql84() {
        Assumptions.assumeTrue(mysqlAvailable, "MySQL 8.4 real nao acessivel; teste ignorado.");
    }

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private PersonUnavailabilityRepository personUnavailabilityRepository;

    @Autowired
    private EventParticipationResponseService eventParticipationResponseService;

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Autowired
    private ScheduleUnavailabilityConflictService scheduleUnavailabilityConflictService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cleanupPersonIdA;
    private Long cleanupPersonIdB;
    private Long cleanupEventId;

    @AfterEach
    void cleanup() {
        if (cleanupEventId != null) {
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", cleanupEventId);
            jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", cleanupEventId);
            jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", cleanupEventId);
        }
        cleanupPerson(cleanupPersonIdA);
        cleanupPerson(cleanupPersonIdB);
        cleanupPersonIdA = null;
        cleanupPersonIdB = null;
        cleanupEventId = null;
    }

    @Test
    void shouldNotPersistTwoOverlappingUnavailabilitiesCreatedConcurrently() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Overlap Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate base = LocalDate.of(2026, 10, 1);
        PersonUnavailabilityRequestDTO first = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(3)), null);
        PersonUnavailabilityRequestDTO second = new PersonUnavailabilityRequestDTO(dayStart(base.plusDays(1)), dayEndExclusive(base.plusDays(5)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(phone, first),
                () -> personUnavailabilityService.create(phone, second),
                successes,
                conflicts
        );

        assertEquals(1, successes.get(), "Apenas uma das duas indisponibilidades sobrepostas deve ser persistida");
        assertEquals(1, conflicts.get());
        assertEquals(1, personUnavailabilityRepository.findOverlapping(person.getId(), dayStart(base), dayEndExclusive(base.plusDays(5))).size());
    }

    @Test
    void shouldNotPersistDuplicateRowsWhenExactSamePeriodCreatedConcurrently() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Duplicate Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate base = LocalDate.of(2026, 10, 10);
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(phone, request),
                () -> personUnavailabilityService.create(phone, request),
                successes,
                conflicts
        );

        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
        assertEquals(1, personUnavailabilityRepository.findOverlapping(person.getId(), dayStart(base), dayEndExclusive(base.plusDays(2))).size());
    }

    @Test
    void shouldAllowDifferentPeopleToCreateUnavailabilitiesConcurrently() throws Exception {
        String phoneA = uniquePhoneNumber();
        String phoneB = uniquePhoneNumber();
        Person personA = savePersonWithRole("Concurrent Independent Person A", phoneA, "ROLE_OPERATOR");
        Person personB = savePersonWithRole("Concurrent Independent Person B", phoneB, "ROLE_OPERATOR");
        cleanupPersonIdA = personA.getId();
        cleanupPersonIdB = personB.getId();

        LocalDate base = LocalDate.of(2026, 10, 15);
        PersonUnavailabilityRequestDTO requestA = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);
        PersonUnavailabilityRequestDTO requestB = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(phoneA, requestA),
                () -> personUnavailabilityService.create(phoneB, requestB),
                successes,
                conflicts
        );

        assertEquals(2, successes.get(), "Pessoas diferentes devem poder operar concorrentemente sem bloqueio cruzado");
        assertEquals(0, conflicts.get());
    }

    @Test
    void shouldAlwaysPersistUnavailabilityRegardlessOfRaceOutcomeAgainstScaleCreationForSamePersonAndDate() throws Exception {
        // Com o novo invariante (feature/schedule-unavailability-conflict-management), uma
        // indisponibilidade futura nunca e bloqueada por um EventAssignment: se a indisponibilidade
        // comitar primeiro, o assignment concorrente deve ser rejeitado (regra 1, inalterada); se o
        // assignment comitar primeiro, a indisponibilidade criada depois NAO e mais rejeitada (o
        // evento e futuro, nao em andamento) e ambos passam a coexistir. Portanto a indisponibilidade
        // deve ter sucesso em qualquer ordem; apenas o assignment pode ou nao existir ao final.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Invariant Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Concurrent Test Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 10, 20);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));

        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(eventDate), dayEndExclusive(eventDate), null);
        CelebrationEventWithScaleRequestDTO scaleRequest = new CelebrationEventWithScaleRequestDTO();
        scaleRequest.setNameMassOrEvent("Concurrent Invariant Event " + UUID.randomUUID());
        scaleRequest.setStartAt(eventStartAt);
        scaleRequest.setEndAt(eventStartAt.plusHours(1));
        scaleRequest.setMassOrCelebration(true);
        scaleRequest.setLocationId(locationId);
        scaleRequest.setReaderIds(List.of(person.getId()));

        AtomicReference<Long> createdEventId = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable unavailabilityTask = () -> {
                ready.countDown();
                await(ready, start);
                try {
                    personUnavailabilityService.create(phone, unavailabilityRequest);
                } catch (ErrorResponseException ignored) {
                    // esperado quando a escala vence a corrida
                }
            };
            Runnable scaleTask = () -> {
                ready.countDown();
                await(ready, start);
                try {
                    var response = celebrationEventService.createEventWithScale(scaleRequest);
                    createdEventId.set(response.getEventId());
                } catch (ErrorResponseException ignored) {
                    // esperado quando a indisponibilidade vence a corrida
                }
            };

            var futureOne = executor.submit(unavailabilityTask);
            var futureTwo = executor.submit(scaleTask);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(15, TimeUnit.SECONDS);
            futureTwo.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        cleanupEventId = createdEventId.get();

        boolean hasUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), dayStart(eventDate), dayEndExclusive(eventDate)).isEmpty();
        boolean hasAssignment = cleanupEventId != null
                && !eventAssignmentRepository.findAllByEventId(cleanupEventId).isEmpty();

        assertTrue(hasUnavailability,
                "A indisponibilidade deve ser persistida independentemente da ordem: vencendo a corrida "
                        + "diretamente, ou sendo criada depois de um assignment futuro (que nao mais a bloqueia)");
        // hasAssignment pode ser true (escala venceu, coexistencia permitida) ou false (indisponibilidade
        // venceu, escala corretamente rejeitada); ambos os desfechos sao validos nesta branch.
    }

    @Test
    void shouldAlwaysPersistUnavailabilityRegardlessOfRaceOutcomeAgainstScaleUpdateForSamePersonAndDate() throws Exception {
        // Mesma justificativa do cenario de criacao: a pessoa e nova na escala deste evento (que
        // comeca vazia), entao a mesma logica de "assignment futuro nao bloqueia indisponibilidade
        // criada depois" se aplica.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Scale Update Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Concurrent Scale Update Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 10, 25);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Scale Update Event " + UUID.randomUUID(), eventStartAt, eventStartAt.plusHours(1), true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();

        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(eventDate), dayEndExclusive(eventDate), null);
        CelebrationEventScaleRequestDTO scaleRequest =
                new CelebrationEventScaleRequestDTO(locationId, null, List.of(person.getId()), null, null, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable unavailabilityTask = () -> {
                ready.countDown();
                await(ready, start);
                try {
                    personUnavailabilityService.create(phone, unavailabilityRequest);
                } catch (ErrorResponseException ignored) {
                    // esperado quando a atualizacao de escala vence a corrida
                }
            };
            Runnable scaleUpdateTask = () -> {
                ready.countDown();
                await(ready, start);
                try {
                    celebrationEventService.updateEventScale(eventId, scaleRequest);
                } catch (ErrorResponseException ignored) {
                    // esperado quando a indisponibilidade vence a corrida
                }
            };

            var futureOne = executor.submit(unavailabilityTask);
            var futureTwo = executor.submit(scaleUpdateTask);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(15, TimeUnit.SECONDS);
            futureTwo.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        boolean hasUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), dayStart(eventDate), dayEndExclusive(eventDate)).isEmpty();
        boolean hasAssignment = !eventAssignmentRepository.findAllByEventId(eventId).isEmpty();

        assertTrue(hasUnavailability,
                "A indisponibilidade deve ser persistida independentemente da ordem (atualizacao de escala): "
                        + "vencendo a corrida diretamente, ou sendo criada depois de um assignment futuro");
        // hasAssignment pode ser true ou false; ambos os desfechos sao validos nesta branch.
    }

    @Test
    void shouldAllowBothEventDateChangeAndFutureUnavailabilityToSucceedConcurrentlyForAssignedPerson() throws Exception {
        // Mesma politica do cenario de endAt isolado: uma mudanca completa de data/horario do evento
        // (updateEvent) nunca valida disponibilidade das pessoas ja atribuidas: as duas operacoes
        // concorrentes devem concluir com sucesso, e o conflito resultante passa a ser derivado.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Date Change Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate originalDate = LocalDate.of(2026, 11, 1);
        LocalDate newDate = LocalDate.of(2026, 11, 5);
        LocalDateTime originalStartAt = LocalDateTime.of(originalDate, LocalTime.of(19, 0));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Date Change Event " + UUID.randomUUID(), originalStartAt, originalStartAt.plusHours(1), true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(newDate), dayEndExclusive(newDate), null);
        LocalDateTime newStartAt = LocalDateTime.of(newDate, event.getStartAt().toLocalTime());
        CelebrationEventRequestDTO dateChangeRequest =
                new CelebrationEventRequestDTO(event.getNameMassOrEvent(), newStartAt, newStartAt.plusHours(1), true);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(phone, unavailabilityRequest),
                () -> celebrationEventService.updateEvent(eventId, dateChangeRequest),
                successes,
                conflicts
        );

        assertEquals(2, successes.get(), "As duas operacoes concorrentes independentes devem concluir com sucesso");
        assertEquals(0, conflicts.get());

        CelebrationEvent finalEvent = celebrationEventRepository.findById(eventId).orElseThrow();
        assertEquals(newStartAt, finalEvent.getStartAt());
        boolean hasAssignment = !eventAssignmentRepository.findAllByEventId(eventId).isEmpty();
        boolean hasUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), dayStart(newDate), dayEndExclusive(newDate)).isEmpty();
        boolean hasConflictingUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), finalEvent.getStartAt(), finalEvent.getEndAt()).isEmpty();

        assertTrue(hasAssignment, "O assignment deve permanecer preservado");
        assertTrue(hasUnavailability, "A indisponibilidade deve ter sido persistida");
        assertTrue(hasConflictingUnavailability,
                "O evento final deve conflitar com a indisponibilidade, visivel na consulta administrativa derivada");
    }

    @Test
    void shouldAllowBothIsolatedEndAtChangeAndFutureUnavailabilityToSucceedConcurrentlyForAssignedPerson() throws Exception {
        // Regra desta branch (secao 4/30): uma indisponibilidade futura pode conviver com um
        // EventAssignment existente; o conflito passa a ser derivado (consulta administrativa), nao
        // bloqueante. Portanto, as duas operacoes concorrentes abaixo devem concluir com sucesso,
        // independentemente da ordem, preservando assignment, participacao e a nova indisponibilidade.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent EndAt Change Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate eventDate = LocalDate.of(2026, 10, 20);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        LocalDateTime eventEndAt = LocalDateTime.of(eventDate, LocalTime.of(20, 0));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent EndAt Change Event " + UUID.randomUUID(), eventStartAt, eventEndAt, true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        eventParticipationResponseService.respond(phone, eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));
        EventParticipationResponse participationBeforeRace = eventParticipationResponseRepository
                .findByEventIdAndPersonId(eventId, person.getId())
                .orElseThrow();
        Long participationId = participationBeforeRace.getId();
        LocalDateTime participationRespondedAt = participationBeforeRace.getRespondedAt();

        // Estado inicial: assignment 19:00-20:00, sem indisponibilidade. Transacao A cria a
        // indisponibilidade futura 20:00-21:00; transacao B estende isoladamente o endAt do evento
        // (mantendo nome/startAt/massOrCelebration) para 20:30, o que passa a se sobrepor a
        // indisponibilidade. Ambas devem concluir com sucesso.
        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(eventEndAt, eventEndAt.plusHours(1), null);
        CelebrationEventRequestDTO endAtChangeRequest =
                new CelebrationEventRequestDTO(event.getNameMassOrEvent(), eventStartAt, eventEndAt.plusMinutes(30), true);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(phone, unavailabilityRequest),
                () -> celebrationEventService.updateEvent(eventId, endAtChangeRequest),
                successes,
                conflicts
        );

        assertEquals(2, successes.get(),
                "Atualizacoes concorrentes independentes (indisponibilidade futura x alteracao isolada de endAt) "
                        + "devem concluir ambas com sucesso");
        assertEquals(0, conflicts.get());

        CelebrationEvent finalEvent = celebrationEventRepository.findById(eventId).orElseThrow();
        assertEquals(eventStartAt, finalEvent.getStartAt());
        assertEquals(eventEndAt.plusMinutes(30), finalEvent.getEndAt());

        boolean hasAssignment = !eventAssignmentRepository.findAllByEventId(eventId).isEmpty();
        boolean hasUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), eventEndAt, eventEndAt.plusHours(1)).isEmpty();
        boolean hasConflictingUnavailability = !personUnavailabilityRepository
                .findOverlapping(person.getId(), finalEvent.getStartAt(), finalEvent.getEndAt()).isEmpty();

        assertTrue(hasAssignment, "O assignment deve permanecer preservado");
        assertTrue(hasUnavailability, "A indisponibilidade 20:00-21:00 deve ter sido persistida");
        assertTrue(hasConflictingUnavailability,
                "O evento final (19:00-20:30) deve conflitar com a indisponibilidade (20:00-21:00), "
                        + "e esse conflito deve ser visivel na consulta administrativa derivada");

        // A participacao registrada antes da corrida deve permanecer exatamente a mesma (mesmo ID,
        // status, motivo e respondedAt): nenhuma das duas operacoes concorrentes deve toca-la.
        EventParticipationResponse participationAfterRace = eventParticipationResponseRepository
                .findByEventIdAndPersonId(eventId, person.getId())
                .orElseThrow();
        assertEquals(participationId, participationAfterRace.getId());
        assertEquals(ParticipationStatus.CONFIRMED, participationAfterRace.getStatus());
        assertEquals(participationBeforeRace.getDeclineReason(), participationAfterRace.getDeclineReason());
        assertEquals(participationRespondedAt, participationAfterRace.getRespondedAt());

        List<ScheduleUnavailabilityConflictResponseDTO> adminConflicts = scheduleUnavailabilityConflictService.findByEventId(eventId);
        assertEquals(1, adminConflicts.size(), "O conflito derivado deve ser retornado pela consulta administrativa");
        assertEquals(person.getId(), adminConflicts.get(0).getPersonId());
        assertEquals("READER", adminConflicts.get(0).getAssignmentType());
    }

    @Test
    void shouldRejectScaleAdditionWhenUnavailabilityWasAlreadyConfirmedInAPriorTransaction() {
        // Ordem estritamente deterministica, sem depender do escalonador de threads: cada chamada de
        // service abaixo e uma transacao @Transactional independente que comita integralmente ao
        // retornar (esta classe de teste nao envolve os testes em uma transacao externa), entao a
        // simples sequencia de chamadas Java reproduz exatamente "transacao 1 comita antes da
        // transacao 2 comecar" sem qualquer necessidade de latches artificiais.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Deterministic Unavailability Wins Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Person priest = savePersonWithRole("Deterministic Unavailability Wins Priest", uniquePhoneNumber(), "ROLE_OPERATOR");
        cleanupPersonIdB = priest.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(priest, MinistryType.PRIEST));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Deterministic Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 12, 1);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        LocalDateTime eventEndAt = eventStartAt.plusHours(1);

        // Estado inicial: evento futuro ja existe com escala previa (padre); a pessoa alvo ainda nao
        // esta escalada e ainda nao possui indisponibilidade.
        CelebrationEventWithScaleRequestDTO initialScale = new CelebrationEventWithScaleRequestDTO();
        initialScale.setNameMassOrEvent("Deterministic Unavailability Wins Event " + UUID.randomUUID());
        initialScale.setStartAt(eventStartAt);
        initialScale.setEndAt(eventEndAt);
        initialScale.setMassOrCelebration(true);
        initialScale.setLocationId(locationId);
        initialScale.setPriestId(priest.getId());
        var initialResponse = celebrationEventService.createEventWithScale(initialScale);
        Long eventId = initialResponse.getEventId();
        cleanupEventId = eventId;

        // Passo 1-3: a transacao da indisponibilidade bloqueia a Person, persiste e COMITA (a chamada
        // retorna normalmente, encerrando a transacao, antes de qualquer outra operacao comecar).
        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(eventDate), dayEndExclusive(eventDate), null);
        PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(phone, unavailabilityRequest);

        assertTrue(
                personUnavailabilityRepository.findOverlapping(person.getId(), dayStart(eventDate), dayEndExclusive(eventDate))
                        .stream().anyMatch(u -> u.getId().equals(unavailability.getId())),
                "A indisponibilidade deve estar persistida e comitada antes da tentativa de escala"
        );

        // Passo 4-6: somente depois, em uma transacao inteiramente separada, a operacao administrativa
        // tenta adicionar a pessoa a escala; ela bloqueia a Person e releem as indisponibilidades ja
        // comitadas (nao uma leitura anterior ao lock), rejeitando a inclusao.
        CelebrationEventScaleRequestDTO scaleUpdate =
                new CelebrationEventScaleRequestDTO(locationId, priest.getId(), List.of(person.getId()), null, null, null);

        PersonUnavailableForEventException exception = assertThrows(
                PersonUnavailableForEventException.class, () -> celebrationEventService.updateEventScale(eventId, scaleUpdate));
        assertEquals("PERSON_UNAVAILABLE_FOR_EVENT", exception.getErrorCode());

        List<EventAssignment> finalAssignments = eventAssignmentRepository.findAllByEventId(eventId);
        assertEquals(1, finalAssignments.size(), "A escala anterior (somente o padre) deve permanecer preservada");
        assertEquals(EventAssignmentType.PRIEST, finalAssignments.get(0).getAssignmentType());
        assertTrue(
                finalAssignments.stream().noneMatch(assignment -> assignment.getPerson().getId().equals(person.getId())),
                "O assignment da pessoa indisponivel nao deve ter sido persistido (sem persistencia parcial)"
        );
        assertTrue(
                eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, person.getId()).isEmpty(),
                "Nenhuma participacao deve ter sido criada para a pessoa rejeitada"
        );
    }

    @Test
    void shouldMakeScaleUpdateWaitForPersonLockAndRejectAfterRereadingCommittedUnavailability() throws Exception {
        // Cenario genuinamente concorrente (diferente do teste deterministico-sequencial acima):
        // a transacao A (indisponibilidade) mantem o lock da Person e a linha aberta (nao comitada)
        // enquanto a transacao B (updateEventScale) e efetivamente iniciada em paralelo e comprovada,
        // via performance_schema.data_lock_waits, genuinamente bloqueada aguardando o lock real do
        // MySQL antes de A comitar. A ordenacao entre "A adquiriu o lock e persistiu" e "B comecou" e
        // garantida por CountDownLatch; a prova de que B ficou bloqueada no lock (e nao apenas lenta)
        // vem do catalogo de locks do proprio MySQL, nao de sleep.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Lock Wait Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Person priest = savePersonWithRole("Concurrent Lock Wait Priest", uniquePhoneNumber(), "ROLE_OPERATOR");
        cleanupPersonIdB = priest.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(priest, MinistryType.PRIEST));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Concurrent Lock Wait Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 12, 10);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        LocalDateTime eventEndAt = eventStartAt.plusHours(1);

        // Estado inicial: evento futuro com padre ja escalado; pessoa alvo ainda nao escalada e sem
        // indisponibilidade; nenhuma participacao da pessoa alvo.
        CelebrationEventWithScaleRequestDTO initialScale = new CelebrationEventWithScaleRequestDTO();
        initialScale.setNameMassOrEvent("Concurrent Lock Wait Event " + UUID.randomUUID());
        initialScale.setStartAt(eventStartAt);
        initialScale.setEndAt(eventEndAt);
        initialScale.setMassOrCelebration(true);
        initialScale.setLocationId(locationId);
        initialScale.setPriestId(priest.getId());
        var initialResponse = celebrationEventService.createEventWithScale(initialScale);
        Long eventId = initialResponse.getEventId();
        cleanupEventId = eventId;

        eventParticipationResponseService.respond(priest.getPhoneNumber(), eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));
        EventParticipationResponse priestParticipationBefore = eventParticipationResponseRepository
                .findByEventIdAndPersonId(eventId, priest.getId())
                .orElseThrow();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CountDownLatch unavailabilityLockedAndPersisted = new CountDownLatch(1);
        CountDownLatch releaseUnavailabilityTransaction = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Transacao A: abre uma transacao real independente, bloqueia a Person, persiste a
            // indisponibilidade (flush, ainda nao comitada) e so comita quando sinalizada.
            Future<?> futureA = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Person lockedPerson = personRepository.findByPhoneNumberForUpdate(phone).orElseThrow();
                PersonUnavailability entity = new PersonUnavailability(
                        lockedPerson, dayStart(eventDate), dayEndExclusive(eventDate), null);
                personUnavailabilityRepository.saveAndFlush(entity);
                unavailabilityLockedAndPersisted.countDown();
                awaitLatch(releaseUnavailabilityTransaction);
            }));

            assertTrue(
                    unavailabilityLockedAndPersisted.await(5, TimeUnit.SECONDS),
                    "A transacao A deveria ter adquirido o lock da Person e persistido a indisponibilidade"
            );

            // Transacao B: somente agora, com a Person ja bloqueada por A, a operacao administrativa
            // e efetivamente iniciada tentando incluir a mesma pessoa na escala.
            CelebrationEventScaleRequestDTO scaleUpdate =
                    new CelebrationEventScaleRequestDTO(locationId, priest.getId(), List.of(person.getId()), null, null, null);
            Future<PersonUnavailableForEventException> futureB = executor.submit(() -> {
                try {
                    celebrationEventService.updateEventScale(eventId, scaleUpdate);
                    return null;
                } catch (PersonUnavailableForEventException e) {
                    return e;
                }
            });

            // Comprovacao real (via MySQL, nao via sleep) de que a transacao B efetivamente comecou
            // e esta bloqueada aguardando o lock da Person mantido por A.
            awaitLockWaitInMySql(Duration.ofSeconds(15));

            // Somente agora A comita, liberando o lock para B reler a indisponibilidade ja confirmada.
            releaseUnavailabilityTransaction.countDown();
            futureA.get(15, TimeUnit.SECONDS);

            PersonUnavailableForEventException exceptionFromB = futureB.get(15, TimeUnit.SECONDS);

            assertTrue(exceptionFromB != null, "A transacao B deveria ter sido rejeitada apos reler a indisponibilidade comitada");
            assertEquals("PERSON_UNAVAILABLE_FOR_EVENT", exceptionFromB.getErrorCode());
        } finally {
            executor.shutdownNow();
        }

        assertFalse(
                personUnavailabilityRepository.findOverlapping(person.getId(), dayStart(eventDate), dayEndExclusive(eventDate)).isEmpty(),
                "A indisponibilidade da transacao A deve estar persistida e comitada"
        );

        List<EventAssignment> finalAssignments = eventAssignmentRepository.findAllByEventId(eventId);
        assertEquals(1, finalAssignments.size(), "A escala anterior (somente o padre) deve permanecer preservada");
        assertEquals(EventAssignmentType.PRIEST, finalAssignments.get(0).getAssignmentType());
        assertEquals(priest.getId(), finalAssignments.get(0).getPerson().getId(), "O padre permanece atribuido");
        assertTrue(
                finalAssignments.stream().noneMatch(assignment -> assignment.getPerson().getId().equals(person.getId())),
                "A pessoa alvo nao deve possuir EventAssignment (sem persistencia parcial)"
        );

        assertTrue(
                eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, person.getId()).isEmpty(),
                "Nenhuma participacao deve ter sido criada para a pessoa alvo"
        );
        EventParticipationResponse priestParticipationAfter = eventParticipationResponseRepository
                .findByEventIdAndPersonId(eventId, priest.getId())
                .orElseThrow();
        assertEquals(priestParticipationBefore.getId(), priestParticipationAfter.getId(),
                "A participacao anterior do padre nao deve ter sido alterada");
        assertEquals(ParticipationStatus.CONFIRMED, priestParticipationAfter.getStatus());

        Long persistedLocationId = jdbcTemplate.queryForObject(
                "SELECT location_id FROM tb_event_location WHERE event_id = ?", Long.class, eventId);
        assertEquals(locationId, persistedLocationId, "A localizacao do evento nao deve ter sido alterada");
    }

    /**
     * Poll deliberadamente curto (nao a sincronizacao principal, que e feita por CountDownLatch) que
     * consulta o catalogo de locks real do MySQL para confirmar que uma transacao esta genuinamente
     * bloqueada aguardando um lock de outra (performance_schema.data_lock_waits), em vez de assumir
     * isso por ausencia de resposta dentro de um prazo.
     */
    private void awaitLockWaitInMySql(Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Integer dataLockWaits = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
            if (dataLockWaits != null && dataLockWaits > 0) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        fail(
                "A transacao B nao entrou em estado de espera de lock dentro do tempo esperado; pode indicar leitura "
                        + "simples antes do lock, snapshot antigo, ausencia de releitura pos-lock ou ordem incorreta de locks."
        );
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch nao foi liberada dentro do tempo esperado");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shouldAllowUnavailabilityAfterScaleConfirmedInAPriorTransactionAndExposeExactlyOneAdministrativeConflict() {
        // Ordem estritamente deterministica (mesma justificativa do teste anterior, sequencia inversa):
        // o assignment comita integralmente antes de a pessoa criar a indisponibilidade futura.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Deterministic Assignment Wins Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Deterministic Assignment Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 12, 5);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        LocalDateTime eventEndAt = eventStartAt.plusHours(1);

        CelebrationEventWithScaleRequestDTO scaleRequest = new CelebrationEventWithScaleRequestDTO();
        scaleRequest.setNameMassOrEvent("Deterministic Assignment Wins Event " + UUID.randomUUID());
        scaleRequest.setStartAt(eventStartAt);
        scaleRequest.setEndAt(eventEndAt);
        scaleRequest.setMassOrCelebration(true);
        scaleRequest.setLocationId(locationId);
        scaleRequest.setReaderIds(List.of(person.getId()));

        // Passo 1-2: a transacao administrativa cria e COMITA o assignment.
        var scaleResponse = celebrationEventService.createEventWithScale(scaleRequest);
        Long eventId = scaleResponse.getEventId();
        cleanupEventId = eventId;

        eventParticipationResponseService.respond(phone, eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));

        // Passo 3: somente depois, em transacao separada, a pessoa cria a indisponibilidade futura.
        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(eventDate), dayEndExclusive(eventDate), null);
        PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(phone, unavailabilityRequest);

        assertTrue(
                personUnavailabilityRepository.findOverlapping(person.getId(), dayStart(eventDate), dayEndExclusive(eventDate))
                        .stream().anyMatch(u -> u.getId().equals(unavailability.getId())),
                "A indisponibilidade deve ser permitida mesmo apos o assignment ja comitado"
        );
        assertEquals(1, eventAssignmentRepository.findAllByEventId(eventId).size(), "O assignment deve permanecer persistido");

        Map<Long, ParticipationResponseSnapshot> participation =
                eventParticipationResponseService.findByPersonIdAndEventIds(person.getId(), List.of(eventId));
        assertTrue(participation.containsKey(eventId), "A participacao deve permanecer preservada");
        assertEquals(ParticipationStatus.CONFIRMED, participation.get(eventId).status());

        List<ScheduleUnavailabilityConflictResponseDTO> conflicts = scheduleUnavailabilityConflictService.findByEventId(eventId);
        assertEquals(1, conflicts.size(), "A consulta administrativa deve retornar exatamente um conflito eventId+personId");
        assertEquals(eventId, conflicts.get(0).getEventId());
        assertEquals(person.getId(), conflicts.get(0).getPersonId());
        assertEquals("READER", conflicts.get(0).getAssignmentType());
    }

    @Test
    void shouldRejectUnavailabilityConflictingWithEventInProgressPreservingAssignmentWithoutPartialPersistence() {
        // Usa o FixedClockConfig desta classe (2026-07-01T12:00:00 em America/Sao_Paulo) para simular
        // um evento em andamento de forma deterministica, sem depender de tempo real decorrido.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Started Event Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDateTime eventStartAt = LocalDateTime.of(2026, 7, 1, 11, 0);
        LocalDateTime eventEndAt = LocalDateTime.of(2026, 7, 1, 13, 0);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Started Event " + UUID.randomUUID(), eventStartAt, eventEndAt, true));
        cleanupEventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        LocalDateTime conflictingStartAt = LocalDateTime.of(2026, 7, 1, 12, 30);
        LocalDateTime conflictingEndAt = LocalDateTime.of(2026, 7, 1, 14, 0);
        PersonUnavailabilityRequestDTO conflictingRequest =
                new PersonUnavailabilityRequestDTO(conflictingStartAt, conflictingEndAt, null);

        assertThrows(ErrorResponseException.class, () -> personUnavailabilityService.create(phone, conflictingRequest));

        assertTrue(
                personUnavailabilityRepository.findOverlapping(person.getId(), conflictingStartAt, conflictingEndAt).isEmpty(),
                "Nenhuma indisponibilidade deve ter sido persistida (rollback completo, sem persistencia parcial)"
        );
        assertEquals(1, eventAssignmentRepository.findAllByEventId(event.getId()).size(),
                "O assignment do evento em andamento deve permanecer preservado");
    }

    @Test
    void shouldRejectUnavailabilityUpdateConflictingWithEventInProgressPreservingOriginalRangeWithoutPartialPersistence() {
        // Mesma logica do teste de create acima, mas exercitando update(): a indisponibilidade ja
        // existente (fora de qualquer conflito) e alterada para um intervalo que passa a conflitar
        // com um evento ja em andamento sob o FixedClockConfig desta classe.
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Started Update Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDateTime eventStartAt = LocalDateTime.of(2026, 7, 1, 11, 0);
        LocalDateTime eventEndAt = LocalDateTime.of(2026, 7, 1, 13, 0);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Started Update Event " + UUID.randomUUID(), eventStartAt, eventEndAt, true));
        cleanupEventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        LocalDateTime originalStartAt = LocalDateTime.of(2026, 7, 2, 8, 0);
        LocalDateTime originalEndAt = LocalDateTime.of(2026, 7, 2, 9, 0);
        PersonUnavailabilityResponseDTO existing = personUnavailabilityService.create(
                phone, new PersonUnavailabilityRequestDTO(originalStartAt, originalEndAt, null));

        LocalDateTime conflictingStartAt = LocalDateTime.of(2026, 7, 1, 12, 30);
        LocalDateTime conflictingEndAt = LocalDateTime.of(2026, 7, 1, 14, 0);
        PersonUnavailabilityRequestDTO conflictingUpdate =
                new PersonUnavailabilityRequestDTO(conflictingStartAt, conflictingEndAt, null);

        assertThrows(ErrorResponseException.class,
                () -> personUnavailabilityService.update(phone, existing.getId(), conflictingUpdate));

        boolean stillHasOriginalRange = !personUnavailabilityRepository
                .findOverlapping(person.getId(), originalStartAt, originalEndAt).isEmpty();
        boolean hasConflictingRange = !personUnavailabilityRepository
                .findOverlapping(person.getId(), conflictingStartAt, conflictingEndAt).isEmpty();

        assertTrue(stillHasOriginalRange,
                "A indisponibilidade deve permanecer com o intervalo original (rollback completo, sem persistencia parcial)");
        assertFalse(hasConflictingRange, "O intervalo conflitante nao deve ter sido persistido");
        assertEquals(1, eventAssignmentRepository.findAllByEventId(event.getId()).size(),
                "O assignment do evento em andamento deve permanecer preservado");
    }

    private void runConcurrently(
            Runnable first, Runnable second, AtomicInteger successes, AtomicInteger conflicts
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable wrappedFirst = wrap(first, ready, start, successes, conflicts);
            Runnable wrappedSecond = wrap(second, ready, start, successes, conflicts);

            var futureOne = executor.submit(wrappedFirst);
            var futureTwo = executor.submit(wrappedSecond);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(15, TimeUnit.SECONDS);
            futureTwo.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Runnable wrap(
            Runnable task, CountDownLatch ready, CountDownLatch start, AtomicInteger successes, AtomicInteger conflicts
    ) {
        return () -> {
            ready.countDown();
            await(ready, start);
            try {
                task.run();
                successes.incrementAndGet();
            } catch (ErrorResponseException e) {
                conflicts.incrementAndGet();
            }
        };
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Person savePersonWithRole(String name, String phoneNumber, String roleAuthority) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
        person.addRole(roleRepository.findByAuthority(roleAuthority).orElseThrow());
        return personRepository.saveAndFlush(person);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_person_unavailability WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private LocalDateTime dayStart(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime dayEndExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }

    private static String bootstrapUrl() {
        return "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo";
    }

    private static String queryVersion(Statement statement) throws SQLException {
        try (java.sql.ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-01T15:00:00Z"), APPLICATION_ZONE);
        }
    }
}
