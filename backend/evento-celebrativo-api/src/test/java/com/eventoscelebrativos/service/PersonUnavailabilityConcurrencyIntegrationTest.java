package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.ErrorResponseException;
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
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
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

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes, que o invariante "nao pode existir
 * EventAssignment para uma pessoa em uma data coberta por PersonUnavailability dessa pessoa"
 * se mantem sob concorrencia PostgreSQL real via Testcontainers, independentemente de qual
 * operacao vence a corrida.
 */
@SpringBootTest
@Import(PersonUnavailabilityConcurrencyIntegrationTest.FixedClockConfig.class)
class PersonUnavailabilityConcurrencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private PersonUnavailabilityService personUnavailabilityService;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

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
        Person person = savePerson("Concurrent Overlap Person", phone);
        cleanupPersonIdA = person.getId();

        LocalDate base = LocalDate.of(2026, 10, 1);
        PersonUnavailabilityRequestDTO first = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(3)), null);
        PersonUnavailabilityRequestDTO second = new PersonUnavailabilityRequestDTO(dayStart(base.plusDays(1)), dayEndExclusive(base.plusDays(5)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(person.getId(), first),
                () -> personUnavailabilityService.create(person.getId(), second),
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
        Person person = savePerson("Concurrent Duplicate Person", phone);
        cleanupPersonIdA = person.getId();

        LocalDate base = LocalDate.of(2026, 10, 10);
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(person.getId(), request),
                () -> personUnavailabilityService.create(person.getId(), request),
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
        Person personA = savePerson("Concurrent Independent Person A", phoneA);
        Person personB = savePerson("Concurrent Independent Person B", phoneB);
        cleanupPersonIdA = personA.getId();
        cleanupPersonIdB = personB.getId();

        LocalDate base = LocalDate.of(2026, 10, 15);
        PersonUnavailabilityRequestDTO requestA = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);
        PersonUnavailabilityRequestDTO requestB = new PersonUnavailabilityRequestDTO(dayStart(base), dayEndExclusive(base.plusDays(2)), null);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(
                () -> personUnavailabilityService.create(personA.getId(), requestA),
                () -> personUnavailabilityService.create(personB.getId(), requestB),
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
        Person person = savePerson("Concurrent Invariant Person", phone);
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

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
                    personUnavailabilityService.create(person.getId(), unavailabilityRequest);
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
        Person person = savePerson("Concurrent Scale Update Person", phone);
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

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
                    personUnavailabilityService.create(person.getId(), unavailabilityRequest);
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
        Person person = savePerson("Concurrent Date Change Person", phone);
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
                () -> personUnavailabilityService.create(person.getId(), unavailabilityRequest),
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
        Person person = savePerson("Concurrent EndAt Change Person", phone);
        cleanupPersonIdA = person.getId();

        LocalDate eventDate = LocalDate.of(2026, 10, 20);
        LocalDateTime eventStartAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        LocalDateTime eventEndAt = LocalDateTime.of(eventDate, LocalTime.of(20, 0));
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent EndAt Change Event " + UUID.randomUUID(), eventStartAt, eventEndAt, true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        eventParticipationResponseService.respond(person.getId(), eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));
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
                () -> personUnavailabilityService.create(person.getId(), unavailabilityRequest),
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
    void shouldAllowUnavailabilityAfterScaleConfirmedInAPriorTransactionAndExposeExactlyOneAdministrativeConflict() {
        // Ordem estritamente deterministica (mesma justificativa do teste anterior, sequencia inversa):
        // o assignment comita integralmente antes de a pessoa criar a indisponibilidade futura.
        String phone = uniquePhoneNumber();
        Person person = savePerson("Deterministic Assignment Wins Person", phone);
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));

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

        eventParticipationResponseService.respond(person.getId(), eventId, new ParticipationResponseRequestDTO("CONFIRMED", null));

        // Passo 3: somente depois, em transacao separada, a pessoa cria a indisponibilidade futura.
        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(dayStart(eventDate), dayEndExclusive(eventDate), null);
        PersonUnavailabilityResponseDTO unavailability = personUnavailabilityService.create(person.getId(), unavailabilityRequest);

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

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, BIRTHDAY);
        return personRepository.saveAndFlush(person);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_person_unavailability WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-01T15:00:00Z"), APPLICATION_ZONE);
        }
    }
}
