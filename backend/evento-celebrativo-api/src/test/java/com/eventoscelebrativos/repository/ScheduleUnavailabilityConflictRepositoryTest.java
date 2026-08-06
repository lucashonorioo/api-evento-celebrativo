package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonUnavailability;
import com.eventoscelebrativos.projection.ScheduleConflictUnavailabilityProjection;
import com.eventoscelebrativos.projection.ScheduleUnavailabilityConflictKeyProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ScheduleUnavailabilityConflictRepositoryTest {

    @Autowired
    private ScheduleUnavailabilityConflictRepository scheduleUnavailabilityConflictRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindConflictByEventIdWhenUnavailabilityOverlapsAssignment() {
        Person reader = savePerson("Conflict Key Reader");
        CelebrationEvent event = saveEvent("Conflict Key Event", LocalDateTime.of(2026, 9, 1, 19, 0), LocalDateTime.of(2026, 9, 1, 20, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 1, 19, 30), LocalDateTime.of(2026, 9, 1, 21, 0));

        List<ScheduleUnavailabilityConflictKeyProjection> result =
                scheduleUnavailabilityConflictRepository.findConflictsByEventId(event.getId(), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertEquals(1, result.size());
        ScheduleUnavailabilityConflictKeyProjection key = result.get(0);
        assertEquals(event.getId(), key.getEventId());
        assertEquals(reader.getId(), key.getPersonId());
        assertEquals("READER", key.getAssignmentType());
    }

    @Test
    void shouldReturnEmptyWhenAssignmentHasNoConflictingUnavailability() {
        Person reader = savePerson("Conflict Key No Conflict Reader");
        CelebrationEvent event = saveEvent("Conflict Key No Conflict Event", LocalDateTime.of(2026, 9, 2, 19, 0), LocalDateTime.of(2026, 9, 2, 20, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        // Adjacente, nao conflita.
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 2, 20, 0), LocalDateTime.of(2026, 9, 2, 21, 0));

        List<ScheduleUnavailabilityConflictKeyProjection> result =
                scheduleUnavailabilityConflictRepository.findConflictsByEventId(event.getId(), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotReturnConflictForEndedEvent() {
        Person reader = savePerson("Conflict Key Ended Reader");
        CelebrationEvent event = saveEvent("Conflict Key Ended Event", LocalDateTime.of(2026, 9, 3, 19, 0), LocalDateTime.of(2026, 9, 3, 20, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 3, 19, 30), LocalDateTime.of(2026, 9, 3, 21, 0));

        // currentSecond depois do endAt do evento: evento encerrado, nao deve aparecer.
        List<ScheduleUnavailabilityConflictKeyProjection> result =
                scheduleUnavailabilityConflictRepository.findConflictsByEventId(event.getId(), LocalDateTime.of(2026, 9, 3, 20, 0));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldOrderMultiplePeopleConflictingInSameEventByName() {
        Person zelia = savePerson("Zelia Almeida");
        Person arthur = savePerson("Arthur Costa");
        CelebrationEvent event = saveEvent("Conflict Key Multi Person Event", LocalDateTime.of(2026, 9, 4, 19, 0), LocalDateTime.of(2026, 9, 4, 20, 0));
        saveAssignment(event, zelia, EventAssignmentType.READER);
        saveAssignment(event, arthur, EventAssignmentType.COMMENTATOR);
        saveUnavailability(zelia, LocalDateTime.of(2026, 9, 4, 19, 30), LocalDateTime.of(2026, 9, 4, 21, 0));
        saveUnavailability(arthur, LocalDateTime.of(2026, 9, 4, 18, 0), LocalDateTime.of(2026, 9, 4, 19, 30));

        List<ScheduleUnavailabilityConflictKeyProjection> result =
                scheduleUnavailabilityConflictRepository.findConflictsByEventId(event.getId(), LocalDateTime.of(2026, 8, 1, 0, 0));

        assertEquals(
                List.of("Arthur Costa", "Zelia Almeida"),
                result.stream().map(ScheduleUnavailabilityConflictKeyProjection::getPersonName).toList()
        );
    }

    @Test
    void shouldPaginateDistinctEventPersonPairsAndCountThemInsteadOfUnavailabilityRows() {
        Person reader = savePerson("Conflict Range Reader");
        CelebrationEvent firstEvent = saveEvent("Conflict Range First Event", LocalDateTime.of(2026, 9, 10, 19, 0), LocalDateTime.of(2026, 9, 10, 20, 0));
        CelebrationEvent secondEvent = saveEvent("Conflict Range Second Event", LocalDateTime.of(2026, 9, 11, 19, 0), LocalDateTime.of(2026, 9, 11, 20, 0));
        saveAssignment(firstEvent, reader, EventAssignmentType.READER);
        saveAssignment(secondEvent, reader, EventAssignmentType.READER);
        // Duas indisponibilidades distintas atingindo o MESMO primeiro evento: nao deve duplicar o par.
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 10, 18, 0), LocalDateTime.of(2026, 9, 10, 19, 30));
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 10, 19, 45), LocalDateTime.of(2026, 9, 10, 21, 0));
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 11, 19, 30), LocalDateTime.of(2026, 9, 11, 21, 0));

        Page<ScheduleUnavailabilityConflictKeyProjection> firstPage = scheduleUnavailabilityConflictRepository.findConflictsByRange(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0), PageRequest.of(0, 1));

        assertEquals(2, firstPage.getTotalElements(), "totalElements deve contar pares evento+pessoa distintos, nao linhas de indisponibilidade");
        assertEquals(1, firstPage.getNumberOfElements());
        assertEquals(firstEvent.getId(), firstPage.getContent().get(0).getEventId());

        Page<ScheduleUnavailabilityConflictKeyProjection> secondPage = scheduleUnavailabilityConflictRepository.findConflictsByRange(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0), PageRequest.of(1, 1));

        assertEquals(1, secondPage.getNumberOfElements());
        assertEquals(secondEvent.getId(), secondPage.getContent().get(0).getEventId());
    }

    @Test
    void shouldExcludeEndedEventsFromRangeQuery() {
        Person reader = savePerson("Conflict Range Ended Reader");
        CelebrationEvent event = saveEvent("Conflict Range Ended Event", LocalDateTime.of(2026, 9, 12, 19, 0), LocalDateTime.of(2026, 9, 12, 20, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 12, 19, 30), LocalDateTime.of(2026, 9, 12, 21, 0));

        Page<ScheduleUnavailabilityConflictKeyProjection> result = scheduleUnavailabilityConflictRepository.findConflictsByRange(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 9, 12, 20, 0), PageRequest.of(0, 10));

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldFindEventCrossingMidnightConflict() {
        Person reader = savePerson("Conflict Midnight Reader");
        CelebrationEvent event = saveEvent("Conflict Midnight Event", LocalDateTime.of(2026, 9, 20, 23, 0), LocalDateTime.of(2026, 9, 21, 1, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 20, 23, 30), LocalDateTime.of(2026, 9, 21, 0, 30));

        Page<ScheduleUnavailabilityConflictKeyProjection> result = scheduleUnavailabilityConflictRepository.findConflictsByRange(
                LocalDateTime.of(2026, 9, 20, 0, 0), LocalDateTime.of(2026, 9, 21, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldFindConflictsForBothEventsWhenSingleUnavailabilitySpansTwoDifferentEvents() {
        Person reader = savePerson("Conflict Wide Span Reader");
        CelebrationEvent firstEvent = saveEvent("Conflict Wide Span First Event", LocalDateTime.of(2026, 9, 15, 9, 0), LocalDateTime.of(2026, 9, 15, 10, 0));
        CelebrationEvent secondEvent = saveEvent("Conflict Wide Span Second Event", LocalDateTime.of(2026, 9, 15, 18, 0), LocalDateTime.of(2026, 9, 15, 19, 0));
        saveAssignment(firstEvent, reader, EventAssignmentType.READER);
        saveAssignment(secondEvent, reader, EventAssignmentType.COMMENTATOR);
        // Uma unica indisponibilidade cobrindo o dia inteiro atinge os dois eventos distintos.
        saveUnavailability(reader, LocalDateTime.of(2026, 9, 15, 0, 0), LocalDateTime.of(2026, 9, 16, 0, 0));

        Page<ScheduleUnavailabilityConflictKeyProjection> result = scheduleUnavailabilityConflictRepository.findConflictsByRange(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0), PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
        assertEquals(
                List.of(firstEvent.getId(), secondEvent.getId()),
                result.getContent().stream().map(ScheduleUnavailabilityConflictKeyProjection::getEventId).toList()
        );
    }

    @Test
    void shouldFindUnavailabilitiesForGivenPersonIds() {
        Person reader = savePerson("Conflict Detail Reader");
        Person other = savePerson("Conflict Detail Other");
        PersonUnavailability first = saveUnavailability(reader, LocalDateTime.of(2026, 9, 5, 18, 0), LocalDateTime.of(2026, 9, 5, 19, 30));
        PersonUnavailability second = saveUnavailability(reader, LocalDateTime.of(2026, 9, 5, 19, 45), LocalDateTime.of(2026, 9, 5, 21, 0));
        saveUnavailability(other, LocalDateTime.of(2026, 9, 5, 18, 0), LocalDateTime.of(2026, 9, 5, 19, 0));

        List<ScheduleConflictUnavailabilityProjection> result =
                scheduleUnavailabilityConflictRepository.findUnavailabilitiesByPersonIdIn(List.of(reader.getId()));

        assertEquals(List.of(first.getId(), second.getId()), result.stream().map(ScheduleConflictUnavailabilityProjection::getId).toList());
    }

    private Person savePerson(String name) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private CelebrationEvent saveEvent(String name, LocalDateTime startAt, LocalDateTime endAt) {
        CelebrationEvent event = new CelebrationEvent(null, name + " " + UUID.randomUUID(), startAt, endAt, true);
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private void saveAssignment(CelebrationEvent event, Person person, EventAssignmentType type) {
        entityManager.persist(new EventAssignment(event, person, type));
        entityManager.flush();
    }

    private PersonUnavailability saveUnavailability(Person person, LocalDateTime startAt, LocalDateTime endAt) {
        PersonUnavailability unavailability = new PersonUnavailability(person, startAt, endAt, null);
        entityManager.persist(unavailability);
        entityManager.flush();
        return unavailability;
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }
}
