package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.PersonMinistryReadSource;
import com.eventoscelebrativos.config.PersonMinistryReadSourceProperties;
import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.person-ministry.read-source.priest=LEGACY",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class PriestLegacyCutoverConsistencyIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private PersonMinistryReadSourceProperties readSourceProperties;

    @Autowired
    private PersonMinistryShadowReadProperties shadowReadProperties;

    @Test
    void shouldKeepLegacySourceAndContractsForPriestLifecycle() throws Exception {
        assertEquals(PersonMinistryReadSource.LEGACY, readSourceProperties.getPriest());
        assertFalse(shadowReadProperties.isPriestEnabled());

        Reader readerWithPriestMinistry = new Reader();
        readerWithPriestMinistry.setName("Legacy Additional Priest Ministry");
        readerWithPriestMinistry.setPhoneNumber(uniquePhoneNumber());
        readerWithPriestMinistry.setBirthdayDate(BIRTHDAY);
        readerWithPriestMinistry.setPassword("encoded-password");
        Person savedReader = personRepository.saveAndFlush(readerWithPriestMinistry);
        personMinistryRepository.saveAndFlush(new PersonMinistry(savedReader, MinistryType.PRIEST));

        String createPhoneNumber = uniquePhoneNumber();
        MvcResult createResult = mockMvc.perform(post("/padres")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priestPayload("Legacy Lifecycle Priest", createPhoneNumber)))
                .andExpect(status().isCreated())
                .andReturn();

        PersonPayload createdPriest = payloadFromObject(createResult);
        assertFalse(getPriestIds().contains(savedReader.getId()));
        assertEquals("Legacy Lifecycle Priest", createdPriest.name());
        assertEquals(createPhoneNumber, createdPriest.phoneNumber());

        String updatedPhoneNumber = uniquePhoneNumber();
        mockMvc.perform(put("/padres/{id}", createdPriest.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(priestPayload("Legacy Lifecycle Priest Updated", updatedPhoneNumber)))
                .andExpect(status().isOk());

        List<PersonPayload> priestsAfterUpdate = getPriests();
        assertEquals(1, priestsAfterUpdate.stream()
                .filter(priest -> priest.id().equals(createdPriest.id()))
                .count());
        assertFalse(priestsAfterUpdate.stream().anyMatch(priest ->
                priest.phoneNumber().equals(createPhoneNumber)));

        mockMvc.perform(delete("/padres/{id}", createdPriest.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertFalse(getPriestIds().contains(createdPriest.id()));
        assertFalse(personRepository.existsById(createdPriest.id()));
    }

    private List<Long> getPriestIds() throws Exception {
        return getPriests().stream().map(PersonPayload::id).toList();
    }

    private List<PersonPayload> getPriests() throws Exception {
        MvcResult result = mockMvc.perform(get("/padres").with(user("operator").roles("OPERATOR")))
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

    private String priestPayload(String name, String phoneNumber) {
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
        return "3497" + String.format("%07d", suffix);
    }

    private record PersonPayload(Long id, String name, String phoneNumber, String birthdayDate) {
    }
}
