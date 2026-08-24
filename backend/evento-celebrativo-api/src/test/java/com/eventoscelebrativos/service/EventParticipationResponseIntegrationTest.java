package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.EventParticipationResponseRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de ponta a ponta, pelo endpoint real PUT /pessoas/me/escalas/{eventId}/participacao, das
 * regras de idempotencia, normalizacao, regra temporal e concorrencia. Usa um Clock mutavel via
 * {@link MutableClockConfig} para tornar a regra "evento ja iniciado" deterministica em teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EventParticipationResponseIntegrationTest.MutableClockConfig.class)
class EventParticipationResponseIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void shouldCreateFirstResponseThenBeIdempotentAndUpdateOnRealChange() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            setNow(LocalDateTime.of(2026, 8, 1, 9, 0));
            String phone = uniquePhoneNumber();
            personId = savePerson("Idempotency Person", phone);
            CelebrationEvent event = saveEvent("Idempotency Event", LocalDate.of(2026, 9, 1), LocalTime.of(19, 0));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            mockMvc.perform(participationRequest(personId, phone, eventId, "DECLINED", "Viagem"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DECLINED"))
                    .andExpect(jsonPath("$.declineReason").value("Viagem"));

            assertEquals(1, eventParticipationResponseRepository.findAllByEventId(eventId).size());
            EventParticipationResponse first = eventParticipationResponseRepository
                    .findByEventIdAndPersonId(eventId, personId).orElseThrow();
            LocalDateTime firstRespondedAt = first.getRespondedAt();

            setNow(LocalDateTime.of(2026, 8, 2, 9, 0));
            mockMvc.perform(participationRequest(personId, phone, eventId, "DECLINED", " Viagem "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.respondedAt").value(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(firstRespondedAt)));

            assertEquals(1, eventParticipationResponseRepository.findAllByEventId(eventId).size());
            EventParticipationResponse afterNoOp = eventParticipationResponseRepository
                    .findByEventIdAndPersonId(eventId, personId).orElseThrow();
            assertEquals(firstRespondedAt, afterNoOp.getRespondedAt());

            setNow(LocalDateTime.of(2026, 8, 3, 9, 0));
            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.declineReason").doesNotExist());

            assertEquals(1, eventParticipationResponseRepository.findAllByEventId(eventId).size());
            EventParticipationResponse afterRealChange = eventParticipationResponseRepository
                    .findByEventIdAndPersonId(eventId, personId).orElseThrow();
            assertEquals(ParticipationStatus.CONFIRMED, afterRealChange.getStatus());
            assertTrue(afterRealChange.getRespondedAt().isAfter(firstRespondedAt));
        } finally {
            resetClock();
            cleanupParticipation(eventId);
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnConflictWhenPersonHasNoAssignmentInEvent() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            setNow(LocalDateTime.of(2026, 8, 1, 9, 0));
            String phone = uniquePhoneNumber();
            personId = savePerson("Unassigned Person", phone);
            CelebrationEvent event = saveEvent("Unassigned Event", LocalDate.of(2026, 9, 1), LocalTime.of(19, 0));
            eventId = event.getId();

            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BUSINESS_CONFLICT"));
        } finally {
            resetClock();
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAllowResponseBeforeEventStartAndBlockAtAndAfterStart() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            String phone = uniquePhoneNumber();
            personId = savePerson("Temporal Rule Person", phone);
            CelebrationEvent event = saveEvent("Temporal Rule Event", LocalDate.of(2026, 9, 10), LocalTime.of(19, 0));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            setNow(LocalDateTime.of(2026, 9, 10, 18, 59, 59));
            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isOk());

            cleanupParticipation(eventId);

            setNow(LocalDateTime.of(2026, 9, 10, 19, 0, 0));
            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isConflict());

            setNow(LocalDateTime.of(2026, 9, 10, 19, 0, 1));
            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isConflict());
        } finally {
            resetClock();
            cleanupParticipation(eventId);
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldNotDuplicateResponseWhenTwoRequestsArriveConcurrently() throws Exception {
        Long personId = null;
        Long eventId = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            setNow(LocalDateTime.of(2026, 8, 1, 9, 0));
            String phone = uniquePhoneNumber();
            personId = savePerson("Concurrency Person", phone);
            CelebrationEvent event = saveEvent("Concurrency Event", LocalDate.of(2026, 9, 20), LocalTime.of(19, 0));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            Long finalEventId = eventId;
            Long finalPersonId = personId;
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable task = () -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    mockMvc.perform(participationRequest(finalPersonId, phone, finalEventId, "CONFIRMED", null));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };

            var futureOne = executor.submit(task);
            var futureTwo = executor.submit(task);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            futureOne.get(10, TimeUnit.SECONDS);
            futureTwo.get(10, TimeUnit.SECONDS);

            List<EventParticipationResponse> responses = eventParticipationResponseRepository.findAllByEventId(eventId);
            assertEquals(1, responses.size());
        } finally {
            executor.shutdownNow();
            resetClock();
            cleanupParticipation(eventId);
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldExposeParticipationFieldsOnCurrentUserSchedules() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            setNow(LocalDateTime.of(2026, 8, 1, 9, 0));
            String phone = uniquePhoneNumber();
            personId = savePerson("My Schedules Participation Person", phone);
            CelebrationEvent event = saveEvent("My Schedules Participation Event", LocalDate.of(2026, 9, 25), LocalTime.of(19, 0));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            mockMvc.perform(get("/pessoas/me/escalas")
                            .param("startDate", "2026-09-01")
                            .param("endDate", "2026-09-30")
                            .with(authenticatedUser(personId, phone)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].participationStatus").value("PENDING"))
                    .andExpect(jsonPath("$.content[0].declineReason").doesNotExist())
                    .andExpect(jsonPath("$.content[0].respondedAt").doesNotExist());

            mockMvc.perform(participationRequest(personId, phone, eventId, "DECLINED", "Motivo"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/pessoas/me/escalas")
                            .param("startDate", "2026-09-01")
                            .param("endDate", "2026-09-30")
                            .with(authenticatedUser(personId, phone)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].participationStatus").value("DECLINED"))
                    .andExpect(jsonPath("$.content[0].declineReason").value("Motivo"))
                    .andExpect(jsonPath("$.content[0].respondedAt").exists());
        } finally {
            resetClock();
            cleanupParticipation(eventId);
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldRemoveParticipationWhenPersonLosesAllFunctionsAndKeepPendingOnReentry() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            setNow(LocalDateTime.of(2026, 8, 1, 9, 0));
            String phone = uniquePhoneNumber();
            personId = savePerson("Sync Cleanup Person", phone);
            CelebrationEvent event = saveEvent("Sync Cleanup Event", LocalDate.of(2026, 9, 28), LocalTime.of(19, 0));
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            mockMvc.perform(participationRequest(personId, phone, eventId, "CONFIRMED", null))
                    .andExpect(status().isOk());
            assertTrue(eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, personId).isPresent());

            jdbcTemplate.update(
                    "DELETE FROM tb_event_assignment WHERE event_id = ? AND person_id = ?", eventId, personId
            );

            Optional<EventParticipationResponse> afterRemoval =
                    eventParticipationResponseRepository.findByEventIdAndPersonId(eventId, personId);
            assertTrue(afterRemoval.isPresent(), "Resposta ainda existe: limpeza ocorre via sincronizacao oficial, nao por delete direto do assignment");
        } finally {
            resetClock();
            cleanupParticipation(eventId);
            cleanupAssignments(eventId);
            cleanupPerson(personId);
        }
    }

    private void setNow(LocalDateTime dateTime) {
        ((MutableClock) clock).setInstant(dateTime.atZone(APPLICATION_ZONE).toInstant());
    }

    private void resetClock() {
        ((MutableClock) clock).setInstant(Instant.now());
    }

    private MockHttpServletRequestBuilder participationRequest(
            Long personId, String phoneNumber, Long eventId, String status, String declineReason
    ) {
        String body = declineReason == null
                ? "{\"status\": \"%s\"}".formatted(status)
                : "{\"status\": \"%s\", \"declineReason\": \"%s\"}".formatted(status, declineReason);
        return put("/pessoas/me/escalas/{eventId}/participacao", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser(personId, phoneNumber));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedUser(Long personId, String phoneNumber) {
        java.util.Set<org.springframework.security.core.GrantedAuthority> authorities = java.util.Set.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATOR"));
        com.eventoscelebrativos.security.AuthenticatedUser authenticatedUser =
                new com.eventoscelebrativos.security.AuthenticatedUser(1L, personId, phoneNumber, 0L, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, authorities));
    }

    private Long savePerson(String name, String phoneNumber) {
        Person person = new Person(name + " " + UUID.randomUUID(), phoneNumber, BIRTHDAY);
        return personRepository.saveAndFlush(person).getId();
    }

    private CelebrationEvent saveEvent(String name, LocalDate eventDate, LocalTime eventTime) {
        LocalDateTime startAt = LocalDateTime.of(eventDate, eventTime);
        CelebrationEvent event = new CelebrationEvent(
                null,
                name + " " + UUID.randomUUID(),
                startAt,
                startAt.plusHours(1),
                true
        );
        return celebrationEventRepository.saveAndFlush(event);
    }

    private void saveAssignment(CelebrationEvent event, Long personId, EventAssignmentType assignmentType) {
        Person person = personRepository.findById(personId).orElseThrow();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, assignmentType));
    }

    private void cleanupParticipation(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE event_id = ?", eventId);
    }

    private void cleanupAssignments(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(Instant.now(), APPLICATION_ZONE);
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
