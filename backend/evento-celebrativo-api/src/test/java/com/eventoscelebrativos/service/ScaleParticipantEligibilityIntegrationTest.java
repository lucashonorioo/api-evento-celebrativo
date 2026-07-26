package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.model.Location;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta, que PersonMinistry e a unica fonte de elegibilidade das escalas:
 * subtipo legado correto sem PersonMinistry ativo e rejeitado, e PersonMinistry ativo e aceito
 * mesmo com subtipo legado divergente - a compatibilidade legada nao restringe mais a escrita.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class ScaleParticipantEligibilityIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CelebrationEventService celebrationEventService;

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
    void shouldRejectPersonWithMatchingLegacyTypeButNoActiveMinistry() throws Exception {
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priestWithoutMinistry = savePriestWithoutMinistry("Eligibility Missing Ministry Priest");
            priestId = priestWithoutMinistry.getId();
            Location location = locationRepository.saveAndFlush(location("Eligibility Missing Ministry Church"));
            locationId = location.getId();
            long eventsBefore = countAllEvents();

            mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Eligibility Missing Ministry Mass", locationId, priestWithoutMinistry.getId(),
                                    null, null, null, null
                            ))))
                    .andExpect(status().isUnprocessableEntity());

            assertEquals(eventsBefore, countAllEvents());
            assertEquals(0, countRows("tb_event_person", "person_id", priestId));
        } finally {
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAcceptPersonWithActiveMinistryEvenWithDivergentLegacyType() throws Exception {
        Long locationId = null;
        Long personId = null;
        Long eventId = null;
        try {
            Reader readerWithPriestMinistry = saveReaderWithExtraMinistry("Eligibility Divergent Type Reader", MinistryType.PRIEST);
            personId = readerWithPriestMinistry.getId();
            Location location = locationRepository.saveAndFlush(location("Eligibility Divergent Type Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Eligibility Divergent Type Mass", locationId, readerWithPriestMinistry.getId(),
                                    null, null, null, null
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priest.id").value(readerWithPriestMinistry.getId()))
                    .andReturn();

            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();
            assertEquals(0, countRows("tb_event_person", "event_id", eventId));
            assertEquals(1, countRows("tb_event_assignment", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAcceptPersonWhenLegacyTypeAndActiveMinistryBothMatch() throws Exception {
        Long locationId = null;
        Long priestId = null;
        Long eventId = null;
        try {
            Priest priest = savePriestWithMinistry("Eligibility Valid Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Eligibility Valid Church"));
            locationId = location.getId();

            MvcResult result = mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Eligibility Valid Mass", locationId, priest.getId(), null, null, null, null
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priest.id").value(priest.getId()))
                    .andReturn();

            eventId = objectMapper.readTree(result.getResponse().getContentAsString()).get("eventId").asLong();
            assertEquals(0, countRows("tb_event_person", "event_id", eventId));
            assertEquals(1, countRows("tb_event_assignment", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectSamePersonUsedInTwoRolesWhenOnlyOneMinistryIsActive() throws Exception {
        Long locationId = null;
        Long priestId = null;
        try {
            Priest priest = savePriestWithMinistry("Eligibility Cross Role Priest");
            priestId = priest.getId();
            Location location = locationRepository.saveAndFlush(location("Eligibility Cross Role Church"));
            locationId = location.getId();
            long eventsBefore = countAllEvents();

            mockMvc.perform(post("/eventos/com-escala")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventRequest(
                                    "Eligibility Cross Role Mass", locationId, priest.getId(),
                                    List.of(priest.getId()), null, null, null
                            ))))
                    .andExpect(status().isUnprocessableEntity());

            assertEquals(eventsBefore, countAllEvents());
        } finally {
            cleanupPerson(priestId);
            cleanupLocation(locationId);
        }
    }

    @Test
    void shouldNotWritePartiallyWhenScaleUpdateFailsEligibility() {
        Long eventId = null;
        Long locationId = null;
        Long priestId = null;
        Long invalidPriestId = null;
        try {
            Priest priest = savePriestWithMinistry("Eligibility Partial Write Priest");
            priestId = priest.getId();
            Priest priestWithoutMinistry = savePriestWithoutMinistry("Eligibility Partial Write Invalid Priest");
            invalidPriestId = priestWithoutMinistry.getId();
            Location location = locationRepository.saveAndFlush(location("Eligibility Partial Write Church"));
            locationId = location.getId();

            eventId = celebrationEventService.createEventWithScale(
                    eventRequest("Eligibility Partial Write Mass", locationId, priest.getId(), null, null, null, null)
            ).getEventId();

            int peopleBefore = countRows("tb_event_person", "event_id", eventId);
            int assignmentsBefore = countRows("tb_event_assignment", "event_id", eventId);

            Long finalEventId = eventId;
            Long finalInvalidPriestId = invalidPriestId;
            Long finalLocationId = locationId;
            assertThrows(BusinessException.class, () -> celebrationEventService.updateEventScale(
                    finalEventId,
                    scaleRequest(finalLocationId, finalInvalidPriestId, null, null, null, null)
            ));

            assertEquals(peopleBefore, countRows("tb_event_person", "event_id", eventId));
            assertEquals(assignmentsBefore, countRows("tb_event_assignment", "event_id", eventId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(priestId);
            cleanupPerson(invalidPriestId);
            cleanupLocation(locationId);
        }
    }

    private Priest savePriestWithMinistry(String name) {
        Priest priest = new Priest();
        populatePerson(priest, name);
        priest = (Priest) personRepository.saveAndFlush(priest);
        personMinistryRepository.saveAndFlush(new PersonMinistry(priest, MinistryType.PRIEST));
        return priest;
    }

    private Priest savePriestWithoutMinistry(String name) {
        Priest priest = new Priest();
        populatePerson(priest, name);
        return (Priest) personRepository.saveAndFlush(priest);
    }

    private Reader saveReaderWithExtraMinistry(String name, MinistryType extraMinistry) {
        Reader reader = new Reader();
        populatePerson(reader, name);
        reader = (Reader) personRepository.saveAndFlush(reader);
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, MinistryType.READER));
        personMinistryRepository.saveAndFlush(new PersonMinistry(reader, extraMinistry));
        return reader;
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
        return "3497" + String.format("%07d", suffix);
    }
}
