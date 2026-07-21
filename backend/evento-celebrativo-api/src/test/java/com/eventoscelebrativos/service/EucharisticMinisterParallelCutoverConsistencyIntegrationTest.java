package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.eucharistic-minister=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class EucharisticMinisterParallelCutoverConsistencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EucharisticMinisterRepository eucharisticMinisterRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Test
    void shouldKeepEucharisticMinisterLifecycleConsistentWhenParallelReadIsOfficialSource() throws Exception {
        Long ministerId = null;
        try {
            List<PersonPayload> initialMinisters = getEucharisticMinisters();
            Set<Long> initialMinisterIds = idsFrom(initialMinisters);
            String createPhoneNumber = uniquePhoneNumber();
            String updatePhoneNumber = uniquePhoneNumber();

            assertFalse(initialMinisters.stream().anyMatch(minister -> minister.phoneNumber().equals(createPhoneNumber)));
            assertEquivalentLegacyAndParallelMinisterSources();

            MvcResult createResult = postEucharisticMinister("Eucharistic Minister Cutover Alpha", createPhoneNumber)
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andReturn();

            PersonPayload createdMinister = payloadFromObject(createResult);
            ministerId = createdMinister.id();
            Long createdMinisterId = ministerId;
            assertFalse(initialMinisterIds.contains(ministerId));
            assertEquals("Eucharistic Minister Cutover Alpha", createdMinister.name());
            assertEquals(createPhoneNumber, createdMinister.phoneNumber());
            assertEquals(BIRTHDAY.toString(), createdMinister.birthdayDate());
            assertJsonContract(createResult);

            assertPersonRow(ministerId, "Eucharistic Minister Cutover Alpha", createPhoneNumber, "eucharistic_minister");
            MinistrySnapshot createdMinistry = assertSingleMinistry(ministerId, MinistryType.EUCHARISTIC_MINISTER, true);
            assertNotNull(createdMinistry.createdAt());
            assertNotNull(createdMinistry.updatedAt());
            assertEquals(1, countMinistries(ministerId));

            int ministryRowsBeforeRead = countAllMinistries();
            List<PersonPayload> ministersAfterCreate = getEucharisticMinisters();
            assertEquals(ministryRowsBeforeRead, countAllMinistries());
            assertContainsOnce(ministersAfterCreate, ministerId);
            assertEquals(idsOrderedByActiveEucharisticMinisterMinistry(), ministersAfterCreate.stream().map(PersonPayload::id).toList());
            assertEquivalentLegacyAndParallelMinisterSources();

            MvcResult updateResult = putEucharisticMinister(ministerId, "Eucharistic Minister Cutover Beta", updatePhoneNumber)
                    .andExpect(status().isOk())
                    .andReturn();

            PersonPayload updatedMinister = payloadFromObject(updateResult);
            assertEquals(ministerId, updatedMinister.id());
            assertEquals("Eucharistic Minister Cutover Beta", updatedMinister.name());
            assertEquals(updatePhoneNumber, updatedMinister.phoneNumber());
            assertJsonContract(updateResult);

            assertPersonRow(ministerId, "Eucharistic Minister Cutover Beta", updatePhoneNumber, "eucharistic_minister");
            MinistrySnapshot updatedMinistry = assertSingleMinistry(ministerId, MinistryType.EUCHARISTIC_MINISTER, true);
            assertEquals(createdMinistry.id(), updatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), updatedMinistry.createdAt());
            assertEquals(1, countMinistries(ministerId, MinistryType.EUCHARISTIC_MINISTER));

            putEucharisticMinister(ministerId, "Eucharistic Minister Cutover Beta 2", updatePhoneNumber)
                    .andExpect(status().isOk());
            assertEquals(1, countMinistries(ministerId, MinistryType.EUCHARISTIC_MINISTER));

            List<PersonPayload> ministersAfterUpdate = getEucharisticMinisters();
            assertContainsOnce(ministersAfterUpdate, ministerId);
            assertTrue(ministersAfterUpdate.stream().anyMatch(minister ->
                    minister.id().equals(createdMinisterId)
                            && minister.name().equals("Eucharistic Minister Cutover Beta 2")
                            && minister.phoneNumber().equals(updatePhoneNumber)));
            assertFalse(ministersAfterUpdate.stream().anyMatch(minister -> minister.phoneNumber().equals(createPhoneNumber)));
            assertEquals(idsOrderedByActiveEucharisticMinisterMinistry(), ministersAfterUpdate.stream().map(PersonPayload::id).toList());
            assertEquivalentLegacyAndParallelMinisterSources();

            deactivateEucharisticMinisterMinistry(ministerId);
            MinistrySnapshot inactiveMinistry = assertSingleMinistry(ministerId, MinistryType.EUCHARISTIC_MINISTER, false);
            assertTrue(legacyMinisterIds().contains(ministerId));
            assertFalse(activeMinisterIds().contains(ministerId));
            assertFalse(getEucharisticMinisters().stream().anyMatch(minister -> minister.id().equals(createdMinisterId)));

            Thread.sleep(25);
            putEucharisticMinister(ministerId, "Eucharistic Minister Cutover Gamma", updatePhoneNumber)
                    .andExpect(status().isOk());

            MinistrySnapshot reactivatedMinistry = assertSingleMinistry(ministerId, MinistryType.EUCHARISTIC_MINISTER, true);
            assertEquals(createdMinistry.id(), reactivatedMinistry.id());
            assertEquals(createdMinistry.createdAt(), reactivatedMinistry.createdAt());
            assertNotEquals(inactiveMinistry.updatedAt(), reactivatedMinistry.updatedAt());
            assertContainsOnce(getEucharisticMinisters(), ministerId);
            assertEquivalentLegacyAndParallelMinisterSources();

            addMinistry(ministerId, MinistryType.READER);
            putEucharisticMinister(ministerId, "Eucharistic Minister Cutover Delta", updatePhoneNumber)
                    .andExpect(status().isOk());

            assertEquals(
                    Set.of(MinistryType.EUCHARISTIC_MINISTER, MinistryType.READER),
                    new LinkedHashSet<>(ministryTypes(ministerId))
            );
            assertSingleMinistry(ministerId, MinistryType.EUCHARISTIC_MINISTER, true);
            assertContainsOnce(getEucharisticMinisters(), ministerId);
            assertEquivalentLegacyAndParallelMinisterSources();

            deleteEucharisticMinister(ministerId)
                    .andExpect(status().isNoContent());

            assertFalse(personRepository.existsById(ministerId));
            assertFalse(eucharisticMinisterRepository.existsById(ministerId));
            assertEquals(0, countMinistries(ministerId));
            assertEquals(0, countOrphanMinistries(ministerId));
            assertFalse(getEucharisticMinisters().stream().anyMatch(minister -> minister.id().equals(createdMinisterId)));
            assertEquals(initialMinisterIds, legacyMinisterIds());
            assertEquivalentLegacyAndParallelMinisterSources();
        } finally {
            cleanupMinister(ministerId);
        }
    }

    @Test
    void shouldNotCreateDuplicateEucharisticMinisterMinistryAfterConsecutiveUpdates() throws Exception {
        Long ministerId = null;
        try {
            ministerId = payloadFromObject(postEucharisticMinister("Eucharistic Minister Sequential Update", uniquePhoneNumber())
                    .andExpect(status().isCreated())
                    .andReturn()).id();

            putEucharisticMinister(ministerId, "Eucharistic Minister Sequential Update B", uniquePhoneNumber())
                    .andExpect(status().isOk());
            putEucharisticMinister(ministerId, "Eucharistic Minister Sequential Update C", uniquePhoneNumber())
                    .andExpect(status().isOk());

            assertEquals(1, countMinistries(ministerId, MinistryType.EUCHARISTIC_MINISTER));
            assertContainsOnce(getEucharisticMinisters(), ministerId);
        } finally {
            cleanupMinister(ministerId);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postEucharisticMinister(String name, String phoneNumber) throws Exception {
        return mockMvc.perform(post("/ministrosDeEucaristia")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ministerPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions putEucharisticMinister(Long ministerId, String name, String phoneNumber) throws Exception {
        return mockMvc.perform(put("/ministrosDeEucaristia/{id}", ministerId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ministerPayload(name, phoneNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteEucharisticMinister(Long ministerId) throws Exception {
        return mockMvc.perform(delete("/ministrosDeEucaristia/{id}", ministerId)
                .with(user("admin").roles("ADMIN"))
                .with(csrf()));
    }

    private List<PersonPayload> getEucharisticMinisters() throws Exception {
        MvcResult result = mockMvc.perform(get("/ministrosDeEucaristia").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(this::payloadFromNode)
                .toList();
    }

    private PersonPayload payloadFromObject(MvcResult result) throws Exception {
        return payloadFromNode(objectMapper.readTree(result.getResponse().getContentAsString()));
    }

    private PersonPayload payloadFromNode(JsonNode node) {
        return new PersonPayload(
                node.path("id").asLong(),
                node.path("name").asText(),
                node.path("phoneNumber").asText(),
                node.path("birthdayDate").asText()
        );
    }

    private void assertJsonContract(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(Set.of("id", "name", "phoneNumber", "birthdayDate"), fieldNames(node));
        assertTrue(node.path("id").isNumber());
        assertTrue(node.path("name").isTextual());
        assertTrue(node.path("phoneNumber").isTextual());
        assertTrue(node.path("birthdayDate").isTextual());
        assertFalse(node.has("password"));
        assertFalse(node.has("ministryType"));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private void assertContainsOnce(List<PersonPayload> ministers, Long ministerId) {
        assertEquals(1, ministers.stream().filter(minister -> minister.id().equals(ministerId)).count());
    }

    private void assertPersonRow(Long ministerId, String expectedName, String expectedPhoneNumber, String expectedPersonType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person
                WHERE id = ?
                  AND name = ?
                  AND phone_number = ?
                  AND person_type = ?
                """,
                Integer.class,
                ministerId,
                expectedName,
                expectedPhoneNumber,
                expectedPersonType
        );
        assertEquals(1, count);
        assertEquals(expectedPersonType, personType(ministerId));
    }

    private MinistrySnapshot assertSingleMinistry(Long personId, MinistryType ministryType, boolean active) {
        List<MinistrySnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, active, created_at, updated_at
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                (rs, rowNum) -> new MinistrySnapshot(
                        rs.getLong("id"),
                        rs.getBoolean("active"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                ),
                personId,
                ministryType.name()
        );
        assertEquals(1, rows.size());
        assertEquals(active, rows.get(0).active());
        return rows.get(0);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void deactivateEucharisticMinisterMinistry(Long personId) {
        assertEquals(1, jdbcTemplate.update(
                """
                UPDATE tb_person_ministry
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                personId,
                MinistryType.EUCHARISTIC_MINISTER.name()
        ));
    }

    private void addMinistry(Long personId, MinistryType ministryType) {
        assertEquals(1, jdbcTemplate.update(
                """
                INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at)
                VALUES (?, ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                personId,
                ministryType.name()
        ));
    }

    private Set<Long> legacyMinisterIds() {
        return new LinkedHashSet<>(eucharisticMinisterRepository.findAll().stream()
                .map(minister -> minister.getId())
                .toList());
    }

    private Set<Long> activeMinisterIds() {
        return new LinkedHashSet<>(idsOrderedByActiveEucharisticMinisterMinistry());
    }

    private List<Long> idsOrderedByActiveEucharisticMinisterMinistry() {
        return jdbcTemplate.queryForList(
                """
                SELECT p.id
                FROM tb_person_ministry pm
                INNER JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.ministry_type = ?
                  AND pm.active = TRUE
                ORDER BY p.name ASC, p.id ASC
                """,
                Long.class,
                MinistryType.EUCHARISTIC_MINISTER.name()
        );
    }

    private void assertEquivalentLegacyAndParallelMinisterSources() {
        assertEquals(legacyMinisterIds(), activeMinisterIds());
    }

    private Set<Long> idsFrom(List<PersonPayload> ministers) {
        return new LinkedHashSet<>(ministers.stream().map(PersonPayload::id).toList());
    }

    private String personType(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT person_type FROM tb_person WHERE id = ?",
                String.class,
                personId
        );
    }

    private List<MinistryType> ministryTypes(Long personId) {
        return personMinistryRepository.findAllByPersonId(personId).stream()
                .map(ministry -> ministry.getMinistryType())
                .toList();
    }

    private int countAllMinistries() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person_ministry", Integer.class);
        return count == null ? 0 : count;
    }

    private int countMinistries(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_person_ministry WHERE person_id = ?",
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private int countMinistries(Long personId, MinistryType ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                """,
                Integer.class,
                personId,
                ministryType.name()
        );
        return count == null ? 0 : count;
    }

    private int countOrphanMinistries(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry pm
                LEFT JOIN tb_person p ON p.id = pm.person_id
                WHERE pm.person_id = ?
                  AND p.id IS NULL
                """,
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private void cleanupMinister(Long ministerId) {
        if (ministerId == null || !personRepository.existsById(ministerId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", ministerId);
        jdbcTemplate.update("DELETE FROM tb_event_person WHERE person_id = ?", ministerId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", ministerId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", ministerId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", ministerId);
    }

    private String ministerPayload(String name, String phoneNumber) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "%s",
                  "password": "123456"
                }
                """.formatted(name, phoneNumber, BIRTHDAY);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }

    private record PersonPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }

    private record MinistrySnapshot(Long id, Boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
