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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.reader=LEGACY",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class ReaderMinistryReadCutoverLegacyIntegrationTest {

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

    @Test
    void shouldKeepLegacyReaderRepositoryAsOfficialSource() throws Exception {
        Commentator commentatorWithReaderMinistry = new Commentator();
        populatePerson(commentatorWithReaderMinistry, "Zzz Legacy Additional Reader Function");
        Person savedCommentator = personRepository.saveAndFlush(commentatorWithReaderMinistry);
        saveMinistry(savedCommentator, MinistryType.COMMENTATOR, true);
        saveMinistry(savedCommentator, MinistryType.READER, true);

        List<Reader> legacyReaders = readerRepository.findAll();

        MvcResult result = getReaders();

        assertEquals(payloadFrom(legacyReaders), responsePayloadFrom(result));
        assertFalse(responsePayloadFrom(result).stream()
                .map(PersonPayload::id)
                .toList()
                .contains(savedCommentator.getId()));
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

    private String uniquePhoneNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "34977" + suffix;
    }

    private List<PersonPayload> payloadFrom(List<? extends Person> people) {
        return people.stream()
                .map(person -> new PersonPayload(
                        person.getId(),
                        person.getName(),
                        person.getPhoneNumber(),
                        person.getBirthdayDate().toString()
                ))
                .toList();
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
