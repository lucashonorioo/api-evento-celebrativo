package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.commentator=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class CommentatorMinistryReadCutoverParallelIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CommentatorRepository commentatorRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseActiveCommentatorMinistryAsOfficialSourceWithoutChangingData() throws Exception {
        Commentator activeCommentator = new Commentator();
        populatePerson(activeCommentator, "000 Cutover Active Commentator");
        Person savedActiveCommentator = personRepository.saveAndFlush(activeCommentator);
        saveMinistry(savedActiveCommentator, MinistryType.COMMENTATOR, true);

        Reader readerWithCommentatorMinistry = new Reader();
        populatePerson(readerWithCommentatorMinistry, "000 Cutover Reader Commentator");
        Person savedReader = personRepository.saveAndFlush(readerWithCommentatorMinistry);
        saveMinistry(savedReader, MinistryType.READER, true);
        saveMinistry(savedReader, MinistryType.COMMENTATOR, true);

        Commentator inactiveCommentator = new Commentator();
        populatePerson(inactiveCommentator, "000 Cutover Inactive Commentator");
        Person savedInactiveCommentator = personRepository.saveAndFlush(inactiveCommentator);
        saveMinistry(savedInactiveCommentator, MinistryType.COMMENTATOR, false);

        Commentator multiMinistryCommentator = new Commentator();
        populatePerson(multiMinistryCommentator, "000 Cutover Multi Commentator");
        Person savedMultiMinistryCommentator = personRepository.saveAndFlush(multiMinistryCommentator);
        saveMinistry(savedMultiMinistryCommentator, MinistryType.COMMENTATOR, true);
        saveMinistry(savedMultiMinistryCommentator, MinistryType.READER, true);

        int ministryRowsBefore = countPersonMinistryRows();
        List<Long> expectedCommentatorIds = activeCommentatorMinistryIdsOrderedByNameAndId();

        MvcResult result = getCommentators();
        List<PersonPayload> payload = responsePayloadFrom(result);
        List<Long> responseIds = payload.stream().map(PersonPayload::id).toList();

        assertEquals(expectedCommentatorIds, responseIds);
        assertTrue(responseIds.contains(savedActiveCommentator.getId()));
        assertTrue(responseIds.contains(savedReader.getId()));
        assertTrue(responseIds.contains(savedMultiMinistryCommentator.getId()));
        assertFalse(responseIds.contains(savedInactiveCommentator.getId()));
        assertEquals(1, responseIds.stream().filter(savedMultiMinistryCommentator.getId()::equals).count());
        assertFalse(commentatorRepository.findAll().stream().map(Person::getId).toList().contains(savedReader.getId()));
        assertEquals("reader", personType(savedReader.getId()));
        assertEquals(ministryRowsBefore, countPersonMinistryRows());

        PersonPayload readerPayload = payload.stream()
                .filter(item -> item.id().equals(savedReader.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(savedReader.getName(), readerPayload.name());
        assertEquals(savedReader.getPhoneNumber(), readerPayload.phoneNumber());
        assertEquals(savedReader.getBirthdayDate().toString(), readerPayload.birthdayDate());

        JsonNode firstNode = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertTrue(firstNode.path("id").isNumber());
        assertTrue(firstNode.path("name").isTextual());
        assertTrue(firstNode.path("phoneNumber").isTextual());
        assertTrue(firstNode.path("birthdayDate").isTextual());
    }

    private MvcResult getCommentators() throws Exception {
        return mockMvc.perform(get("/comentaristas").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void populatePerson(Person person, String name) {
        person.setName(name);
        person.setPhoneNumber(uniquePhoneNumber());
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
    }

    private void saveMinistry(Person person, MinistryType ministryType, boolean active) {
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(active);
        personMinistryRepository.saveAndFlush(ministry);
    }

    private List<Long> activeCommentatorMinistryIdsOrderedByNameAndId() {
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
                MinistryType.COMMENTATOR.name()
        );
    }

    private int countPersonMinistryRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_person_ministry", Integer.class);
        return count == null ? 0 : count;
    }

    private String personType(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT person_type FROM tb_person WHERE id = ?",
                String.class,
                personId
        );
    }

    private String uniquePhoneNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "34977" + suffix;
    }

    private List<PersonPayload> responsePayloadFrom(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return StreamSupport.stream(root.spliterator(), false)
                .map(node -> new PersonPayload(
                        node.path("id").asLong(),
                        node.path("name").asText(),
                        node.path("phoneNumber").asText(),
                        node.path("birthdayDate").asText()
                ))
                .toList();
    }

    private record PersonPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }
}
