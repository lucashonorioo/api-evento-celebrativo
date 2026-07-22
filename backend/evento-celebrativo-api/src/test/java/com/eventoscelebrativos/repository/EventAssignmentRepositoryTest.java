package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventAssignmentRepositoryTest {

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistEventAssignmentWithEnumAsStringAndTimestamps() {
        CelebrationEvent event = saveEvent("Assignment Persistence Event");
        Reader reader = saveReader("Assignment Reader", "34973000001");

        EventAssignment assignment = eventAssignmentRepository.saveAndFlush(
                new EventAssignment(event, reader, EventAssignmentType.READER)
        );

        String storedType = jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE id = ?",
                String.class,
                assignment.getId()
        );

        assertNotNull(assignment.getId());
        assertEquals("READER", storedType);
        assertNotNull(assignment.getCreatedAt());
        assertNotNull(assignment.getUpdatedAt());
    }

    @Test
    void shouldFindAssignmentsByEventAndPerson() {
        CelebrationEvent event = saveEvent("Assignment Lookup Event");
        Reader reader = saveReader("Assignment Lookup Reader", "34973000002");
        EventAssignment assignment = eventAssignmentRepository.saveAndFlush(
                new EventAssignment(event, reader, EventAssignmentType.READER)
        );

        List<EventAssignment> byEvent = eventAssignmentRepository.findAllByEventId(event.getId());

        assertEquals(1, byEvent.size());
        assertEquals(assignment.getId(), byEvent.get(0).getId());
        assertEquals(
                assignment.getId(),
                eventAssignmentRepository.findByEventIdAndPersonId(event.getId(), reader.getId()).orElseThrow().getId()
        );
        assertTrue(eventAssignmentRepository.existsByEventIdAndPersonId(event.getId(), reader.getId()));
        assertFalse(eventAssignmentRepository.existsByEventIdAndPersonId(event.getId(), 999_999L));
    }

    @Test
    void shouldEnforceUniqueEventAndPerson() {
        CelebrationEvent event = saveEvent("Assignment Unique Event");
        Reader reader = saveReader("Assignment Unique Reader", "34973000003");
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, reader, EventAssignmentType.READER));

        assertThrows(DataIntegrityViolationException.class, () ->
                eventAssignmentRepository.saveAndFlush(
                        new EventAssignment(event, reader, EventAssignmentType.COMMENTATOR)
                ));
    }

    @Test
    void shouldAllowSamePersonInDifferentEvents() {
        CelebrationEvent firstEvent = saveEvent("Assignment First Event");
        CelebrationEvent secondEvent = saveEvent("Assignment Second Event");
        Reader reader = saveReader("Assignment Multi Event Reader", "34973000004");

        eventAssignmentRepository.save(new EventAssignment(firstEvent, reader, EventAssignmentType.READER));
        eventAssignmentRepository.save(new EventAssignment(secondEvent, reader, EventAssignmentType.READER));
        eventAssignmentRepository.flush();

        assertEquals(1, eventAssignmentRepository.findAllByEventId(firstEvent.getId()).size());
        assertEquals(1, eventAssignmentRepository.findAllByEventId(secondEvent.getId()).size());
    }

    @Test
    void shouldAllowSeveralPeopleInSameEvent() {
        CelebrationEvent event = saveEvent("Assignment Several People Event");
        Reader reader = saveReader("Assignment Several Reader", "34973000005");
        Priest priest = savePriest("Assignment Several Priest", "34973000006");

        eventAssignmentRepository.save(new EventAssignment(event, reader, EventAssignmentType.READER));
        eventAssignmentRepository.save(new EventAssignment(event, priest, EventAssignmentType.PRIEST));
        eventAssignmentRepository.flush();

        List<EventAssignmentType> types = eventAssignmentRepository.findAllByEventId(event.getId()).stream()
                .map(EventAssignment::getAssignmentType)
                .toList();

        assertEquals(2, types.size());
        assertTrue(types.contains(EventAssignmentType.READER));
        assertTrue(types.contains(EventAssignmentType.PRIEST));
    }

    @Test
    void shouldDeleteAllAssignmentsByEventWithoutDeletingEventOrPeople() {
        CelebrationEvent event = saveEvent("Assignment Delete Event");
        CelebrationEvent otherEvent = saveEvent("Assignment Delete Other Event");
        Reader reader = saveReader("Assignment Delete Reader", "34973000007");
        Priest priest = savePriest("Assignment Delete Priest", "34973000008");
        eventAssignmentRepository.save(new EventAssignment(event, reader, EventAssignmentType.READER));
        eventAssignmentRepository.save(new EventAssignment(event, priest, EventAssignmentType.PRIEST));
        eventAssignmentRepository.save(new EventAssignment(otherEvent, reader, EventAssignmentType.READER));
        eventAssignmentRepository.flush();

        eventAssignmentRepository.deleteAllByEventId(event.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(eventAssignmentRepository.findAllByEventId(event.getId()).isEmpty());
        assertEquals(1, eventAssignmentRepository.findAllByEventId(otherEvent.getId()).size());
        assertNotNull(entityManager.find(CelebrationEvent.class, event.getId()));
        assertNotNull(entityManager.find(Reader.class, reader.getId()));
        assertNotNull(entityManager.find(Priest.class, priest.getId()));
    }

    @Test
    void shouldNotCascadeDeleteEventOrPersonWhenDeletingAssignment() {
        CelebrationEvent event = saveEvent("Assignment Cascade Event");
        Reader reader = saveReader("Assignment Cascade Reader", "34973000009");
        EventAssignment assignment = eventAssignmentRepository.saveAndFlush(
                new EventAssignment(event, reader, EventAssignmentType.READER)
        );

        eventAssignmentRepository.delete(assignment);
        eventAssignmentRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(CelebrationEvent.class, event.getId()));
        assertNotNull(entityManager.find(Reader.class, reader.getId()));
    }

    private CelebrationEvent saveEvent(String name) {
        CelebrationEvent event = new CelebrationEvent(
                null,
                name,
                LocalDate.of(2026, 9, 1),
                LocalTime.of(19, 0),
                true
        );
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Reader saveReader(String name, String phoneNumber) {
        Reader reader = new Reader();
        populatePerson(reader, name, phoneNumber);
        entityManager.persist(reader);
        entityManager.flush();
        return reader;
    }

    private Priest savePriest(String name, String phoneNumber) {
        Priest priest = new Priest();
        populatePerson(priest, name, phoneNumber);
        entityManager.persist(priest);
        entityManager.flush();
        return priest;
    }

    private void populatePerson(Person person, String name, String phoneNumber) {
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
    }
}
