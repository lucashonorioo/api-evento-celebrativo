package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
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
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
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
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentReadService eventAssignmentReadService;

    @MockitoSpyBean
    private LegacyScaleMirrorService legacyScaleMirrorService;

    @Autowired
    private EventAssignmentConsistencyService eventAssignmentConsistencyService;

    @Autowired
    private EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetReadSource() {
        eventAssignmentReadSourceProperties.setEventScaleDetail(EventAssignmentReadSource.PARALLEL);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReportNoConsistencyIssuesAfterCreatingAndUpdatingScale() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest priest = savePriest("Consistency Priest");
            Reader reader = saveReader("Consistency Reader");
            Commentator commentator = saveCommentator("Consistency Commentator");
            MinisterOfTheWord ministerOfTheWord = saveMinisterOfTheWord("Consistency Word Minister");
            EucharisticMinister eucharisticMinister = saveEucharisticMinister("Consistency Eucharistic Minister");
            personIds = List.of(priest.getId(), reader.getId(), commentator.getId(), ministerOfTheWord.getId(), eucharisticMinister.getId());
            Location location = locationRepository.saveAndFlush(location("Consistency Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Consistency Mass", locationId, priest.getId(),
                                    List.of(reader.getId()), List.of(commentator.getId()),
                                    List.of(ministerOfTheWord.getId()), List.of(eucharisticMinister.getId())
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();

            assertConsistent(eventId);

            Long finalEventId = eventId;
            mockMvc.perform(put("/eventos/{id}/escala", finalEventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId, priest.getId(), List.of(reader.getId()), null, null, null
                            ))))
                    .andExpect(status().isOk());

            assertConsistent(eventId);
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
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
    @WithMockUser(roles = "ADMIN")
    void shouldKeepLegacyOverrideReadSemanticallyConsistentAfterOfficialWrite() throws Exception {
        Long eventId = null;
        Long locationId = null;
        List<Long> personIds = List.of();
        try {
            Priest priest = savePriest("Legacy Override Priest");
            Priest newPriest = savePriest("Legacy Override New Priest");
            personIds = List.of(priest.getId(), newPriest.getId());
            Location location = locationRepository.saveAndFlush(location("Legacy Override Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Legacy Override Mass", locationId, priest.getId(), null, null, null, null
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();

            Long finalEventId = eventId;
            mockMvc.perform(put("/eventos/{id}/escala", finalEventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scaleRequest(
                                    locationId, newPriest.getId(), null, null, null, null
                            ))))
                    .andExpect(status().isOk());

            eventAssignmentReadSourceProperties.setEventScaleDetail(EventAssignmentReadSource.LEGACY);

            mockMvc.perform(get("/eventos/{id}/escala", finalEventId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priest.id").value(newPriest.getId()));
        } finally {
            cleanupEvent(eventId);
            personIds.forEach(this::cleanupPerson);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldRollbackOfficialAssignmentsWhenLegacyMirrorSyncFailsOnCreate() {
        Long priestId = null;
        Long locationId = null;
        try {
            Priest priest = savePriest("Mirror Failure Create Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Mirror Failure Create Church"));
            locationId = location.getId();
            long eventsBefore = countAllEvents();
            RuntimeException failure = new IllegalStateException("legacy mirror sync failed");
            doThrow(failure).when(legacyScaleMirrorService).synchronizeMirror(any(), any());

            CelebrationEventWithScaleRequestDTO request = eventRequest(
                    "Mirror Failure Create Mass", locationId, priestId, null, null, null, null
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
    void shouldRollbackOfficialAssignmentsWhenLegacyMirrorSyncFailsOnUpdate() throws Exception {
        Long eventId = null;
        Long locationId = null;
        Long oldReaderId = null;
        Long newReaderId = null;
        try {
            Reader oldReader = saveReader("Mirror Failure Update Old Reader");
            oldReaderId = oldReader.getId();
            Reader newReader = saveReader("Mirror Failure Update New Reader");
            newReaderId = newReader.getId();
            Location location = locationRepository.saveAndFlush(location("Mirror Failure Update Church"));
            locationId = location.getId();

            MvcResult createResult = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Mirror Failure Update Mass", locationId, null, List.of(oldReaderId), null, null, null
                            ))))
                    .andExpect(status().isCreated())
                    .andReturn();
            eventId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("eventId").asLong();

            List<Long> assignmentIdsBefore = eventAssignmentReadService.findAllByEventId(eventId).stream()
                    .map(EventAssignmentSnapshot::assignmentId)
                    .sorted()
                    .toList();
            int assignmentsBefore = countRows("tb_event_assignment", "event_id", eventId);
            int peopleBefore = countRows("tb_event_person", "event_id", eventId);

            RuntimeException failure = new IllegalStateException("legacy mirror sync failed");
            doThrow(failure).when(legacyScaleMirrorService).synchronizeMirror(any(), any());

            Long finalEventId = eventId;
            Long finalLocationId = locationId;
            Long finalNewReaderId = newReaderId;
            RuntimeException result = assertThrows(RuntimeException.class, () ->
                    celebrationEventService.updateEventScale(
                            finalEventId,
                            scaleRequest(finalLocationId, null, List.of(finalNewReaderId), null, null, null)
                    ));

            assertSame(failure, result);
            List<Long> assignmentIdsAfter = eventAssignmentReadService.findAllByEventId(eventId).stream()
                    .map(EventAssignmentSnapshot::assignmentId)
                    .sorted()
                    .toList();
            assertEquals(assignmentIdsBefore, assignmentIdsAfter);
            assertEquals(assignmentsBefore, countRows("tb_event_assignment", "event_id", eventId));
            assertEquals(peopleBefore, countRows("tb_event_person", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(oldReaderId);
            cleanupPerson(newReaderId);
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

    private void assertConsistent(Long eventId) {
        var legacyEvent = celebrationEventRepository.findByIdWithPeople(eventId).orElseThrow();
        List<EventAssignmentSnapshot> parallelAssignments = eventAssignmentReadService.findAllByEventId(eventId);

        EventAssignmentConsistencyReport report = eventAssignmentConsistencyService.compareEvent(legacyEvent, parallelAssignments);

        assertTrue(report.consistent(), () -> "issues: " + report.issues());
        assertEquals(0, report.issues().size());
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
