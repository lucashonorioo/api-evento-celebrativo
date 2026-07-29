package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldPersistEventAssignmentWithEnumAsStringAndTimestamps() {
        CelebrationEvent event = saveEvent("Assignment Persistence Event");
        Person reader = savePerson("Assignment Reader", "34973000001");

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
        Person reader = savePerson("Assignment Lookup Reader", "34973000002");
        EventAssignment assignment = eventAssignmentRepository.saveAndFlush(
                new EventAssignment(event, reader, EventAssignmentType.READER)
        );

        List<EventAssignment> byEvent = eventAssignmentRepository.findAllByEventId(event.getId());

        assertEquals(1, byEvent.size());
        assertEquals(assignment.getId(), byEvent.get(0).getId());
    }

    @Test
    void shouldAllowSamePersonInDifferentAssignmentTypesForSameEvent() {
        CelebrationEvent event = saveEvent("Assignment Multi Function Event");
        Person reader = savePerson("Assignment Multi Function Reader", "34973000003");
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, reader, EventAssignmentType.READER));

        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, reader, EventAssignmentType.COMMENTATOR));

        assertEquals(2, eventAssignmentRepository.findAllByEventId(event.getId()).size());
    }

    @Test
    void shouldEnforceUniqueEventPersonAndAssignmentType() {
        CelebrationEvent event = saveEvent("Assignment Unique Event");
        Person reader = savePerson("Assignment Unique Reader", "34973000103");
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, reader, EventAssignmentType.READER));

        assertThrows(DataIntegrityViolationException.class, () ->
                eventAssignmentRepository.saveAndFlush(
                        new EventAssignment(event, reader, EventAssignmentType.READER)
                ));
    }

    @Test
    void shouldAllowSamePersonInDifferentEvents() {
        CelebrationEvent firstEvent = saveEvent("Assignment First Event");
        CelebrationEvent secondEvent = saveEvent("Assignment Second Event");
        Person reader = savePerson("Assignment Multi Event Reader", "34973000004");

        eventAssignmentRepository.save(new EventAssignment(firstEvent, reader, EventAssignmentType.READER));
        eventAssignmentRepository.save(new EventAssignment(secondEvent, reader, EventAssignmentType.READER));
        eventAssignmentRepository.flush();

        assertEquals(1, eventAssignmentRepository.findAllByEventId(firstEvent.getId()).size());
        assertEquals(1, eventAssignmentRepository.findAllByEventId(secondEvent.getId()).size());
    }

    @Test
    void shouldAllowSeveralPeopleInSameEvent() {
        CelebrationEvent event = saveEvent("Assignment Several People Event");
        Person reader = savePerson("Assignment Several Reader", "34973000005");
        Person priest = savePerson("Assignment Several Priest", "34973000006");

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
        Person reader = savePerson("Assignment Delete Reader", "34973000007");
        Person priest = savePerson("Assignment Delete Priest", "34973000008");
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
        assertNotNull(entityManager.find(Person.class, reader.getId()));
        assertNotNull(entityManager.find(Person.class, priest.getId()));
    }

    @Test
    void shouldNotCascadeDeleteEventOrPersonWhenDeletingAssignment() {
        CelebrationEvent event = saveEvent("Assignment Cascade Event");
        Person reader = savePerson("Assignment Cascade Reader", "34973000009");
        EventAssignment assignment = eventAssignmentRepository.saveAndFlush(
                new EventAssignment(event, reader, EventAssignmentType.READER)
        );

        eventAssignmentRepository.delete(assignment);
        eventAssignmentRepository.flush();
        entityManager.clear();

        assertNotNull(entityManager.find(CelebrationEvent.class, event.getId()));
        assertNotNull(entityManager.find(Person.class, reader.getId()));
    }

    @Test
    void shouldFindEventAssignmentsWithPeopleForEventOrderedDeterministically() {
        CelebrationEvent event = saveEvent("Assignment Read Event");
        saveAssignment(event, savePerson("Zelia Minister", "34973000010"), EventAssignmentType.EUCHARISTIC_MINISTER);
        saveAssignment(event, savePerson("Bruno Reader", "34973000011"), EventAssignmentType.READER);
        saveAssignment(event, savePerson("Carlos Priest", "34973000012"), EventAssignmentType.PRIEST);
        saveAssignment(event, savePerson("Ana Commentator", "34973000013"), EventAssignmentType.COMMENTATOR);
        saveAssignment(event, savePerson("Dora Word", "34973000014"), EventAssignmentType.MINISTER_OF_THE_WORD);
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
        Person sharedReader = savePerson("Assignment Shared Reader", "34973000015");
        Person otherReader = savePerson("Assignment Other Reader", "34973000016");
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
        Person reader = savePerson("Assignment Batch Duplicate Reader", "34973000017");
        Person priest = savePerson("Assignment Batch Duplicate Priest", "34973000018");
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveAssignment(event, priest, EventAssignmentType.PRIEST);
        entityManager.flush();
        entityManager.clear();

        List<Long> assignmentIds = eventAssignmentRepository.findAllByEventIdInWithPerson(List.of(event.getId())).stream()
                .map(EventAssignment::getId)
                .toList();

        assertEquals(assignmentIds.stream().distinct().count(), assignmentIds.size());
    }

    @Test
    void shouldReturnOnlyEventsOfGivenPerson() {
        Person reader = savePerson("Schedule Reader", "34974000001");
        Person other = savePerson("Schedule Other", "34974000002");
        CelebrationEvent ownEvent = saveEvent("Schedule Own Event", LocalDate.of(2026, 8, 10), LocalTime.of(10, 0));
        CelebrationEvent otherEvent = saveEvent("Schedule Other Event", LocalDate.of(2026, 8, 11), LocalTime.of(10, 0));
        saveAssignment(ownEvent, reader, EventAssignmentType.READER);
        saveAssignment(otherEvent, other, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.getTotalElements());
        assertEquals(ownEvent.getId(), result.getContent().get(0).getEventId());
    }

    @Test
    void shouldExcludeEventBelongingOnlyToAnotherPerson() {
        Person reader = savePerson("Schedule Isolation Reader", "34974000003");
        Person other = savePerson("Schedule Isolation Other", "34974000004");
        CelebrationEvent otherEvent = saveEvent("Schedule Isolation Event", LocalDate.of(2026, 8, 12), LocalTime.of(10, 0));
        saveAssignment(otherEvent, other, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldIncludeEventOnStartDateBoundary() {
        Person reader = savePerson("Schedule Start Boundary Reader", "34974000005");
        CelebrationEvent event = saveEvent("Schedule Start Boundary Event", LocalDate.of(2026, 8, 1), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldIncludeEventOnEndDateBoundary() {
        Person reader = savePerson("Schedule End Boundary Reader", "34974000006");
        CelebrationEvent event = saveEvent("Schedule End Boundary Event", LocalDate.of(2026, 8, 31), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldExcludeEventBeforePeriod() {
        Person reader = savePerson("Schedule Before Reader", "34974000007");
        CelebrationEvent event = saveEvent("Schedule Before Event", LocalDate.of(2026, 7, 31), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeEventAfterPeriod() {
        Person reader = savePerson("Schedule After Reader", "34974000008");
        CelebrationEvent event = saveEvent("Schedule After Event", LocalDate.of(2026, 9, 1), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldOrderScheduleEventsByDateThenTime() {
        Person reader = savePerson("Schedule Order Reader", "34974000009");
        CelebrationEvent later = saveEvent("Schedule Order Later Event", LocalDate.of(2026, 8, 10), LocalTime.of(19, 0));
        CelebrationEvent earlierSameDay = saveEvent("Schedule Order Earlier Event", LocalDate.of(2026, 8, 10), LocalTime.of(8, 0));
        CelebrationEvent earliestDate = saveEvent("Schedule Order Earliest Event", LocalDate.of(2026, 8, 5), LocalTime.of(23, 0));
        saveAssignment(later, reader, EventAssignmentType.READER);
        saveAssignment(earlierSameDay, reader, EventAssignmentType.READER);
        saveAssignment(earliestDate, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 0, 10);

        assertEquals(
                List.of(earliestDate.getId(), earlierSameDay.getId(), later.getId()),
                result.getContent().stream().map(PersonScheduleEventProjection::getEventId).toList()
        );
    }

    @Test
    void shouldTieBreakByEventIdWhenDateAndTimeAreEqual() {
        Person reader = savePerson("Schedule TieBreak Reader", "34974000010");
        LocalDate date = LocalDate.of(2026, 8, 15);
        LocalTime time = LocalTime.of(10, 0);
        CelebrationEvent second = saveEvent("Schedule TieBreak Second Event", date, time);
        CelebrationEvent first = saveEvent("Schedule TieBreak First Event", date, time);
        saveAssignment(second, reader, EventAssignmentType.READER);
        saveAssignment(first, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, date, date, 0, 10);

        List<Long> orderedByIdAsc = List.of(second.getId(), first.getId()).stream().sorted().toList();
        assertEquals(orderedByIdAsc, result.getContent().stream().map(PersonScheduleEventProjection::getEventId).toList());
    }

    @Test
    void shouldPaginateByDistinctEventNotByAssignmentRowAndCountDistinctEvents() {
        Person reader = savePerson("Schedule Pagination Reader", "34974000011");
        CelebrationEvent multiFunctionEvent = saveEvent("Schedule Pagination MultiFunction Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        CelebrationEvent secondEvent = saveEvent("Schedule Pagination Second Event", LocalDate.of(2026, 8, 6), LocalTime.of(10, 0));
        saveAssignment(multiFunctionEvent, reader, EventAssignmentType.READER);
        saveAssignment(multiFunctionEvent, reader, EventAssignmentType.COMMENTATOR);
        saveAssignment(secondEvent, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> firstPage = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 0, 1);

        assertEquals(2, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(1, firstPage.getContent().size());
        assertEquals(multiFunctionEvent.getId(), firstPage.getContent().get(0).getEventId());

        Page<PersonScheduleEventProjection> secondPage = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 1, 1);

        assertEquals(1, secondPage.getContent().size());
        assertEquals(secondEvent.getId(), secondPage.getContent().get(0).getEventId());
    }

    @Test
    void shouldReturnEmptyPageWhenNoAssignmentInPeriod() {
        Person reader = savePerson("Schedule Empty Reader", "34974000012");

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldReturnEmptyPageWhenRequestingPageBeyondTotal() {
        Person reader = savePerson("Schedule Beyond Reader", "34974000013");
        CelebrationEvent event = saveEvent("Schedule Beyond Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5, 10);

        assertTrue(result.getContent().isEmpty());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldReturnLocationOfEvent() {
        Person reader = savePerson("Schedule Location Reader", "34974000014");
        CelebrationEvent event = saveEvent("Schedule Location Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        Location location = saveLocation("Schedule Location Church");
        attachLocation(event, location);
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        PersonScheduleEventProjection result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .getContent().get(0);

        assertEquals(location.getId(), result.getLocationId());
        assertEquals("Schedule Location Church", result.getLocationName());
    }

    @Test
    void shouldReturnNullLocationWhenEventHasNoLocation() {
        Person reader = savePerson("Schedule No Location Reader", "34974000015");
        CelebrationEvent event = saveEvent("Schedule No Location Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        entityManager.flush();
        entityManager.clear();

        PersonScheduleEventProjection result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .getContent().get(0);

        assertNull(result.getLocationId());
        assertNull(result.getLocationName());
    }

    @Test
    void shouldGroupBothAssignmentTypesOfSamePersonInSameEvent() {
        Person reader = savePerson("Schedule MultiFunction Reader", "34974000016");
        CelebrationEvent event = saveEvent("Schedule MultiFunction Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveAssignment(event, reader, EventAssignmentType.COMMENTATOR);
        entityManager.flush();
        entityManager.clear();

        List<PersonScheduleAssignmentProjection> assignments = eventAssignmentRepository
                .findAssignmentTypesByPersonIdAndEventIdIn(reader.getId(), List.of(event.getId()));

        List<String> types = assignments.stream().map(PersonScheduleAssignmentProjection::getAssignmentType).sorted().toList();
        assertEquals(List.of("COMMENTATOR", "READER"), types);
    }

    @Test
    void shouldNotReturnAssignmentsOfOtherPeopleInSameEvent() {
        Person reader = savePerson("Schedule Own Assignment Reader", "34974000017");
        Person otherPerson = savePerson("Schedule Own Assignment Other", "34974000018");
        CelebrationEvent event = saveEvent("Schedule Own Assignment Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        saveAssignment(event, otherPerson, EventAssignmentType.PRIEST);
        entityManager.flush();
        entityManager.clear();

        List<PersonScheduleAssignmentProjection> assignments = eventAssignmentRepository
                .findAssignmentTypesByPersonIdAndEventIdIn(reader.getId(), List.of(event.getId()));

        assertEquals(1, assignments.size());
        assertEquals("READER", assignments.get(0).getAssignmentType());
    }

    @Test
    void shouldNotDependOnActivePersonMinistryToReturnSchedule() {
        Person reader = savePerson("Schedule Legacy Ministry Reader", "34974000019");
        CelebrationEvent event = saveEvent("Schedule Legacy Ministry Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        saveAssignment(event, reader, EventAssignmentType.READER);
        PersonMinistry ministry = personMinistryRepository.save(new PersonMinistry(reader, MinistryType.READER));
        ministry.deactivate();
        personMinistryRepository.save(ministry);
        entityManager.flush();
        entityManager.clear();

        Page<PersonScheduleEventProjection> result = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldAvoidNPlusOneWhenLoadingScheduleEventsAndAssignments() {
        Person reader = savePerson("Schedule NPlusOne Reader", "34974000020");
        CelebrationEvent firstEvent = saveEvent("Schedule NPlusOne First Event", LocalDate.of(2026, 8, 5), LocalTime.of(10, 0));
        CelebrationEvent secondEvent = saveEvent("Schedule NPlusOne Second Event", LocalDate.of(2026, 8, 6), LocalTime.of(10, 0));
        saveAssignment(firstEvent, reader, EventAssignmentType.READER);
        saveAssignment(secondEvent, reader, EventAssignmentType.COMMENTATOR);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = hibernateStatistics();
        statistics.clear();

        Page<PersonScheduleEventProjection> eventPage = findSchedule(reader, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        List<Long> eventIds = eventPage.getContent().stream().map(PersonScheduleEventProjection::getEventId).toList();
        Map<Long, List<String>> assignmentsByEvent = eventAssignmentRepository
                .findAssignmentTypesByPersonIdAndEventIdIn(reader.getId(), eventIds).stream()
                .collect(Collectors.groupingBy(
                        PersonScheduleAssignmentProjection::getEventId,
                        Collectors.mapping(PersonScheduleAssignmentProjection::getAssignmentType, Collectors.toList())
                ));

        assertEquals(2, assignmentsByEvent.size());
        assertTrue(statistics.getPrepareStatementCount() <= 3);
    }

    private Page<PersonScheduleEventProjection> findSchedule(Person person, LocalDate startDate, LocalDate endDate) {
        return findSchedule(person, startDate, endDate, 0, 10);
    }

    private Page<PersonScheduleEventProjection> findSchedule(Person person, LocalDate startDate, LocalDate endDate, int page, int size) {
        return eventAssignmentRepository.findScheduleEventsByPersonId(
                person.getId(),
                startDate,
                endDate,
                PageRequest.of(page, size)
        );
    }

    private Location saveLocation(String churchName) {
        Location location = new Location(null, churchName, "Address " + churchName);
        entityManager.persist(location);
        entityManager.flush();
        return location;
    }

    private void attachLocation(CelebrationEvent event, Location location) {
        event.getLocations().add(location);
        entityManager.persist(event);
        entityManager.flush();
    }

    private CelebrationEvent saveEvent(String name) {
        return saveEvent(name, LocalDate.of(2026, 9, 1), LocalTime.of(19, 0));
    }

    private CelebrationEvent saveEvent(String name, LocalDate eventDate, LocalTime eventTime) {
        CelebrationEvent event = new CelebrationEvent(
                null,
                name,
                eventDate,
                eventTime,
                true
        );
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Person savePerson(String name, String phoneNumber) {
        Person person = new Person();
        populatePerson(person, name, phoneNumber);
        entityManager.persist(person);
        entityManager.flush();
        return person;
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
