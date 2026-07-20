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
        "app.person-ministry.read-source.commentator=LEGACY",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@Transactional
class CommentatorLegacyCutoverConsistencyIntegrationTest {

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
    void shouldKeepLegacySourceAndContractsForCommentatorLifecycle() throws Exception {
        assertEquals(PersonMinistryReadSource.LEGACY, readSourceProperties.getCommentator());
        assertFalse(shadowReadProperties.isCommentatorEnabled());

        Reader readerWithCommentatorMinistry = new Reader();
        readerWithCommentatorMinistry.setName("Legacy Additional Commentator Ministry");
        readerWithCommentatorMinistry.setPhoneNumber(uniquePhoneNumber());
        readerWithCommentatorMinistry.setBirthdayDate(BIRTHDAY);
        readerWithCommentatorMinistry.setPassword("encoded-password");
        Person savedReader = personRepository.saveAndFlush(readerWithCommentatorMinistry);
        personMinistryRepository.saveAndFlush(new PersonMinistry(savedReader, MinistryType.COMMENTATOR));

        String createPhoneNumber = uniquePhoneNumber();
        MvcResult createResult = mockMvc.perform(post("/comentaristas")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentatorPayload("Legacy Lifecycle Commentator", createPhoneNumber)))
                .andExpect(status().isCreated())
                .andReturn();

        PersonPayload createdCommentator = payloadFromObject(createResult);
        assertFalse(getCommentatorIds().contains(savedReader.getId()));
        assertEquals("Legacy Lifecycle Commentator", createdCommentator.name());
        assertEquals(createPhoneNumber, createdCommentator.phoneNumber());

        String updatedPhoneNumber = uniquePhoneNumber();
        mockMvc.perform(put("/comentaristas/{id}", createdCommentator.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentatorPayload("Legacy Lifecycle Commentator Updated", updatedPhoneNumber)))
                .andExpect(status().isOk());

        List<PersonPayload> commentatorsAfterUpdate = getCommentators();
        assertEquals(1, commentatorsAfterUpdate.stream()
                .filter(commentator -> commentator.id().equals(createdCommentator.id()))
                .count());
        assertFalse(commentatorsAfterUpdate.stream().anyMatch(commentator ->
                commentator.phoneNumber().equals(createPhoneNumber)));

        mockMvc.perform(delete("/comentaristas/{id}", createdCommentator.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertFalse(getCommentatorIds().contains(createdCommentator.id()));
        assertFalse(personRepository.existsById(createdCommentator.id()));
    }

    private List<Long> getCommentatorIds() throws Exception {
        return getCommentators().stream().map(PersonPayload::id).toList();
    }

    private List<PersonPayload> getCommentators() throws Exception {
        MvcResult result = mockMvc.perform(get("/comentaristas").with(user("operator").roles("OPERATOR")))
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

    private String commentatorPayload(String name, String phoneNumber) {
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
