package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.exception.exceptions.ErrorResponseException;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.PersonUnavailabilityRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, com transacoes reais e threads concorrentes, que o invariante "nao pode existir
 * EventAssignment para uma pessoa em uma data coberta por PersonUnavailability dessa pessoa"
 * se mantem sob concorrencia real, independentemente de qual operacao vence a corrida.
 */
@SpringBootTest
class PersonUnavailabilityConcurrencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

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
        PersonUnavailabilityRequestDTO first = new PersonUnavailabilityRequestDTO(base, base.plusDays(3), null);
        PersonUnavailabilityRequestDTO second = new PersonUnavailabilityRequestDTO(base.plusDays(1), base.plusDays(5), null);

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
        assertEquals(1, personUnavailabilityRepository.findOverlapping(person.getId(), base, base.plusDays(5)).size());
    }

    @Test
    void shouldNotPersistDuplicateRowsWhenExactSamePeriodCreatedConcurrently() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Duplicate Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate base = LocalDate.of(2026, 10, 10);
        PersonUnavailabilityRequestDTO request = new PersonUnavailabilityRequestDTO(base, base.plusDays(2), null);

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
        assertEquals(1, personUnavailabilityRepository.findOverlapping(person.getId(), base, base.plusDays(2)).size());
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
        PersonUnavailabilityRequestDTO requestA = new PersonUnavailabilityRequestDTO(base, base.plusDays(2), null);
        PersonUnavailabilityRequestDTO requestB = new PersonUnavailabilityRequestDTO(base, base.plusDays(2), null);

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
    void shouldPreserveInvariantWhenUnavailabilityRacesAgainstScaleCreationForSamePersonAndDate() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Invariant Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Concurrent Test Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 10, 20);

        PersonUnavailabilityRequestDTO unavailabilityRequest =
                new PersonUnavailabilityRequestDTO(eventDate, eventDate, null);
        CelebrationEventWithScaleRequestDTO scaleRequest = new CelebrationEventWithScaleRequestDTO();
        scaleRequest.setNameMassOrEvent("Concurrent Invariant Event " + UUID.randomUUID());
        scaleRequest.setEventDate(eventDate);
        scaleRequest.setEventTime(LocalTime.of(19, 0));
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
                .findOverlapping(person.getId(), eventDate, eventDate).isEmpty();
        boolean hasAssignment = cleanupEventId != null
                && !eventAssignmentRepository.findAllByEventId(cleanupEventId).isEmpty();

        assertFalse(hasUnavailability && hasAssignment,
                "Invariante violado: pessoa nao pode estar indisponivel e escalada na mesma data simultaneamente");
        assertTrue(hasUnavailability || hasAssignment, "Uma das duas operacoes deveria ter sido bem-sucedida");
    }

    @Test
    void shouldPreserveInvariantWhenUnavailabilityRacesAgainstScaleUpdateForSamePersonAndDate() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Scale Update Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, MinistryType.READER));

        Long locationId = locationRepository.saveAndFlush(new Location(null, "Concurrent Scale Update Church", "Address")).getId();
        LocalDate eventDate = LocalDate.of(2026, 10, 25);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Scale Update Event " + UUID.randomUUID(), eventDate, LocalTime.of(19, 0), true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();

        PersonUnavailabilityRequestDTO unavailabilityRequest = new PersonUnavailabilityRequestDTO(eventDate, eventDate, null);
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
                .findOverlapping(person.getId(), eventDate, eventDate).isEmpty();
        boolean hasAssignment = !eventAssignmentRepository.findAllByEventId(eventId).isEmpty();

        assertFalse(hasUnavailability && hasAssignment,
                "Invariante violado: pessoa nao pode estar indisponivel e escalada na mesma data simultaneamente (atualizacao de escala)");
        assertTrue(hasUnavailability || hasAssignment, "Uma das duas operacoes deveria ter sido bem-sucedida");
    }

    @Test
    void shouldPreserveInvariantWhenUnavailabilityRacesAgainstEventDateChangeForAssignedPerson() throws Exception {
        String phone = uniquePhoneNumber();
        Person person = savePersonWithRole("Concurrent Date Change Person", phone, "ROLE_OPERATOR");
        cleanupPersonIdA = person.getId();

        LocalDate originalDate = LocalDate.of(2026, 11, 1);
        LocalDate newDate = LocalDate.of(2026, 11, 5);
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Concurrent Date Change Event " + UUID.randomUUID(), originalDate, LocalTime.of(19, 0), true));
        cleanupEventId = event.getId();
        Long eventId = event.getId();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));

        PersonUnavailabilityRequestDTO unavailabilityRequest = new PersonUnavailabilityRequestDTO(newDate, newDate, null);
        CelebrationEventRequestDTO dateChangeRequest =
                new CelebrationEventRequestDTO(event.getNameMassOrEvent(), newDate, event.getEventTime(), true);

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
                    // esperado quando a alteracao de data vence a corrida
                }
            };
            Runnable dateChangeTask = () -> {
                ready.countDown();
                await(ready, start);
                try {
                    celebrationEventService.updateEvent(eventId, dateChangeRequest);
                } catch (ErrorResponseException ignored) {
                    // esperado quando a indisponibilidade vence a corrida
                }
            };

            var futureOne = executor.submit(unavailabilityTask);
            var futureTwo = executor.submit(dateChangeTask);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(15, TimeUnit.SECONDS);
            futureTwo.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        CelebrationEvent finalEvent = celebrationEventRepository.findById(eventId).orElseThrow();
        boolean hasAssignment = !eventAssignmentRepository.findAllByEventId(eventId).isEmpty();
        boolean hasUnavailabilityCoveringFinalDate = !personUnavailabilityRepository
                .findOverlapping(person.getId(), finalEvent.getEventDate(), finalEvent.getEventDate()).isEmpty();

        assertFalse(hasAssignment && hasUnavailabilityCoveringFinalDate,
                "Invariante violado: pessoa nao pode estar indisponivel e escalada na data final do evento simultaneamente (mudanca de eventDate)");
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

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }
}
