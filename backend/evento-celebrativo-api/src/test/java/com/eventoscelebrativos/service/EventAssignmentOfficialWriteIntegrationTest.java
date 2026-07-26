package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que, apos EventAssignment virar a fonte oficial da escrita de escala, o espelho legado
 * (tb_event_person) permanece consistente com o estado oficial: auditoria sem divergencias,
 * no-op preservando identidade dos assignments, e leitura LEGACY por override ainda semanticamente
 * equivalente a leitura oficial.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class EventAssignmentOfficialWriteIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CelebrationEventService celebrationEventService;

    @Autowired
    private EventAssignmentReadService eventAssignmentReadService;

    @MockitoSpyBean
    private EventAssignmentCompatibilityService eventAssignmentCompatibilityService;

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
    void shouldNotWriteLegacyMirrorRowsWhenCreatingAndUpdatingScale() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest priest = savePriest("No Mirror Priest");
            Reader reader = saveReader("No Mirror Reader");
            Commentator commentator = saveCommentator("No Mirror Commentator");
            MinisterOfTheWord ministerOfTheWord = saveMinisterOfTheWord("No Mirror Word Minister");
            EucharisticMinister eucharisticMinister = saveEucharisticMinister("No Mirror Eucharistic Minister");
            personIds = List.of(priest.getId(), reader.getId(), commentator.getId(), ministerOfTheWord.getId(), eucharisticMinister.getId());
            Location location = locationRepository.saveAndFlush(location("No Mirror Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "No Mirror Mass", locationId, priest.getId(),
                                    List.of(reader.getId()), List.of(commentator.getId()),
                                    List.of(ministerOfTheWord.getId()), List.of(eucharisticMinister.getId())
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();

            assertEquals(0, countRows("tb_event_person", "event_id", eventId));
            assertEquals(5, countRows("tb_event_assignment", "event_id", eventId));

            Long finalEventId = eventId;
            mockMvc.perform(put("/eventos/{id}/escala", finalEventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId, priest.getId(), List.of(reader.getId()), null, null, null
                            ))))
                    .andExpect(status().isOk());

            assertEquals(0, countRows("tb_event_person", "event_id", eventId));
            assertEquals(2, countRows("tb_event_assignment", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldClearOnlyThatEventLegacyMirrorRowsWhenUpdatingScale() throws Exception {
        Long firstEventId = null;
        Long secondEventId = null;
        Long locationId = null;
        Long readerId = null;
        try {
            Reader reader = saveReader("Scoped Mirror Reader");
            readerId = reader.getId();
            Location location = locationRepository.saveAndFlush(location("Scoped Mirror Church"));
            locationId = location.getId();

            firstEventId = insertLegacyEventWithMirrorRow("Scoped Mirror First Mass", locationId, readerId);
            secondEventId = insertLegacyEventWithMirrorRow("Scoped Mirror Second Mass", locationId, readerId);

            Long finalFirstEventId = firstEventId;
            mockMvc.perform(put("/eventos/{id}/escala", finalFirstEventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId, null, List.of(readerId), null, null, null
                            ))))
                    .andExpect(status().isOk());

            assertEquals(0, countRows("tb_event_person", "event_id", firstEventId));
            assertEquals(1, countRows("tb_event_person", "event_id", secondEventId));
        } finally {
            cleanupEvent(firstEventId);
            cleanupEvent(secondEventId);
            cleanupPerson(readerId);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldPreserveAssignmentIdentityWhenUpdatingWithoutChanges() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest priest = savePriest("NoOp Priest");
            Reader reader = saveReader("NoOp Reader");
            personIds = List.of(priest.getId(), reader.getId());
            Location location = locationRepository.saveAndFlush(location("NoOp Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "NoOp Mass", locationId, priest.getId(), List.of(reader.getId()), null, null, null
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();

            List<EventAssignmentSnapshot> before = eventAssignmentReadService.findAllByEventId(eventId);

            Long finalEventId = eventId;
            mockMvc.perform(put("/eventos/{id}/escala", finalEventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId, priest.getId(), List.of(reader.getId()), null, null, null
                            ))))
                    .andExpect(status().isOk());

            List<EventAssignmentSnapshot> after = eventAssignmentReadService.findAllByEventId(eventId);

            assertEquals(
                    before.stream().map(EventAssignmentSnapshot::assignmentId).sorted().toList(),
                    after.stream().map(EventAssignmentSnapshot::assignmentId).sorted().toList()
            );
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackEventAndLegacyMirrorCleanupWhenOfficialAssignmentWriteFailsOnCreate() {
        Long priestId = null;
        Long locationId = null;
        try {
            Priest priest = savePriest("Official Write Failure Create Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Official Write Failure Create Church"));
            locationId = location.getId();
            long eventsBefore = countAllEvents();
            RuntimeException failure = new IllegalStateException("official assignment write failed");
            doThrow(failure).when(eventAssignmentCompatibilityService).synchronizeAssignments(any(), any());

            CelebrationEventWithScaleRequestDTO request = eventRequest(
                    "Official Write Failure Create Mass", locationId, priestId, null, null, null, null
            );

            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.createEventWithScale(request));

            assertSame(failure, result);
            assertEquals(eventsBefore, countAllEvents());
            assertEquals(0, countRows("tb_event_assignment", "person_id", priestId));
            assertEquals(0, countRows("tb_event_person", "person_id", priestId));
        } finally {
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRollbackAssignmentsAndLegacyMirrorCleanupWhenOfficialAssignmentWriteFailsOnUpdate() throws Exception {
        Long eventId = null;
        Long locationId = null;
        Long readerId = null;
        try {
            Reader reader = saveReader("Official Write Failure Update Reader");
            readerId = reader.getId();
            Location location = locationRepository.saveAndFlush(location("Official Write Failure Update Church"));
            locationId = location.getId();

            eventId = insertLegacyEventWithMirrorRow("Official Write Failure Update Mass", locationId, readerId);

            int assignmentsBefore = countRows("tb_event_assignment", "event_id", eventId);
            int peopleBefore = countRows("tb_event_person", "event_id", eventId);

            RuntimeException failure = new IllegalStateException("official assignment write failed");
            doThrow(failure).when(eventAssignmentCompatibilityService).synchronizeAssignments(any(), any());

            Long finalEventId = eventId;
            Long finalLocationId = locationId;
            Long finalReaderId = readerId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.updateEventScale(
                            finalEventId,
                            scaleRequest(finalLocationId, null, List.of(finalReaderId), null, null, null)
                    ));

            assertSame(failure, result);
            assertEquals(assignmentsBefore, countRows("tb_event_assignment", "event_id", eventId));
            assertEquals(peopleBefore, countRows("tb_event_person", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(readerId);
            cleanupLocation(locationId);
        }
    }

    private long countAllEvents() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_celebration_event", Long.class);
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

    private Long insertLegacyEventWithMirrorRow(String name, Long locationId, Long personId) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                LocalDate.now().plusDays(30),
                LocalTime.of(19, 0)
        );
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
        jdbcTemplate.update(
                "INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)",
                eventId,
                locationId
        );
        jdbcTemplate.update(
                "INSERT INTO tb_event_person(event_id, person_id) VALUES (?, ?)",
                eventId,
                personId
        );
        return eventId;
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
                locationId, priestId, readerIds, commentatorIds, ministerOfTheWordIds, eucharisticMinisterIds
        );
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
        return "3496" + String.format("%07d", suffix);
    }
}
