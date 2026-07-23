package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.EventAssignmentReadSource;
import com.eventoscelebrativos.config.EventAssignmentReadSourceProperties;
import com.eventoscelebrativos.config.EventAssignmentShadowReadProperties;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.EventScheduleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
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
        "app.event-assignment.read-source.event-scale-detail=PARALLEL",
        "app.event-assignment.read-source.eucharist-scale=PARALLEL",
        "app.event-assignment.read-source.monthly-schedule=PARALLEL",
        "app.event-assignment.shadow-read.event-scale-detail-enabled=false",
        "app.event-assignment.shadow-read.eucharist-scale-enabled=false",
        "app.event-assignment.shadow-read.monthly-schedule-enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EventAssignmentParallelCutoverConsistencyIntegrationTest {

    private static final String EVENT_SCALE_URL = "/eventos/1/escala";
    private static final String EUCHARIST_SCALE_URL =
            "/eventos/escala/eucaristia?startDate=2025-07-01&endDate=2025-07-31&page=0&size=1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventAssignmentReadSourceProperties eventAssignmentReadSourceProperties;

    @Autowired
    private EventAssignmentShadowReadProperties eventAssignmentShadowReadProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void resetReadSources() {
        setReadSources(
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL
        );
        SqlCapture.clear();
    }

    @Test
    void shouldKeepBackfilledFixturesEquivalentAndReadOnlyAcrossAllParallelCutovers() throws Exception {
        List<Map<String, Object>> assignmentsBefore = assignmentRows();
        long eventPeopleBefore = count("tb_event_person");

        setReadSources(
                EventAssignmentReadSource.LEGACY,
                EventAssignmentReadSource.LEGACY,
                EventAssignmentReadSource.LEGACY
        );
        JsonNode legacyScaleDetail = getEventScaleJson(EVENT_SCALE_URL);
        JsonNode legacyEucharistScale = getPublicJson(EUCHARIST_SCALE_URL);
        List<JsonNode> legacyMonthlySchedules = new ArrayList<>();
        for (EventScheduleType type : EventScheduleType.values()) {
            legacyMonthlySchedules.add(getMonthlyScheduleJson(fixtureMonthlyUrl(type, 0, 1, false)));
        }

        setReadSources(
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL
        );

        JsonNode parallelScaleDetail = getParallelEventScaleJson(EVENT_SCALE_URL, 2);
        assertEquals(legacyScaleDetail, parallelScaleDetail);
        assertScaleDetailContract(parallelScaleDetail);

        JsonNode parallelEucharistScale = getParallelPublicJson(EUCHARIST_SCALE_URL, 3);
        assertEquals(legacyEucharistScale, parallelEucharistScale);
        assertEquals(3, parallelEucharistScale.path("totalElements").asInt());
        assertEquals(3, parallelEucharistScale.path("totalPages").asInt());

        for (int index = 0; index < EventScheduleType.values().length; index++) {
            EventScheduleType type = EventScheduleType.values()[index];
            JsonNode parallelMonthly = getParallelMonthlyScheduleJson(fixtureMonthlyUrl(type, 0, 1, false), 3);

            assertEquals(legacyMonthlySchedules.get(index), parallelMonthly);
            assertEquals(type.name(), parallelMonthly.path("content").get(0).path("assignmentType").asText());
        }

        assertEquals(eventPeopleBefore, count("tb_event_person"));
        assertEquals(assignmentsBefore, assignmentRows());
    }

    @Test
    void shouldUseWriteThroughCreatedAndUpdatedEventsInTheThreeParallelCutovers() throws Exception {
        Long locationId = firstLocationId();
        List<Long> priests = personIdsByType("priest", 2);
        List<Long> readers = personIdsByType("reader", 3);
        List<Long> commentators = personIdsByType("commentator", 2);
        List<Long> ministersOfTheWord = personIdsByType("minister_of_the_word", 1);
        List<Long> eucharisticMinisters = personIdsByType("eucharistic_minister", 2);
        LocalDate eventDate = LocalDate.now().plusDays(45);

        Long eventId = createEventWithScale(eventRequest(
                locationId,
                eventDate,
                priests.get(0),
                List.of(readers.get(0), readers.get(1)),
                List.of(commentators.get(0)),
                List.of(ministersOfTheWord.get(0)),
                List.of(eucharisticMinisters.get(0), eucharisticMinisters.get(1))
        ));
        createEventWithScale(eventRequest(
                locationId,
                eventDate,
                priests.get(1),
                List.of(readers.get(2)),
                List.of(commentators.get(1)),
                List.of(ministersOfTheWord.get(0)),
                List.of(eucharisticMinisters.get(1))
        ));
        entityManager.flush();

        assertEquals(7, countRows("tb_event_assignment", "event_id", eventId));
        assertParallelScaleContains(eventId, "priest", priests.get(0));
        assertParallelMonthlyContains(eventDate, EventScheduleType.READER, personName(readers.get(0)));
        assertParallelMonthlyContains(eventDate, EventScheduleType.READER, personName(readers.get(1)));
        assertParallelEucharistContains(eventDate, personName(eucharisticMinisters.get(0)));
        assertParallelEucharistContains(eventDate, personName(eucharisticMinisters.get(1)));

        mockMvc.perform(put("/eventos/{id}/escala", eventId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CelebrationEventScaleRequestDTO(
                                locationId,
                                priests.get(1),
                                List.of(readers.get(1), readers.get(2)),
                                List.of(commentators.get(0), commentators.get(1)),
                                List.of(ministersOfTheWord.get(0)),
                                List.of(eucharisticMinisters.get(1))
                        ))))
                .andExpect(status().isOk());
        entityManager.flush();

        List<Map<String, Object>> assignmentsBeforeGets = assignmentRows();
        long eventPeopleBeforeGets = count("tb_event_person");

        JsonNode scale = getParallelEventScaleJson("/eventos/" + eventId + "/escala", 2);
        assertEquals(priests.get(1).longValue(), scale.path("priest").path("id").asLong());
        assertDoesNotContain(scale.path("readers"), readers.get(0));
        assertContainsPersonId(scale.path("readers"), readers.get(1));
        assertContainsPersonId(scale.path("readers"), readers.get(2));
        assertContainsPersonId(scale.path("commentators"), commentators.get(1));
        assertContainsPersonId(scale.path("eucharisticMinisters"), eucharisticMinisters.get(1));
        assertDoesNotContain(scale.path("eucharisticMinisters"), eucharisticMinisters.get(0));

        JsonNode sameDateEucharist = getParallelPublicJson(urlForEucharistDate(eventDate, 0, 10), 2);
        assertTrue(sameDateEucharist.path("totalElements").asInt() >= 2);
        assertTrue(sameDateEucharist.path("content").toString().contains(personName(eucharisticMinisters.get(1))));
        assertFalse(sameDateEucharist.path("content").toString().contains(personName(eucharisticMinisters.get(0))));

        JsonNode sameDateMonthly = getParallelMonthlyScheduleJson(urlForMonthlyDate(eventDate, EventScheduleType.READER, 0, 10), 2);
        assertTrue(sameDateMonthly.path("totalElements").asInt() >= 2);
        assertTrue(sameDateMonthly.path("content").toString().contains(personName(readers.get(2))));
        assertFalse(sameDateMonthly.path("content").toString().contains(personName(readers.get(0))));

        assertEquals(eventPeopleBeforeGets, count("tb_event_person"));
        assertEquals(assignmentsBeforeGets, assignmentRows());
    }

    @Test
    void shouldHandleEmptySchedulesPaginationBoundariesAndSubtypeDivergenceWithoutLegacyFallback() throws Exception {
        Long emptyEventId = insertEvent("Parallel Empty Event", LocalDate.now().plusDays(60));
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", emptyEventId, firstLocationId());

        JsonNode emptyScale = getParallelEventScaleJson("/eventos/" + emptyEventId + "/escala", 2);
        assertTrue(emptyScale.path("priest").isNull() || emptyScale.path("priest").isMissingNode());
        assertEquals(0, emptyScale.path("readers").size());
        assertEquals(0, emptyScale.path("commentators").size());
        assertEquals(0, emptyScale.path("ministersOfTheWord").size());
        assertEquals(0, emptyScale.path("eucharisticMinisters").size());

        mockMvc.perform(get("/eventos/999999/escala")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isNotFound());

        assertPage("/eventos/escala/eucaristia?startDate=2030-01-01&endDate=2030-01-31&page=0&size=10", 0, 0, 1);
        assertPage(fixtureMonthlyUrl(EventScheduleType.EUCHARISTIC_MINISTER, 0, 2, false), 3, 2, 3);
        assertPage(fixtureMonthlyUrl(EventScheduleType.EUCHARISTIC_MINISTER, 1, 2, false), 3, 1, 2);
        assertPage(fixtureMonthlyUrl(EventScheduleType.EUCHARISTIC_MINISTER, 9, 2, false), 3, 0, 2);
        assertPage("/eventos/escalas?startDate=2030-01-01&endDate=2030-01-31&type=READER&page=0&size=10", 0, 0, 1);

        long eventPeopleBefore = count("tb_event_person");
        Long personId = insertPerson("reader", "Reader Assigned As Eucharistic Minister");
        LocalDate eventDate = LocalDate.now().plusDays(61);
        Long eventId = insertEvent("Parallel Divergent Assignment", eventDate);
        jdbcTemplate.update("INSERT INTO tb_event_location(event_id, location_id) VALUES (?, ?)", eventId, firstLocationId());
        jdbcTemplate.update(
                """
                INSERT INTO tb_event_assignment(event_id, person_id, assignment_type, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                eventId,
                personId,
                EventAssignmentType.EUCHARISTIC_MINISTER.name()
        );
        entityManager.flush();

        JsonNode divergentScale = getParallelEventScaleJson("/eventos/" + eventId + "/escala", 2);
        assertEquals(0, divergentScale.path("readers").size());
        assertContainsPersonId(divergentScale.path("eucharisticMinisters"), personId);
        assertTrue(getParallelPublicJson(urlForEucharistDate(eventDate, 0, 10), 2).path("content").toString().contains(personName(personId)));
        assertTrue(getParallelMonthlyScheduleJson(
                urlForMonthlyDate(eventDate, EventScheduleType.EUCHARISTIC_MINISTER, 0, 10),
                2
        ).path("content").toString().contains(personName(personId)));
        assertEquals(eventPeopleBefore, count("tb_event_person"));
    }

    @Test
    void shouldKeepReadSourceRollbacksIndependentAcrossTheThreeCutovers() throws Exception {
        assertFalse(eventAssignmentShadowReadProperties.isEventScaleDetailEnabled());
        assertFalse(eventAssignmentShadowReadProperties.isEucharistScaleEnabled());
        assertFalse(eventAssignmentShadowReadProperties.isMonthlyScheduleEnabled());

        assertRollbackScenario(
                EventAssignmentReadSource.LEGACY,
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL,
                true,
                false,
                false
        );
        assertRollbackScenario(
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.LEGACY,
                EventAssignmentReadSource.PARALLEL,
                false,
                true,
                false
        );
        assertRollbackScenario(
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.PARALLEL,
                EventAssignmentReadSource.LEGACY,
                false,
                false,
                true
        );
    }

    private void assertRollbackScenario(
            EventAssignmentReadSource scaleDetail,
            EventAssignmentReadSource eucharistScale,
            EventAssignmentReadSource monthlySchedule,
            boolean scaleUsesLegacy,
            boolean eucharistUsesLegacy,
            boolean monthlyUsesLegacy
    ) throws Exception {
        setReadSources(scaleDetail, eucharistScale, monthlySchedule);

        assertEndpointUsesLegacyTable(() -> getEventScaleJson(EVENT_SCALE_URL), scaleUsesLegacy);
        assertEndpointUsesLegacyTable(() -> getPublicJson(EUCHARIST_SCALE_URL), eucharistUsesLegacy);
        assertEndpointUsesLegacyTable(
                () -> getMonthlyScheduleJson(fixtureMonthlyUrl(EventScheduleType.READER, 0, 1, false)),
                monthlyUsesLegacy
        );
    }

    private void assertEndpointUsesLegacyTable(SqlCheckedRequest request, boolean expectedLegacyTable) throws Exception {
        SqlCapture.clear();
        statistics().clear();

        request.execute();

        assertEquals(expectedLegacyTable, sqlContains("tb_event_person"), String.join("\n", SqlCapture.statements()));
    }

    private JsonNode getParallelEventScaleJson(String url, long expectedQueries) throws Exception {
        JsonNode json = getWithParallelSqlAssertions(() -> getEventScaleJson(url), expectedQueries);
        assertNoSqlContaining("tb_event_person");
        return json;
    }

    private JsonNode getParallelPublicJson(String url, long expectedQueries) throws Exception {
        JsonNode json = getWithParallelSqlAssertions(() -> getPublicJson(url), expectedQueries);
        assertNoSqlContaining("tb_event_person");
        return json;
    }

    private JsonNode getParallelMonthlyScheduleJson(String url, long expectedQueries) throws Exception {
        JsonNode json = getWithParallelSqlAssertions(() -> getMonthlyScheduleJson(url), expectedQueries);
        assertNoSqlContaining("tb_event_person");
        return json;
    }

    private JsonNode getWithParallelSqlAssertions(SqlCheckedRequest request, long expectedQueries) throws Exception {
        SqlCapture.clear();
        statistics().clear();

        JsonNode json = request.execute();

        assertEquals(expectedQueries, statistics().getPrepareStatementCount(), String.join("\n", SqlCapture.statements()));
        assertNoWritesCaptured();
        return json;
    }

    private JsonNode getEventScaleJson(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode getMonthlyScheduleJson(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode getPublicJson(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void assertPage(String url, int expectedTotalElements, int expectedContentSize, long expectedQueries) throws Exception {
        JsonNode page = url.contains("/eventos/escalas")
                ? getParallelMonthlyScheduleJson(url, expectedQueries)
                : getParallelPublicJson(url, expectedQueries);

        assertEquals(expectedTotalElements, page.path("totalElements").asInt());
        assertEquals(expectedContentSize, page.path("content").size());
    }

    private Long createEventWithScale(CelebrationEventWithScaleRequestDTO request) throws Exception {
        String json = mockMvc.perform(post("/eventos/com-escala")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json).path("eventId").asLong();
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            Long locationId,
            LocalDate eventDate,
            Long priestId,
            List<Long> readerIds,
            List<Long> commentatorIds,
            List<Long> ministerOfTheWordIds,
            List<Long> eucharisticMinisterIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Consolidated Parallel Cutover " + UUID.randomUUID());
        request.setEventDate(eventDate);
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

    private void assertScaleDetailContract(JsonNode root) {
        assertTrue(root.path("eventId").isNumber());
        assertTrue(root.path("eventName").isTextual());
        assertTrue(root.path("eventDate").isTextual());
        assertTrue(root.path("eventTime").isTextual());
        assertTrue(root.path("massOrCelebration").isBoolean());
        assertTrue(root.path("location").path("id").isNumber());
        assertTrue(root.path("readers").isArray());
        assertTrue(root.path("commentators").isArray());
        assertTrue(root.path("ministersOfTheWord").isArray());
        assertTrue(root.path("eucharisticMinisters").isArray());
        assertFalse(root.has("assignments"));
    }

    private void assertParallelScaleContains(Long eventId, String groupName, Long personId) throws Exception {
        JsonNode root = getParallelEventScaleJson("/eventos/" + eventId + "/escala", 2);
        if ("priest".equals(groupName)) {
            assertEquals(personId.longValue(), root.path("priest").path("id").asLong());
            return;
        }
        assertContainsPersonId(root.path(groupName), personId);
    }

    private void assertParallelMonthlyContains(LocalDate eventDate, EventScheduleType type, String expectedName) throws Exception {
        JsonNode content = getParallelMonthlyScheduleJson(urlForMonthlyDate(eventDate, type, 0, 10), 2).path("content");
        assertTrue(content.toString().contains(expectedName), "Expected monthly schedule to contain " + expectedName);
    }

    private void assertParallelEucharistContains(LocalDate eventDate, String expectedName) throws Exception {
        JsonNode content = getParallelPublicJson(urlForEucharistDate(eventDate, 0, 10), 2).path("content");
        assertTrue(content.toString().contains(expectedName), "Expected eucharist scale to contain " + expectedName);
    }

    private void assertContainsPersonId(JsonNode people, Long personId) {
        for (JsonNode person : people) {
            if (person.path("id").asLong() == personId) {
                return;
            }
        }
        throw new AssertionError("Expected person id " + personId + " in " + people);
    }

    private void assertDoesNotContain(JsonNode people, Long personId) {
        for (JsonNode person : people) {
            assertFalse(person.path("id").asLong() == personId, "Unexpected person id " + personId + " in " + people);
        }
    }

    private void setReadSources(
            EventAssignmentReadSource scaleDetail,
            EventAssignmentReadSource eucharistScale,
            EventAssignmentReadSource monthlySchedule
    ) {
        eventAssignmentReadSourceProperties.setEventScaleDetail(scaleDetail);
        eventAssignmentReadSourceProperties.setEucharistScale(eucharistScale);
        eventAssignmentReadSourceProperties.setMonthlySchedule(monthlySchedule);
    }

    private String fixtureMonthlyUrl(EventScheduleType type, int page, int size, boolean includeUnassigned) {
        return "/eventos/escalas?startDate=2025-07-01&endDate=2025-07-31&type=" + type
                + "&page=" + page
                + "&size=" + size
                + "&includeUnassigned=" + includeUnassigned;
    }

    private String urlForMonthlyDate(LocalDate eventDate, EventScheduleType type, int page, int size) {
        return "/eventos/escalas?startDate=" + eventDate
                + "&endDate=" + eventDate
                + "&type=" + type
                + "&page=" + page
                + "&size=" + size;
    }

    private String urlForEucharistDate(LocalDate eventDate, int page, int size) {
        return "/eventos/escala/eucaristia?startDate=" + eventDate
                + "&endDate=" + eventDate
                + "&page=" + page
                + "&size=" + size;
    }

    private Long firstLocationId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_location ORDER BY id LIMIT 1", Long.class);
    }

    private List<Long> personIdsByType(String personType, int limit) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM tb_person WHERE person_type = ? ORDER BY id LIMIT ?",
                Long.class,
                personType,
                limit
        );
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

    private int countRows(String table, String column, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
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

    private boolean sqlContains(String text) {
        String expected = text.toLowerCase();
        return SqlCapture.statements().stream()
                .map(String::toLowerCase)
                .anyMatch(sql -> sql.contains(expected));
    }

    private void assertNoSqlContaining(String text) {
        assertFalse(sqlContains(text), String.join("\n", SqlCapture.statements()));
    }

    private void assertNoWritesCaptured() {
        boolean hasWrite = SqlCapture.statements().stream()
                .map(String::stripLeading)
                .map(String::toLowerCase)
                .anyMatch(sql -> sql.startsWith("insert ")
                        || sql.startsWith("update ")
                        || sql.startsWith("delete ")
                        || sql.startsWith("merge "));
        assertFalse(hasWrite, String.join("\n", SqlCapture.statements()));
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }

    @FunctionalInterface
    private interface SqlCheckedRequest {
        JsonNode execute() throws Exception;
    }

    private static final class SqlCapture {
        private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

        private static void add(String sql) {
            STATEMENTS.add(sql);
        }

        private static List<String> statements() {
            synchronized (STATEMENTS) {
                return List.copyOf(STATEMENTS);
            }
        }

        private static void clear() {
            STATEMENTS.clear();
        }
    }

    @TestConfiguration
    static class SqlCaptureConfiguration {

        @Bean
        HibernatePropertiesCustomizer sqlCaptureStatementInspector() {
            StatementInspector inspector = sql -> {
                SqlCapture.add(sql);
                return sql;
            };
            return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
        }
    }
}
