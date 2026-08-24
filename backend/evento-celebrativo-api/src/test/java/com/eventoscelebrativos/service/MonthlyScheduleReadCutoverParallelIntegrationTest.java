package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.model.MinistryType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class MonthlyScheduleReadCutoverParallelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldUseBackfilledAssignmentsAsOfficialSourceForAllTypesWithoutChangingContractOrData() throws Exception {
        List<Map<String, Object>> assignmentsBefore = assignmentRows();

        for (EventScheduleType type : EventScheduleType.values()) {
            String url = fixtureUrl(type, 0, 1, false);
            Statistics statistics = statistics();
            statistics.clear();
            String parallelJson = getJson(url);

            assertEquals(3L, statistics.getPrepareStatementCount());
            JsonNode root = objectMapper.readTree(parallelJson);
            assertEquals(type.name(), root.path("content").get(0).path("assignmentType").asText());
            assertTrue(root.path("content").get(0).path("assignments").isArray());
        }

        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldPreserveParallelFiltersPaginationBoundariesAndNoResults() throws Exception {
        JsonNode oneDay = objectMapper.readTree(getJson(
                "/eventos/escalas?startDate=2025-07-13&endDate=2025-07-13&type=READER&page=0&size=10"
        ));
        assertEquals(1, oneDay.path("totalElements").asInt());
        JsonNode event = oneDay.path("content").get(0);
        assertEquals("2025-07-13T10:00:00", event.path("startAt").asText());
        assertEquals("2025-07-13T11:00:00", event.path("endAt").asText());
        assertFalse(event.has("eventDate"));
        assertFalse(event.has("eventTime"));

        JsonNode firstPage = objectMapper.readTree(getJson(fixtureUrl(EventScheduleType.EUCHARISTIC_MINISTER, 0, 2, false)));
        JsonNode lastPage = objectMapper.readTree(getJson(fixtureUrl(EventScheduleType.EUCHARISTIC_MINISTER, 1, 2, false)));
        JsonNode beyondPage = objectMapper.readTree(getJson(fixtureUrl(EventScheduleType.EUCHARISTIC_MINISTER, 9, 2, false)));
        assertEquals(3, firstPage.path("totalElements").asInt());
        assertEquals(2, firstPage.path("content").size());
        assertEquals(1, lastPage.path("content").size());
        assertEquals(0, beyondPage.path("content").size());

        JsonNode noResults = objectMapper.readTree(getJson(
                "/eventos/escalas?startDate=2030-01-01&endDate=2030-01-31&type=READER&page=0&size=10"
        ));
        assertEquals(0, noResults.path("totalElements").asInt());
        assertEquals(0, noResults.path("content").size());
    }

    @Test
    void shouldReadEventCreatedAndUpdatedByWriteThroughFromParallelSource() throws Exception {
        List<Long> readerIds = personIdsByMinistry(MinistryType.READER);
        Long firstReaderId = readerIds.get(0);
        Long secondReaderId = readerIds.get(1);
        Long locationId = firstLocationId();
        LocalDate eventDate = LocalDate.now().plusDays(95);

        String createdJson = mockMvc.perform(post("/eventos/com-escala")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(
                                locationId,
                                eventDate,
                                List.of(firstReaderId, secondReaderId)
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long eventId = objectMapper.readTree(createdJson).path("eventId").asLong();
        entityManager.flush();

        assertAssignmentType(eventId, firstReaderId, EventAssignmentType.READER);
        assertAssignmentType(eventId, secondReaderId, EventAssignmentType.READER);
        assertScheduleContains(eventDate, EventScheduleType.READER, List.of(personName(firstReaderId), personName(secondReaderId)));

        mockMvc.perform(put("/eventos/{id}/escala", eventId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CelebrationEventScaleRequestDTO(
                                locationId,
                                null,
                                List.of(secondReaderId),
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk());
        entityManager.flush();

        assertEquals(1, countRows("tb_event_assignment", "event_id", eventId));
        assertScheduleContains(eventDate, EventScheduleType.READER, List.of(personName(secondReaderId)));
        assertScheduleDoesNotContain(eventDate, EventScheduleType.READER, personName(firstReaderId));
    }

    @Test
    void shouldGroupPersonByAssignmentTypeWhenLegacySubtypeIsDifferent() throws Exception {
        Long personId = insertPerson("Reader Serving As Commentator");
        Long eventId = insertEvent("Parallel Monthly Different Subtype", LocalDate.now().plusDays(96));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.COMMENTATOR.name()
        );
        String personName = personName(personId);

        String json = getJson(urlForDate(LocalDate.now().plusDays(96), EventScheduleType.COMMENTATOR));

        assertTrue(objectMapper.readTree(json).path("content").toString().contains(personName));
        assertEquals(1, countAssignment(eventId, personId));
    }

    private void assertScheduleContains(LocalDate eventDate, EventScheduleType type, List<String> expectedNames) throws Exception {
        JsonNode content = objectMapper.readTree(getJson(urlForDate(eventDate, type))).path("content");
        for (String expectedName : expectedNames) {
            assertTrue(content.toString().contains(expectedName), "Expected name in schedule: " + expectedName);
        }
    }

    private void assertScheduleDoesNotContain(LocalDate eventDate, EventScheduleType type, String name) throws Exception {
        JsonNode content = objectMapper.readTree(getJson(urlForDate(eventDate, type))).path("content");
        assertFalse(content.toString().contains(name), "Unexpected name in schedule: " + name);
    }

    private String getJson(String url) throws Exception {
        return mockMvc.perform(get(url).with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String fixtureUrl(EventScheduleType type, int page, int size, boolean includeUnassigned) {
        return "/eventos/escalas?startDate=2025-07-01&endDate=2025-07-31&type=" + type
                + "&page=" + page
                + "&size=" + size
                + "&includeUnassigned=" + includeUnassigned;
    }

    private String urlForDate(LocalDate eventDate, EventScheduleType type) {
        return "/eventos/escalas?startDate=" + eventDate
                + "&endDate=" + eventDate
                + "&type=" + type
                + "&page=0&size=10";
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            Long locationId,
            LocalDate eventDate,
            List<Long> readerIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Parallel Monthly Schedule " + UUID.randomUUID());
        request.setStartAt(LocalDateTime.of(eventDate, LocalTime.of(19, 0)));
        request.setEndAt(LocalDateTime.of(eventDate, LocalTime.of(20, 0)));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setReaderIds(readerIds);
        return request;
    }

    private List<Long> personIdsByMinistry(MinistryType ministryType) {
        return jdbcTemplate.queryForList(
                """
                SELECT p.id
                FROM tb_person_ministry pm
                INNER JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.ministry_type = ?
                  AND pm.active = TRUE
                ORDER BY p.id
                LIMIT 2
                """,
                Long.class,
                ministryType.name()
        );
    }

    private Long firstLocationId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_location ORDER BY id LIMIT 1", Long.class);
    }

    private Long insertPerson(String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(public_id, name, phone_number, birthday_date)
                VALUES (?, ?, ?, '1990-01-10')
                """,
                newPublicId(),
                name + " " + UUID.randomUUID(),
                phoneNumber
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private Long insertEvent(String name, LocalDate eventDate) {
        String eventName = name + " " + UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.of(eventDate, LocalTime.of(19, 0));
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, start_at, end_at, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                startAt,
                startAt.plusHours(1)
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_celebration_event WHERE name_mass_or_event = ?",
                Long.class,
                eventName
        );
    }

    private String personName(Long personId) {
        return jdbcTemplate.queryForObject("SELECT name FROM tb_person WHERE id = ?", String.class, personId);
    }

    private void assertAssignmentType(Long eventId, Long personId, EventAssignmentType assignmentType) {
        String actual = jdbcTemplate.queryForObject(
                "SELECT assignment_type FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                String.class,
                eventId,
                personId
        );
        assertEquals(assignmentType.name(), actual);
    }

    private int countRows(String table, String column, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private int countAssignment(Long eventId, Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE event_id = ? AND person_id = ?",
                Integer.class,
                eventId,
                personId
        );
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> assignmentRows() {
        return jdbcTemplate.queryForList(
                """
                SELECT id, event_id, person_id, assignment_type, created_at, updated_at
                FROM tb_event_assignment
                ORDER BY id
                """
        );
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }

    private byte[] newPublicId() {
        UUID uuid = UUID.randomUUID();
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
