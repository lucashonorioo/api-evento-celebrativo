package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, pelos endpoints reais e pelo servico de sincronizacao real (H2, transacoes reais), que
 * UserAccount permanece espelhando Person em todos os fluxos de producao integrados nesta etapa:
 * Person.phoneNumber = UserAccount.username, Person.password = UserAccount.passwordHash e
 * Person.roles = UserAccount.roles.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class UserAccountSynchronizationIntegrationTest {

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
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private UserAccountSynchronizationService userAccountSynchronizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateMirroredUserAccountForEachOfTheFiveMinisterialCreationFlows() throws Exception {
        List<Long> personIds = List.of(
                createReader("Sync Reader"),
                createCommentator("Sync Commentator"),
                createPriest("Sync Priest"),
                createMinisterOfTheWord("Sync Word Minister"),
                createEucharisticMinister("Sync Eucharistic Minister")
        );
        try {
            for (Long personId : personIds) {
                assertAccountMirrorsPerson(personId);
                assertEquals(Set.of("ROLE_OPERATOR"), roleAuthoritiesOfAccount(personId));
                assertTrue(userAccountRepository.findByPersonId(personId).orElseThrow().isEnabled());
            }
        } finally {
            personIds.forEach(this::cleanupPerson);
        }
    }

    @Test
    void shouldSyncUsernameAndPasswordHashWithoutRecryptingWhenMinisterialUpdateChangesThem() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Sync Update Reader");
            UserAccount before = userAccountRepository.findByPersonId(personId).orElseThrow();
            Long accountId = before.getId();

            String newPhone = uniquePhoneNumber();
            mockMvc.perform(put("/leitores/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ReaderRequestDTO("Sync Update Reader Renamed", newPhone, BIRTHDAY, "654321"))))
                    .andExpect(status().isOk());

            Person person = personRepository.findById(personId).orElseThrow();
            UserAccount after = userAccountRepository.findByPersonId(personId).orElseThrow();

            assertEquals(accountId, after.getId());
            assertEquals(newPhone, after.getUsername());
            assertEquals(person.getPassword(), after.getPasswordHash());
            assertNotEquals(before.getPasswordHash(), after.getPasswordHash());
            verify(passwordEncoder, times(1)).encode("654321");
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldSyncRolesPreserveIdAndCreatedAtAndChangeUpdatedAtOnRoleEndpoint() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Sync Role Reader");
            UserAccount before = userAccountRepository.findByPersonId(personId).orElseThrow();

            mockMvc.perform(put("/pessoas/{id}/roles", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            UserAccount after = userAccountRepository.findByPersonId(personId).orElseThrow();

            assertEquals(before.getId(), after.getId());
            assertEquals(before.getCreatedAt(), after.getCreatedAt());
            assertTrue(!after.getUpdatedAt().isBefore(before.getUpdatedAt()));
            assertEquals(before.isEnabled(), after.isEnabled());
            assertEquals(Set.of("ROLE_ADMIN"), roleAuthoritiesOfAccount(personId));
            assertAccountMirrorsPerson(personId);
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldNotReenableDisabledAccountWhenRolesAreSynchronized() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Sync Disabled Reader");
            jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE WHERE person_id = ?", personId);

            mockMvc.perform(put("/pessoas/{id}/roles", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PersonRoleUpdateRequestDTO("ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            UserAccount after = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertTrue(!after.isEnabled());
            assertEquals(Set.of("ROLE_ADMIN"), roleAuthoritiesOfAccount(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldFailAndRollBackWholeUpdateWhenAccountIsMissingForExistingPerson() throws Exception {
        Long personId = null;
        try {
            Person orphanPerson = new Person();
            orphanPerson.setName("Legacy Orphan Reader");
            orphanPerson.setPhoneNumber(uniquePhoneNumber());
            orphanPerson.setPassword("original-hash");
            orphanPerson.addRole(roleRepository.findByAuthority("ROLE_OPERATOR").orElseThrow());
            orphanPerson = personRepository.saveAndFlush(orphanPerson);
            personId = orphanPerson.getId();
            personMinistryRepository.saveAndFlush(new PersonMinistry(orphanPerson, MinistryType.READER));
            assertTrue(userAccountRepository.findByPersonId(personId).isEmpty());

            String attemptedPhone = uniquePhoneNumber();
            mockMvc.perform(put("/leitores/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ReaderRequestDTO("Legacy Orphan Reader Updated", attemptedPhone, BIRTHDAY, "new-password"))))
                    .andExpect(status().isConflict());

            Person unchanged = personRepository.findById(personId).orElseThrow();
            assertEquals("original-hash", unchanged.getPassword());
            assertNotEquals(attemptedPhone, unchanged.getPhoneNumber());
            assertTrue(userAccountRepository.findByPersonId(personId).isEmpty());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldFailWhenSynchronizingNewPersonThatAlreadyHasAnAccount() {
        Long personId = null;
        try {
            Person person = new Person();
            person.setName("Duplicate Account Person");
            person.setPhoneNumber(uniquePhoneNumber());
            person.setPassword("hash");
            person = personRepository.saveAndFlush(person);
            personId = person.getId();

            UserAccount preexisting = new UserAccount(
                    person, person.getPhoneNumber(), person.getPassword(),
                    LocalDateTime.now().withNano(0), LocalDateTime.now().withNano(0));
            userAccountRepository.saveAndFlush(preexisting);

            Person finalPerson = person;
            assertThrows(RuntimeException.class,
                    () -> userAccountSynchronizationService.synchronizeNewPerson(finalPerson));
        } finally {
            cleanupPerson(personId);
        }
    }

    private void assertAccountMirrorsPerson(Long personId) {
        Person person = personRepository.findById(personId).orElseThrow();
        UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
        assertEquals(person.getPhoneNumber(), account.getUsername());
        assertEquals(person.getPassword(), account.getPasswordHash());
    }

    private Set<String> roleAuthoritiesOfAccount(Long personId) {
        UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
        return userAccountRoleRepository.findByUserAccountId(account.getId()).stream()
                .map(UserAccountRole::getRole)
                .map(Role::getAuthority)
                .collect(Collectors.toSet());
    }

    private Long createReader(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/leitores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReaderRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createCommentator(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/comentaristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommentatorRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createPriest(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/padres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PriestRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createMinisterOfTheWord(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/ministrosDaPalavra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MinisterOfTheWordRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createEucharisticMinister(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/ministrosDeEucaristia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EucharisticMinisterRequestDTO(name, uniquePhoneNumber(), BIRTHDAY, "123456"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void cleanupPerson(Long personId) {
        if (personId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person_role WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }
}
