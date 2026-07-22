package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class EventAssignmentLegacyCompatibilityIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private PriestService priestService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldWriteThroughAssignmentsWhenCreatingEventWithScale() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest priest = savePriest("Assignment Create Priest");
            Reader reader = saveReader("Assignment Create Reader");
            Commentator commentator = saveCommentator("Assignment Create Commentator");
            MinisterOfTheWord ministerOfTheWord = saveMinisterOfTheWord("Assignment Create Word Minister");
            EucharisticMinister eucharisticMinister = saveEucharisticMinister("Assignment Create Eucharistic Minister");
            personIds = List.of(priest.getId(), reader.getId(), commentator.getId(), ministerOfTheWord.getId(), eucharisticMinister.getId());
            Location location = locationRepository.saveAndFlush(location("Assignment Create Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Assignment Create Mass",
                                    locationId,
                                    priest.getId(),
                                    List.of(reader.getId()),
                                    List.of(commentator.getId()),
                                    List.of(ministerOfTheWord.getId()),
                                    List.of(eucharisticMinister.getId())
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").isNumber())
                    .andExpect(jsonPath("$.priest.id").value(priest.getId()))
                    .andExpect(jsonPath("$.readers[0].id").value(reader.getId()))
                    .andExpect(jsonPath("$.commentators[0].id").value(commentator.getId()))
                    .andExpect(jsonPath("$.ministersOfTheWord[0].id").value(ministerOfTheWord.getId()))
                    .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(eucharisticMinister.getId()))
                    .andExpect(jsonPath("$.assignments").doesNotExist())
                    .andReturn();

            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();

            assertEquals(Set.copyOf(personIds), Set.copyOf(eventPersonIds(eventId)));
            assertEventAssignmentPeopleMatchEventPeople(eventId);
            assertAssignmentType(eventId, priest.getId(), EventAssignmentType.PRIEST);
            assertAssignmentType(eventId, reader.getId(), EventAssignmentType.READER);
            assertAssignmentType(eventId, commentator.getId(), EventAssignmentType.COMMENTATOR);
            assertAssignmentType(eventId, ministerOfTheWord.getId(), EventAssignmentType.MINISTER_OF_THE_WORD);
            assertAssignmentType(eventId, eucharisticMinister.getId(), EventAssignmentType.EUCHARISTIC_MINISTER);
            assertEquals(5, countEventAssignments(eventId));
            assertEquals(0, countDuplicatedAssignments(eventId));
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldSynchronizeAssignmentsIncrementallyWhenUpdatingEventScale() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest oldPriest = savePriest("Assignment Update Old Priest");
            Priest newPriest = savePriest("Assignment Update New Priest");
            Reader keptReader = saveReader("Assignment Update Kept Reader");
            Reader removedReader = saveReader("Assignment Update Removed Reader");
            Commentator addedCommentator = saveCommentator("Assignment Update Added Commentator");
            MinisterOfTheWord oldWordMinister = saveMinisterOfTheWord("Assignment Update Old Word Minister");
            MinisterOfTheWord newWordMinister = saveMinisterOfTheWord("Assignment Update New Word Minister");
            EucharisticMinister oldEucharisticMinister = saveEucharisticMinister("Assignment Update Old Eucharistic Minister");
            EucharisticMinister newEucharisticMinister = saveEucharisticMinister("Assignment Update New Eucharistic Minister");
            personIds = List.of(
                    oldPriest.getId(),
                    newPriest.getId(),
                    keptReader.getId(),
                    removedReader.getId(),
                    addedCommentator.getId(),
                    oldWordMinister.getId(),
                    newWordMinister.getId(),
                    oldEucharisticMinister.getId(),
                    newEucharisticMinister.getId()
            );
            Location location = locationRepository.saveAndFlush(location("Assignment Update Church"));
            locationId = location.getId();

            MvcResult createResult = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Assignment Update Mass",
                                    locationId,
                                    oldPriest.getId(),
                                    List.of(keptReader.getId(), removedReader.getId()),
                                    null,
                                    List.of(oldWordMinister.getId()),
                                    List.of(oldEucharisticMinister.getId())
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("eventId").asLong();
            AssignmentSnapshot keptReaderBefore = assignmentSnapshot(eventId, keptReader.getId());
            AssignmentSnapshot oldPriestBefore = assignmentSnapshot(eventId, oldPriest.getId());

            mockMvc.perform(put("/eventos/{id}/escala", eventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId,
                                    newPriest.getId(),
                                    List.of(keptReader.getId()),
                                    List.of(addedCommentator.getId()),
                                    List.of(newWordMinister.getId()),
                                    List.of(newEucharisticMinister.getId())
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priest.id").value(newPriest.getId()))
                    .andExpect(jsonPath("$.readers[0].id").value(keptReader.getId()))
                    .andExpect(jsonPath("$.commentators[0].id").value(addedCommentator.getId()))
                    .andExpect(jsonPath("$.ministersOfTheWord[0].id").value(newWordMinister.getId()))
                    .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(newEucharisticMinister.getId()))
                    .andExpect(jsonPath("$.assignments").doesNotExist());

            Set<Long> expectedPeople = Set.of(
                    newPriest.getId(),
                    keptReader.getId(),
                    addedCommentator.getId(),
                    newWordMinister.getId(),
                    newEucharisticMinister.getId()
            );
            assertEquals(expectedPeople, Set.copyOf(eventPersonIds(eventId)));
            assertEventAssignmentPeopleMatchEventPeople(eventId);

            AssignmentSnapshot keptReaderAfter = assignmentSnapshot(eventId, keptReader.getId());
            assertEquals(keptReaderBefore.id(), keptReaderAfter.id());
            assertEquals(keptReaderBefore.createdAt(), keptReaderAfter.createdAt());
            assertFalse(hasAssignment(eventId, oldPriest.getId()));
            assertFalse(hasAssignment(eventId, removedReader.getId()));
            assertFalse(hasAssignment(eventId, oldWordMinister.getId()));
            assertFalse(hasAssignment(eventId, oldEucharisticMinister.getId()));
            assertNotEquals(oldPriestBefore.id(), assignmentSnapshot(eventId, newPriest.getId()).id());
            assertAssignmentType(eventId, newPriest.getId(), EventAssignmentType.PRIEST);
            assertAssignmentType(eventId, keptReader.getId(), EventAssignmentType.READER);
            assertAssignmentType(eventId, addedCommentator.getId(), EventAssignmentType.COMMENTATOR);
            assertAssignmentType(eventId, newWordMinister.getId(), EventAssignmentType.MINISTER_OF_THE_WORD);
            assertAssignmentType(eventId, newEucharisticMinister.getId(), EventAssignmentType.EUCHARISTIC_MINISTER);
            assertEquals(0, countDuplicatedAssignments(eventId));

            mockMvc.perform(get("/eventos/{id}/escala", eventId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priest.id").value(newPriest.getId()))
                    .andExpect(jsonPath("$.readers[0].id").value(keptReader.getId()))
                    .andExpect(jsonPath("$.commentators[0].id").value(addedCommentator.getId()))
                    .andExpect(jsonPath("$.ministersOfTheWord[0].id").value(newWordMinister.getId()))
                    .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(newEucharisticMinister.getId()))
                    .andExpect(jsonPath("$.assignments").doesNotExist());
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteAssignmentsWhenDeletingEvent() throws Exception {
        Long eventId = null;
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priest = savePriest("Assignment Delete Event Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Delete Event Church"));
            locationId = location.getId();
            eventId = createEventWithPriest("Assignment Delete Event Mass", locationId, priestId);

            mockMvc.perform(delete("/eventos/{id}", eventId))
                    .andExpect(status().isNoContent());

            assertEquals(0, countRows("tb_celebration_event", "id", eventId));
            assertEquals(0, countRows("tb_event_person", "event_id", eventId));
            assertEquals(0, countEventAssignments(eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackAssignmentsWhenEventDeletionFailsAfterAssignmentRemoval() {
        Long eventId = null;
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priest = savePriest("Assignment Delete Rollback Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Delete Rollback Church"));
            locationId = location.getId();
            eventId = createEventWithPriest("Assignment Delete Rollback Mass", locationId, priestId);
            int assignmentsBefore = countEventAssignments(eventId);
            int eventPeopleBefore = countRows("tb_event_person", "event_id", eventId);
            createEventDeleteBlocker(eventId);

            Long savedEventId = eventId;
            assertThrows(DatabaseException.class, () -> celebrationEventService.deleteEventById(savedEventId));

            assertEquals(1, countRows("tb_celebration_event", "id", eventId));
            assertEquals(eventPeopleBefore, countRows("tb_event_person", "event_id", eventId));
            assertEquals(assignmentsBefore, countEventAssignments(eventId));
        } finally {
            dropEventDeleteBlocker();
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldPreserveAssignmentsWhenDeletingLinkedPersonFails() {
        Long eventId = null;
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priest = savePriest("Assignment Linked Person Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Assignment Linked Person Church"));
            locationId = location.getId();
            eventId = createEventWithPriest("Assignment Linked Person Mass", locationId, priestId);
            int ministriesBefore = countRows("tb_person_ministry", "person_id", priestId);
            int assignmentsBefore = countRows("tb_event_assignment", "person_id", priestId);
            int eventPeopleBefore = countRows("tb_event_person", "person_id", priestId);

            Long savedPriestId = priestId;
            assertThrows(DatabaseException.class, () -> priestService.deletePriestById(savedPriestId));

            assertTrue(personRepository.existsById(priestId));
            assertEquals(ministriesBefore, countRows("tb_person_ministry", "person_id", priestId));
            assertEquals(assignmentsBefore, countRows("tb_event_assignment", "person_id", priestId));
            assertEquals(eventPeopleBefore, countRows("tb_event_person", "person_id", priestId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    private Long createEventWithPriest(String name, Long locationId, Long priestId) {
        return celebrationEventService.createEventWithScale(
                eventRequest(name, locationId, priestId, null, null, null, null)
        ).getEventId();
    }

    private Priest savePriest(String name) {
        Priest priest = new Priest();
        populatePerson(priest, name);
        priest = (Priest) personRepository.saveAndFlush(priest);
        personMinistryRepository.saveAndFlush(new PersonMinistry(priest, MinistryType.PRIEST));
        return priest;
    }

    private Reader saveReader(String name) {
        Reader reader = new Reader();
        populatePerson(reader, name);
        reader = (Reader) personRepository.saveAndFlush(reader);
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));
        return reader;
    }

    private Commentator saveCommentator(String name) {
        Commentator commentator = new Commentator();
        populatePerson(commentator, name);
        commentator = (Commentator) personRepository.saveAndFlush(commentator);
        personMinistryRepository.saveAndFlush(new PersonMinistry(commentator, MinistryType.COMMENTATOR));
        return commentator;
    }

    private MinisterOfTheWord saveMinisterOfTheWord(String name) {
        MinisterOfTheWord minister = new MinisterOfTheWord();
        populatePerson(minister, name);
        minister = (MinisterOfTheWord) personRepository.saveAndFlush(minister);
        personMinistryRepository.saveAndFlush(new PersonMinistry(minister, MinistryType.MINISTER_OF_THE_WORD));
        return minister;
    }

    private EucharisticMinister saveEucharisticMinister(String name) {
        EucharisticMinister minister = new EucharisticMinister();
        populatePerson(minister, name);
        minister = (EucharisticMinister) personRepository.saveAndFlush(minister);
        personMinistryRepository.saveAndFlush(new PersonMinistry(minister, MinistryType.EUCHARISTIC_MINISTER));
        return minister;
    }

    private void populatePerson(Person person, String name) {
        person.setName(name + " " + UUID.randomUUID());
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
    }

    private Location location(String name) {
        return new Location(null, name + " " + UUID.randomUUID(), "Rua Teste, 123");
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            String name,
            Long locationId,
            Long priestId,
            List<Long> readerIds,
            List<Long> commentatorIds,
            List<Long> ministerOfTheWordIds,
            List<Long> eucharisticMinisterIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent(name + " " + UUID.randomUUID());
        request.setEventDate(LocalDate.now().plusDays(30));
        request.setEventTime(LocalTime.of(19, 0));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setPriestId(priestId);
        request.setReaderIds(readerIds);
        request.setCommentatorIds(commentatorIds);
        request.setMinisterOfTheWordIds(ministerOfTheWordIds);
        request.setEucharisticMinisterIds(eucharisticMinisterIds);
        return request;
    }

    private CelebrationEventScaleRequestDTO scaleRequest(
            Long locationId,
            Long priestId,
            List<Long> readerIds,
            List<Long> commentatorIds,
            List<Long> ministerOfTheWordIds,
            List<Long> eucharisticMinisterIds
    ) {
        return new CelebrationEventScaleRequestDTO(
                locationId,
                priestId,
                readerIds,
                commentatorIds,
                ministerOfTheWordIds,
                eucharisticMinisterIds
        );
    }

    private List<Long> eventPersonIds(Long eventId) {
        return jdbcTemplate.queryForList(
                "SELECT person_id FROM tb_event_person WHERE event_id = ? ORDER BY person_id",
                Long.class,
                eventId
        );
    }

    private List<Long> eventAssignmentPersonIds(Long eventId) {
        return jdbcTemplate.queryForList(
                "SELECT person_id FROM tb_event_assignment WHERE event_id = ? ORDER BY person_id",
                Long.class,
                eventId
        );
    }

    private void assertEventAssignmentPeopleMatchEventPeople(Long eventId) {
        assertEquals(eventPersonIds(eventId), eventAssignmentPersonIds(eventId));
    }

    private void assertAssignmentType(Long eventId, Long personId, EventAssignmentType assignmentType) {
        String actual = jdbcTemplate.queryForObject(
                """
                SELECT assignment_type
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                String.class,
                eventId,
                personId
        );
        assertEquals(assignmentType.name(), actual);
    }

    private AssignmentSnapshot assignmentSnapshot(Long eventId, Long personId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, assignment_type, created_at, updated_at
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                (rs, rowNum) -> new AssignmentSnapshot(
                        rs.getLong("id"),
                        EventAssignmentType.valueOf(rs.getString("assignment_type")),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                ),
                eventId,
                personId
        );
    }

    private boolean hasAssignment(Long eventId, Long personId) {
        return countEventAssignments(eventId, personId) > 0;
    }

    private int countEventAssignments(Long eventId) {
        return countRows("tb_event_assignment", "event_id", eventId);
    }

    private int countEventAssignments(Long eventId, Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_event_assignment
                WHERE event_id = ?
                  AND person_id = ?
                """,
                Integer.class,
                eventId,
                personId
        );
        return count == null ? 0 : count;
    }

    private int countDuplicatedAssignments(Long eventId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT person_id
                    FROM tb_event_assignment
                    WHERE event_id = ?
                    GROUP BY person_id
                    HAVING COUNT(*) > 1
                ) duplicates
                """,
                Integer.class,
                eventId
        );
        return count == null ? 0 : count;
    }

    private int countRows(String table, String column, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private void createEventDeleteBlocker(Long eventId) {
        dropEventDeleteBlocker();
        jdbcTemplate.execute("""
                CREATE TABLE tb_event_delete_blocker (
                    event_id BIGINT NOT NULL,
                    CONSTRAINT fk_event_delete_blocker_event
                        FOREIGN KEY (event_id)
                        REFERENCES tb_celebration_event(id)
                )
                """);
        jdbcTemplate.update("INSERT INTO tb_event_delete_blocker(event_id) VALUES (?)", eventId);
    }

    private void dropEventDeleteBlocker() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS tb_event_delete_blocker");
    }

    private void cleanupEvent(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private void cleanupLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE location_id = ?", locationId);
        jdbcTemplate.update("DELETE FROM tb_location WHERE id = ?", locationId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }

    private record AssignmentSnapshot(
            Long id,
            EventAssignmentType assignmentType,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }
}
