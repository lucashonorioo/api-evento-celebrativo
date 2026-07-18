package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.ReaderRepository;
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
        "app.person-ministry.read-source.reader=PARALLEL",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class ReaderMinistryReadCutoverParallelIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseActiveReaderMinistryAsOfficialSourceWithoutChangingData() throws Exception {
        Reader activeReader = new Reader();
        populatePerson(activeReader, "000 Cutover Active Reader");
        Person savedActiveReader = personRepository.saveAndFlush(activeReader);
        saveMinistry(savedActiveReader, MinistryType.READER, true);

        Commentator commentatorWithReaderMinistry = new Commentator();
        populatePerson(commentatorWithReaderMinistry, "000 Cutover Commentator Reader");
        Person savedCommentator = personRepository.saveAndFlush(commentatorWithReaderMinistry);
        saveMinistry(savedCommentator, MinistryType.COMMENTATOR, true);
        saveMinistry(savedCommentator, MinistryType.READER, true);

        Reader inactiveReader = new Reader();
        populatePerson(inactiveReader, "000 Cutover Inactive Reader");
        Person savedInactiveReader = personRepository.saveAndFlush(inactiveReader);
        saveMinistry(savedInactiveReader, MinistryType.READER, false);

        Reader multiMinistryReader = new Reader();
        populatePerson(multiMinistryReader, "000 Cutover Multi Reader");
        Person savedMultiMinistryReader = personRepository.saveAndFlush(multiMinistryReader);
        saveMinistry(savedMultiMinistryReader, MinistryType.READER, true);
        saveMinistry(savedMultiMinistryReader, MinistryType.COMMENTATOR, true);

        int ministryRowsBefore = countPersonMinistryRows();
        List<Long> expectedReaderIds = activeReaderMinistryIdsOrderedByNameAndId();

        MvcResult result = getReaders();
        List<PersonPayload> payload = responsePayloadFrom(result);
        List<Long> responseIds = payload.stream().map(PersonPayload::id).toList();

        assertEquals(expectedReaderIds, responseIds);
        assertTrue(responseIds.contains(savedActiveReader.getId()));
        assertTrue(responseIds.contains(savedCommentator.getId()));
        assertTrue(responseIds.contains(savedMultiMinistryReader.getId()));
        assertFalse(responseIds.contains(savedInactiveReader.getId()));
        assertEquals(1, responseIds.stream().filter(savedMultiMinistryReader.getId()::equals).count());
        assertFalse(readerRepository.findAll().stream().map(Person::getId).toList().contains(savedCommentator.getId()));
        assertEquals("commentator", personType(savedCommentator.getId()));
        assertEquals(ministryRowsBefore, countPersonMinistryRows());

        PersonPayload commentatorPayload = payload.stream()
                .filter(item -> item.id().equals(savedCommentator.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(savedCommentator.getName(), commentatorPayload.name());
        assertEquals(savedCommentator.getPhoneNumber(), commentatorPayload.phoneNumber());
        assertEquals(savedCommentator.getBirthdayDate().toString(), commentatorPayload.birthdayDate());

        JsonNode firstNode = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertTrue(firstNode.path("id").isNumber());
        assertTrue(firstNode.path("name").isTextual());
        assertTrue(firstNode.path("phoneNumber").isTextual());
        assertTrue(firstNode.path("birthdayDate").isTextual());
    }

    private MvcResult getReaders() throws Exception {
        return mockMvc.perform(get("/leitores").with(user("operator").roles("OPERATOR")))
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

    private List<Long> activeReaderMinistryIdsOrderedByNameAndId() {
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
                MinistryType.READER.name()
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
