package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.model.EventAssignmentType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class EventScaleDetailReadCutoverParallelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldUseBackfilledAssignmentsAsOfficialSourceWithoutChangingContractOrData() throws Exception {
        List<Map<String, Object>> assignmentsBefore = assignmentRows();
        Statistics statistics = statistics();
        statistics.clear();

        String parallelJson = getAuthorizedJson(1L);

        assertEquals(2L, statistics.getPrepareStatementCount());
        assertEquals(assignmentsBefore, assignmentRows());

        JsonNode root = objectMapper.readTree(parallelJson);
        assertEquals(1L, root.path("eventId").asLong());
        assertTrue(root.path("location").path("id").isNumber());
        assertTrue(root.path("priest").path("id").isNumber());
        assertTrue(root.path("readers").isArray());
        assertTrue(root.path("commentators").isArray());
        assertTrue(root.path("ministersOfTheWord").isArray());
        assertTrue(root.path("eucharisticMinisters").isArray());
        assertFalse(root.has("assignments"));
        assertFalse(root.has("phoneNumber"));
        assertFalse(root.path("readers").get(0).has("phoneNumber"));
    }

    @Test
    void shouldReadEventCreatedByWriteThroughFromParallelSource() throws Exception {
        Long priestId = personIdByMinistry(MinistryType.PRIEST);
        Long readerId = personIdByMinistry(MinistryType.READER);
        Long commentatorId = personIdByMinistry(MinistryType.COMMENTATOR);
        Long ministerOfTheWordId = personIdByMinistry(MinistryType.MINISTER_OF_THE_WORD);
        Long eucharisticMinisterId = personIdByMinistry(MinistryType.EUCHARISTIC_MINISTER);
        Long locationId = firstLocationId();

        String createdJson = mockMvc.perform(post("/eventos/com-escala")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(
                                locationId,
                                priestId,
                                List.of(readerId),
                                List.of(commentatorId),
                                List.of(ministerOfTheWordId),
                                List.of(eucharisticMinisterId)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignments").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long eventId = objectMapper.readTree(createdJson).path("eventId").asLong();
        entityManager.flush();

        assertEquals(5, countRows("tb_event_assignment", "event_id", eventId));
        assertAssignmentType(eventId, priestId, EventAssignmentType.PRIEST);
        assertAssignmentType(eventId, readerId, EventAssignmentType.READER);
        assertAssignmentType(eventId, commentatorId, EventAssignmentType.COMMENTATOR);
        assertAssignmentType(eventId, ministerOfTheWordId, EventAssignmentType.MINISTER_OF_THE_WORD);
        assertAssignmentType(eventId, eucharisticMinisterId, EventAssignmentType.EUCHARISTIC_MINISTER);

        mockMvc.perform(get("/eventos/{id}/escala", eventId)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.priest.id").value(priestId))
                .andExpect(jsonPath("$.readers[0].id").value(readerId))
                .andExpect(jsonPath("$.commentators[0].id").value(commentatorId))
                .andExpect(jsonPath("$.ministersOfTheWord[0].id").value(ministerOfTheWordId))
                .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(eucharisticMinisterId))
                .andExpect(jsonPath("$.assignments").doesNotExist());
    }

    @Test
    void shouldGroupPersonByAssignmentTypeWhenLegacySubtypeIsDifferent() throws Exception {
        Long personId = insertPerson("Parallel Different Subtype Reader");
        Long eventId = insertEvent("Parallel Different Subtype Mass");
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

        mockMvc.perform(get("/eventos/{id}/escala", eventId)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readers").isEmpty())
                .andExpect(jsonPath("$.eucharisticMinisters[0].id").value(personId))
                .andExpect(jsonPath("$.eucharisticMinisters[0].name").exists())
                .andExpect(jsonPath("$.eucharisticMinisters[0].phoneNumber").doesNotExist());
    }

    @Test
    void shouldPreserveNotFoundAndSecurityBehaviorInParallelSource() throws Exception {
        mockMvc.perform(get("/eventos/999999/escala")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/eventos/1/escala"))
                .andExpect(status().isUnauthorized());
    }

    private String getAuthorizedJson(Long eventId) throws Exception {
        return mockMvc.perform(get("/eventos/{id}/escala", eventId)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private CelebrationEventWithScaleRequestDTO eventRequest(
            Long locationId,
            Long priestId,
            List<Long> readerIds,
            List<Long> commentatorIds,
            List<Long> ministerOfTheWordIds,
            List<Long> eucharisticMinisterIds
    ) {
        CelebrationEventWithScaleRequestDTO request = new CelebrationEventWithScaleRequestDTO();
        request.setNameMassOrEvent("Parallel Cutover Mass " + UUID.randomUUID());
        request.setStartAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0)));
        request.setEndAt(LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(20, 0)));
        request.setMassOrCelebration(true);
        request.setLocationId(locationId);
        request.setPriestId(priestId);
        request.setReaderIds(readerIds);
        request.setCommentatorIds(commentatorIds);
        request.setMinisterOfTheWordIds(ministerOfTheWordIds);
        request.setEucharisticMinisterIds(eucharisticMinisterIds);
        return request;
    }

    private Long personIdByMinistry(MinistryType ministryType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT p.id
                FROM tb_person_ministry pm
                INNER JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.ministry_type = ?
                  AND pm.active = TRUE
                ORDER BY p.id
                LIMIT 1
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

    private Long insertEvent(String name) {
        String eventName = name + " " + UUID.randomUUID();
        LocalDateTime startAt = LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0));
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
