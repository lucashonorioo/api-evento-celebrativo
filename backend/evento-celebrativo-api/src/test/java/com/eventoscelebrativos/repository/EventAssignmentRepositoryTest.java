package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    void shouldFindEventAssignmentsWithPeopleForEventOrderedDeterministically() {
        CelebrationEvent event = saveEvent("Assignment Read Event");
        saveAssignment(event, saveEucharisticMinister("Zelia Minister", "34973000010"), EventAssignmentType.EUCHARISTIC_MINISTER);
        saveAssignment(event, saveReader("Bruno Reader", "34973000011"), EventAssignmentType.READER);
        saveAssignment(event, savePriest("Carlos Priest", "34973000012"), EventAssignmentType.PRIEST);
        saveAssignment(event, saveCommentator("Ana Commentator", "34973000013"), EventAssignmentType.COMMENTATOR);
        saveAssignment(event, saveMinisterOfTheWord("Dora Word", "34973000014"), EventAssignmentType.MINISTER_OF_THE_WORD);
        entityManager.flush();
        entityManager.clear();

        List<EventAssignment> result = eventAssignmentRepository.findAllByEventIdWithPerson(event.getId());

        assertEquals(5, result.size());
        assertEquals(
                result.stream().sorted(repositoryOrder()).map(EventAssignment::getId).toList(),
                result.stream().map(EventAssignment::getId).toList()
        );
        assertTrue(result.stream().allMatch(assignment -> assignment.getPerson().getName() != null));
    }

    @Test
    void shouldReturnEmptyListForEventWithoutAssignments() {
        CelebrationEvent event = saveEvent("Assignment Empty Event");
        entityManager.flush();
        entityManager.clear();

        assertTrue(eventAssignmentRepository.findAllByEventIdWithPerson(event.getId()).isEmpty());
    }

    @Test
    void shouldFindAssignmentsForSeveralEventsInSingleBatchQueryWithoutPersonNPlusOne() {
        CelebrationEvent firstEvent = saveEvent("Assignment Batch First Event");
        CelebrationEvent secondEvent = saveEvent("Assignment Batch Second Event");
        Reader sharedReader = saveReader("Assignment Shared Reader", "34973000015");
        Reader otherReader = saveReader("Assignment Other Reader", "34973000016");
        saveAssignment(firstEvent, sharedReader, EventAssignmentType.READER);
        saveAssignment(secondEvent, sharedReader, EventAssignmentType.READER);
        saveAssignment(secondEvent, otherReader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = hibernateStatistics();
        statistics.clear();

        List<EventAssignment> result = eventAssignmentRepository.findAllByEventIdInWithPerson(
                List.of(secondEvent.getId(), firstEvent.getId())
        );
        result.forEach(assignment -> assertNotNull(assignment.getPerson().getName()));

        assertEquals(3, result.size());
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(List.of(firstEvent.getId(), secondEvent.getId(), secondEvent.getId()),
                result.stream().map(assignment -> assignment.getEvent().getId()).toList());
    }

    @Test
    void shouldNotDuplicateAssignmentsWhenReadingInBatch() {
        CelebrationEvent event = saveEvent("Assignment Batch Duplicate Event");
        Reader reader = saveReader("Assignment Batch Duplicate Reader", "34973000017");
        Priest priest = savePriest("Assignment Batch Duplicate Priest", "34973000018");
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveAssignment(event, priest, EventAssignmentType.PRIEST);
        entityManager.flush();
        entityManager.clear();

        List<Long> assignmentIds = eventAssignmentRepository.findAllByEventIdInWithPerson(List.of(event.getId())).stream()
                .map(EventAssignment::getId)
                .toList();

        assertEquals(assignmentIds.stream().distinct().count(), assignmentIds.size());
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

    private Commentator saveCommentator(String name, String phoneNumber) {
        Commentator commentator = new Commentator();
        populatePerson(commentator, name, phoneNumber);
        entityManager.persist(commentator);
        entityManager.flush();
        return commentator;
    }

    private MinisterOfTheWord saveMinisterOfTheWord(String name, String phoneNumber) {
        MinisterOfTheWord ministerOfTheWord = new MinisterOfTheWord();
        populatePerson(ministerOfTheWord, name, phoneNumber);
        entityManager.persist(ministerOfTheWord);
        entityManager.flush();
        return ministerOfTheWord;
    }

    private EucharisticMinister saveEucharisticMinister(String name, String phoneNumber) {
        EucharisticMinister eucharisticMinister = new EucharisticMinister();
        populatePerson(eucharisticMinister, name, phoneNumber);
        entityManager.persist(eucharisticMinister);
        entityManager.flush();
        return eucharisticMinister;
    }

    private EventAssignment saveAssignment(CelebrationEvent event, Person person, EventAssignmentType assignmentType) {
        EventAssignment assignment = new EventAssignment(event, person, assignmentType);
        entityManager.persist(assignment);
        return assignment;
    }

    private void populatePerson(Person person, String name, String phoneNumber) {
        person.setName(name);
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        person.setPassword("encoded-password");
    }

    private Statistics hibernateStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
    }

    private Comparator<EventAssignment> repositoryOrder() {
        return Comparator
                .comparing((EventAssignment assignment) -> assignment.getEvent().getId())
                .thenComparing(assignment -> assignment.getAssignmentType().name())
                .thenComparing(assignment -> assignment.getPerson().getName().toLowerCase())
                .thenComparing(assignment -> assignment.getPerson().getId())
                .thenComparing(EventAssignment::getId);
    }
}
