package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.event-assignment.read-source.eucharist-scale=PARALLEL",
        "app.event-assignment.shadow-read.eucharist-scale-enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EucharistScaleReadCutoverParallelIntegrationTest {

    private static final String FIXTURE_URL =
            "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=0&size=2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void resetReadSources() {
        eventAssignmentReadSourceProperties.setEucharistScale(EventAssignmentReadSource.PARALLEL);
    }

    @Test
    void shouldUseBackfilledAssignmentsAsOfficialSourceWithoutChangingContractPaginationOrData() throws Exception {
        eventAssignmentReadSourceProperties.setEucharistScale(EventAssignmentReadSource.LEGACY);
        String legacyJson = getPublicJson(FIXTURE_URL);
        List<Map<String, Object>> assignmentsBefore = assignmentRows();
        long eventPeopleBefore = count("tb_event_person");

        eventAssignmentReadSourceProperties.setEucharistScale(EventAssignmentReadSource.PARALLEL);
        Statistics statistics = statistics();
        statistics.clear();

        String parallelJson = getPublicJson(FIXTURE_URL);

        assertEquals(legacyJson, parallelJson);
        assertEquals(3L, statistics.getPrepareStatementCount());
        assertEquals(eventPeopleBefore, count("tb_event_person"));
        assertEquals(assignmentsBefore, assignmentRows());

        JsonNode root = objectMapper.readTree(parallelJson);
        assertEquals(2, root.path("size").asInt());
        assertEquals(3, root.path("totalElements").asInt());
        assertEquals(2, root.path("totalPages").asInt());
        assertEquals(0, root.path("number").asInt());
        assertTrue(root.path("content").isArray());
        assertFalse(root.path("content").get(0).has("eventId"));
        assertTrue(root.path("content").get(0).path("nameMinisters").isArray());
    }

    @Test
    void shouldPreserveParallelFiltersInclusiveDatesAndPageBoundaries() throws Exception {
        String oneDay = getPublicJson(
                "/eventos/escala/eucaristia?startDate=2025-07-13&endDate=2025-07-13&page=0&size=10"
        );
        JsonNode oneDayRoot = objectMapper.readTree(oneDay);
        assertEquals(1, oneDayRoot.path("totalElements").asInt());
        assertEquals("2025-07-13", oneDayRoot.path("content").get(0).path("eventDate").asText());

        String lastPage = getPublicJson(
                "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=1&size=2"
        );
        JsonNode lastPageRoot = objectMapper.readTree(lastPage);
        assertEquals(3, lastPageRoot.path("totalElements").asInt());
        assertEquals(1, lastPageRoot.path("content").size());

        String beyondPage = getPublicJson(
                "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=9&size=2"
        );
        JsonNode beyondRoot = objectMapper.readTree(beyondPage);
        assertEquals(3, beyondRoot.path("totalElements").asInt());
        assertEquals(0, beyondRoot.path("content").size());

        String noResults = getPublicJson(
                "/eventos/escala/eucaristia?startDate=2030-01-01&endDate=2030-01-31&page=0&size=10"
        );
        JsonNode noResultsRoot = objectMapper.readTree(noResults);
        assertEquals(0, noResultsRoot.path("totalElements").asInt());
        assertEquals(0, noResultsRoot.path("content").size());
    }

    @Test
    void shouldReadEventCreatedAndUpdatedByWriteThroughFromParallelSource() throws Exception {
        Long firstMinisterId = personIdsByType("eucharistic_minister").get(0);
        Long secondMinisterId = personIdsByType("eucharistic_minister").get(1);
        Long locationId = firstLocationId();
        LocalDate eventDate = LocalDate.now().plusDays(80);

        String createdJson = mockMvc.perform(post("/eventos/com-escala")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(
                                locationId,
                                eventDate,
                                List.of(firstMinisterId, secondMinisterId)
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long eventId = objectMapper.readTree(createdJson).path("eventId").asLong();
        entityManager.flush();

        assertEquals(2, countRows("tb_event_assignment", "event_id", eventId));
        assertAssignmentType(eventId, firstMinisterId, EventAssignmentType.EUCHARISTIC_MINISTER);
        assertAssignmentType(eventId, secondMinisterId, EventAssignmentType.EUCHARISTIC_MINISTER);
        assertScaleContains(eventDate, List.of(personName(firstMinisterId), personName(secondMinisterId)));

        mockMvc.perform(put("/eventos/{id}/escala", eventId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CelebrationEventScaleRequestDTO(
                                locationId,
                                null,
                                null,
                                null,
                                null,
                                List.of(secondMinisterId)
                        ))))
                .andExpect(status().isOk());
        entityManager.flush();

        assertEquals(1, countRows("tb_event_assignment", "event_id", eventId));
        assertScaleContains(eventDate, List.of(personName(secondMinisterId)));
        assertScaleDoesNotContain(eventDate, personName(firstMinisterId));
    }

    @Test
    void shouldGroupPersonByAssignmentTypeWhenLegacySubtypeIsDifferent() throws Exception {
        Long personId = insertPerson("reader", "Reader Serving Eucharist");
        Long eventId = insertEvent("Parallel Eucharist Different Subtype", LocalDate.now().plusDays(90));
        Long locationId = firstLocationId();
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, locationId);
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.EUCHARISTIC_MINISTER.name()
        );
        long eventPeopleBefore = count("tb_event_person");
        String personName = personName(personId);

        String json = getPublicJson(urlForDate(LocalDate.now().plusDays(90)));

        assertTrue(objectMapper.readTree(json).path("content").toString().contains(personName));
        assertEquals(eventPeopleBefore, count("tb_event_person"));
        assertEquals(1, countAssignment(eventId, personId));
    }

    private void assertScaleContains(LocalDate eventDate, List<String> expectedNames) throws Exception {
        JsonNode content = objectMapper.readTree(getPublicJson(urlForDate(eventDate))).path("content");
        for (String expectedName : expectedNames) {
            assertTrue(content.toString().contains(expectedName), "Expected name in scale: " + expectedName);
        }
    }

    private void assertScaleDoesNotContain(LocalDate eventDate, String name) throws Exception {
        JsonNode content = objectMapper.readTree(getPublicJson(urlForDate(eventDate))).path("content");
        assertFalse(content.toString().contains(name), "Unexpected name in scale: " + name);
    }

    private String getPublicJson(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            Long locationId,
            LocalDate eventDate,
            List<Long> eucharisticMinisterIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Parallel Eucharist Scale " + UUID.randomUUID());
        request.setEventDate(eventDate);
        request.setEventTime(LocalTime.of(19, 0));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setEucharisticMinisterIds(eucharisticMinisterIds);
        return request;
    }

    private String urlForDate(LocalDate eventDate) {
        return "/eventos/escala/eucaristia?startDate=" + eventDate
                + "&endDate=" + eventDate
                + "&page=0&size=10";
    }

    private List<Long> personIdsByType(String personType) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM tb_person WHERE person_type = ? ORDER BY id LIMIT 2",
                Long.class,
                personType
        );
    }

    private Long firstLocationId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_location ORDER BY id LIMIT 1", Long.class);
    }

    private Long insertPerson(String personType, String name) {
        String phoneNumber = uniquePhoneNumber();
        jdbcTemplate.update(
                """
                INSERT INTO tb_person(name, phone_number, birthday_date, password, person_type)
                VALUES (?, ?, '1990-01-10', 'encoded-password', ?)
                """,
                name + " " + UUID.randomUUID(),
                phoneNumber,
                personType
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tb_person WHERE phone_number = ?",
                Long.class,
                phoneNumber
        );
    }

    private Long insertEvent(String name, LocalDate eventDate) {
        String eventName = name + " " + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tb_celebration_event(name_mass_or_event, event_date, event_time, mass_or_celebration)
                VALUES (?, ?, ?, TRUE)
                """,
                eventName,
                eventDate,
                LocalTime.of(19, 0)
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

    private long count(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
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
        return "3496" + String.format("%07d", suffix);
    }
}
