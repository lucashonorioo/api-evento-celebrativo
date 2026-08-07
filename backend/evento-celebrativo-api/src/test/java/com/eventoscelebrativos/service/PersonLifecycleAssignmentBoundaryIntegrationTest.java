package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.exception.exceptions.PersonHasActiveAssignmentsException;
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
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Import(PersonLifecycleAssignmentBoundaryIntegrationTest.FixedClockConfig.class)
class PersonLifecycleAssignmentBoundaryIntegrationTest {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 8, 1, 12, 0, 0);
    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private UserAccountLifecycleService userAccountLifecycleService;

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

    private final List<Long> cleanupEventIds = new ArrayList<>();
    private final List<Long> cleanupPersonIds = new ArrayList<>();

    @BeforeEach
    void authenticateDifferentAdmin() {
        AuthenticatedUser admin = new AuthenticatedUser(
                999_000L,
                999_001L,
                "admin-***0001",
                0L,
                Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.authorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        for (Long eventId : cleanupEventIds.reversed()) {
            cleanupEvent(eventId);
        }
        for (Long personId : cleanupPersonIds.reversed()) {
            cleanupPerson(personId);
        }
        cleanupEventIds.clear();
        cleanupPersonIds.clear();
    }

    @Test
    void shouldDeactivatePersonWithoutAssignments() {
        Person person = createPerson();

        userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false));

        assertFalse(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(0L, countRows("tb_event_assignment", "person_id", person.getId()));
    }

    @Test
    void shouldIgnoreAssignmentWhenEventEndedBeforeCurrentSecond() {
        Person person = createPerson();
        Long eventId = createEventWithAssignment(person, CURRENT_SECOND.minusHours(2), CURRENT_SECOND.minusSeconds(1));

        userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false));

        assertFalse(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(1L, countRows("tb_event_assignment", "event_id", eventId));
    }

    @Test
    void shouldIgnoreAssignmentWhenEventEndsExactlyAtCurrentSecond() {
        Person person = createPerson();
        Long eventId = createEventWithAssignment(person, CURRENT_SECOND.minusHours(1), CURRENT_SECOND);

        userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false));

        assertFalse(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(1L, countRows("tb_event_assignment", "event_id", eventId));
    }

    @Test
    void shouldRejectDeactivationWhenEventIsInProgressAndPreserveAssignmentAndParticipation() {
        Person person = createPerson();
        Long eventId = createEventWithAssignment(person, CURRENT_SECOND.minusMinutes(30), CURRENT_SECOND.plusMinutes(30));
        createParticipation(eventId, person);

        PersonHasActiveAssignmentsException exception = assertThrows(
                PersonHasActiveAssignmentsException.class,
                () -> userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false))
        );

        assertEquals("PERSON_HAS_ACTIVE_ASSIGNMENTS", exception.getErrorCode());
        assertEquals(1, exception.getAssignments().size());
        assertEquals(eventId, exception.getAssignments().get(0).getEventId());
        assertEquals("READER", exception.getAssignments().get(0).getAssignmentType());
        assertTrue(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(1L, countRows("tb_event_assignment", "event_id", eventId));
        assertEquals(1L, countRows("tb_event_participation_response", "event_id", eventId));
    }

    @Test
    void shouldRejectDeactivationWhenEventIsFuture() {
        Person person = createPerson();
        Long eventId = createEventWithAssignment(person, CURRENT_SECOND.plusHours(1), CURRENT_SECOND.plusHours(2));

        PersonHasActiveAssignmentsException exception = assertThrows(
                PersonHasActiveAssignmentsException.class,
                () -> userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false))
        );

        assertEquals("PERSON_HAS_ACTIVE_ASSIGNMENTS", exception.getErrorCode());
        assertEquals(1, exception.getAssignments().size());
        assertEquals(eventId, exception.getAssignments().get(0).getEventId());
        assertTrue(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(1L, countRows("tb_event_assignment", "event_id", eventId));
    }

    @Test
    void shouldRejectSeveralActiveAssignmentsWithoutPartialChange() {
        Person person = createPerson();
        Long inProgressEventId = createEventWithAssignment(person, CURRENT_SECOND.minusMinutes(15), CURRENT_SECOND.plusMinutes(15));
        Long futureEventId = createEventWithAssignment(person, CURRENT_SECOND.plusDays(1), CURRENT_SECOND.plusDays(1).plusHours(1));
        createParticipation(futureEventId, person);

        PersonHasActiveAssignmentsException exception = assertThrows(
                PersonHasActiveAssignmentsException.class,
                () -> userAccountLifecycleService.updatePersonActive(person.getId(), activeRequest(false))
        );

        assertEquals("PERSON_HAS_ACTIVE_ASSIGNMENTS", exception.getErrorCode());
        assertEquals(2, exception.getAssignments().size());
        assertEquals(inProgressEventId, exception.getAssignments().get(0).getEventId());
        assertEquals(futureEventId, exception.getAssignments().get(1).getEventId());
        assertTrue(personRepository.findById(person.getId()).orElseThrow().isActive());
        assertEquals(2L, countRows("tb_event_assignment", "person_id", person.getId()));
        assertEquals(1L, countRows("tb_event_participation_response", "person_id", person.getId()));
        assertEquals(1L, countRows("tb_event_assignment", "event_id", inProgressEventId));
        assertEquals(1L, countRows("tb_event_assignment", "event_id", futureEventId));
    }

    private Person createPerson() {
        Person person = new Person();
        person.setName("Assignment Boundary " + UUID.randomUUID());
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(true);
        Person saved = personRepository.saveAndFlush(person);
        cleanupPersonIds.add(saved.getId());
        return saved;
    }

    private Long createEventWithAssignment(Person person, LocalDateTime startAt, LocalDateTime endAt) {
        CelebrationEvent event = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null,
                "Boundary Event " + UUID.randomUUID(),
                startAt,
                endAt,
                true
        ));
        cleanupEventIds.add(event.getId());
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, EventAssignmentType.READER));
        return event.getId();
    }

    private void createParticipation(Long eventId, Person person) {
        CelebrationEvent event = celebrationEventRepository.findById(eventId).orElseThrow();
        eventParticipationResponseRepository.saveAndFlush(new EventParticipationResponse(
                event,
                person,
                ParticipationStatus.CONFIRMED,
                null,
                CURRENT_SECOND.minusMinutes(1)
        ));
    }

    private PersonActiveRequestDTO activeRequest(boolean active) {
        PersonActiveRequestDTO request = new PersonActiveRequestDTO();
        request.setActive(active);
        return request;
    }

    private long countRows(String tableName, String columnName, Object value) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Long.class,
                value
        );
        return count == null ? 0L : count;
    }

    private void cleanupEvent(Long eventId) {
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        jdbcTemplate.update("DELETE FROM tb_event_participation_response WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)", personId);
        jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhone() {
        return "3494" + String.format("%07d", Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-01T15:00:00.900Z"), APPLICATION_ZONE);
        }
    }
}
