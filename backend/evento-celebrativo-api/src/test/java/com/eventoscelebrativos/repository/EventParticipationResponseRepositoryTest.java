package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventParticipationResponse;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventParticipationResponseRepositoryTest {

    @Autowired
    private EventParticipationResponseRepository eventParticipationResponseRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistParticipationResponseWithStatusAsStringAndTimestamps() {
        CelebrationEvent event = saveEvent("Participation Persistence Event");
        Person person = savePerson("Participation Person", "34974000001");
        LocalDateTime respondedAt = LocalDateTime.of(2026, 8, 1, 10, 0);

        EventParticipationResponse response = eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.DECLINED, "Viagem", respondedAt)
        );

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM tb_event_participation_response WHERE id = ?",
                String.class,
                response.getId()
        );

        assertNotNull(response.getId());
        assertEquals("DECLINED", storedStatus);
        assertEquals("Viagem", response.getDeclineReason());
        assertEquals(respondedAt, response.getRespondedAt());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void shouldAcceptDeclineReasonUpToFiveHundredCharacters() {
        CelebrationEvent event = saveEvent("Participation Reason Length Event");
        Person person = savePerson("Participation Reason Person", "34974000002");
        String maxReason = "a".repeat(500);

        EventParticipationResponse response = eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.DECLINED, maxReason, LocalDateTime.now())
        );

        assertEquals(500, response.getDeclineReason().length());
    }

    @Test
    void shouldEnforceUniqueEventAndPerson() {
        CelebrationEvent event = saveEvent("Participation Unique Event");
        Person person = savePerson("Participation Unique Person", "34974000003");
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                eventParticipationResponseRepository.saveAndFlush(
                        new EventParticipationResponse(event, person, ParticipationStatus.DECLINED, "Outro motivo", LocalDateTime.now())
                ));
    }

    @Test
    void shouldAllowDifferentPeopleRespondingToSameEvent() {
        CelebrationEvent event = saveEvent("Participation Multi Person Event");
        Person personOne = savePerson("Participation Multi Person One", "34974000004");
        Person personTwo = savePerson("Participation Multi Person Two", "34974000005");

        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, personOne, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, personTwo, ParticipationStatus.DECLINED, "Motivo", LocalDateTime.now())
        );

        assertEquals(2, eventParticipationResponseRepository.findAllByEventId(event.getId()).size());
    }

    @Test
    void shouldAllowSamePersonRespondingToDifferentEvents() {
        CelebrationEvent eventOne = saveEvent("Participation Event One");
        CelebrationEvent eventTwo = saveEvent("Participation Event Two");
        Person person = savePerson("Participation Same Person", "34974000006");

        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(eventOne, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(eventTwo, person, ParticipationStatus.DECLINED, "Motivo", LocalDateTime.now())
        );

        List<EventParticipationResponse> byPersonAndEvents = eventParticipationResponseRepository
                .findAllByPersonIdAndEventIdIn(person.getId(), List.of(eventOne.getId(), eventTwo.getId()));
        assertEquals(2, byPersonAndEvents.size());
    }

    @Test
    void shouldFindByEventIdAndPersonId() {
        CelebrationEvent event = saveEvent("Participation Lookup Event");
        Person person = savePerson("Participation Lookup Person", "34974000007");
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );

        Optional<EventParticipationResponse> found =
                eventParticipationResponseRepository.findByEventIdAndPersonId(event.getId(), person.getId());

        assertTrue(found.isPresent());
        assertEquals(ParticipationStatus.CONFIRMED, found.get().getStatus());
    }

    @Test
    void shouldDeleteAllResponsesForEventOnCascadeWhenEventIsDeleted() {
        CelebrationEvent event = saveEvent("Participation Event Cascade Event");
        Person person = savePerson("Participation Event Cascade Person", "34974000008");
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );

        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", event.getId());

        assertTrue(eventParticipationResponseRepository.findAllByEventId(event.getId()).isEmpty());
    }

    @Test
    void shouldDeleteAllResponsesForPersonOnCascadeWhenPersonIsDeleted() {
        CelebrationEvent event = saveEvent("Participation Person Cascade Event");
        Person person = savePerson("Participation Person Cascade Person", "34974000009");
        EventParticipationResponse response = eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );
        Long responseId = response.getId();
        Long personId = person.getId();

        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
        entityManager.clear();

        assertTrue(eventParticipationResponseRepository.findById(responseId).isEmpty());
    }

    @Test
    void shouldRetainOnlyResponsesForGivenPersonIds() {
        CelebrationEvent event = saveEvent("Participation Retain Event");
        Person personToKeep = savePerson("Participation Retain Keep", "34974000010");
        Person personToRemove = savePerson("Participation Retain Remove", "34974000011");
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, personToKeep, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, personToRemove, ParticipationStatus.DECLINED, "Motivo", LocalDateTime.now())
        );

        eventParticipationResponseRepository.deleteAllByEventIdAndPersonIdNotIn(event.getId(), List.of(personToKeep.getId()));
        entityManager.flush();
        entityManager.clear();

        List<EventParticipationResponse> remaining = eventParticipationResponseRepository.findAllByEventId(event.getId());
        assertEquals(1, remaining.size());
        assertEquals(personToKeep.getId(), remaining.get(0).getPerson().getId());
    }

    @Test
    void shouldDeleteAllResponsesForEventWhenCalledDirectly() {
        CelebrationEvent event = saveEvent("Participation Delete All Event");
        Person person = savePerson("Participation Delete All Person", "34974000012");
        eventParticipationResponseRepository.saveAndFlush(
                new EventParticipationResponse(event, person, ParticipationStatus.CONFIRMED, null, LocalDateTime.now())
        );

        eventParticipationResponseRepository.deleteAllByEventId(event.getId());
        entityManager.flush();

        assertTrue(eventParticipationResponseRepository.findAllByEventId(event.getId()).isEmpty());
    }

    private CelebrationEvent saveEvent(String name) {
        LocalDateTime startAt = LocalDateTime.of(2026, 9, 1, 19, 0);
        CelebrationEvent event = new CelebrationEvent(
                null,
                name,
                startAt,
                startAt.plusHours(1),
                true
        );
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person(name, phoneNumber, LocalDate.of(1990, 1, 10));
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
