package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, pelos endpoints reais e por PersonAccountCoordinator real (H2, transacoes reais), que os
 * cinco fluxos de criacao ministerial provisionam UserAccount/UserAccountRole corretamente e que
 * atualizacoes cadastrais (telefone) propagam para UserAccount.username sem recriptar senha nem
 * aceitar campos de conta. Person nao carrega mais password nem roles - nao ha "espelhamento" para
 * verificar, apenas o resultado do provisionamento e da sincronizacao de telefone/username.
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
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateAccountWithSingleOperatorRoleForEachOfTheFiveMinisterialCreationFlows() throws Exception {
        List<Long> personIds = List.of(
                createReader("Sync Reader"),
                createCommentator("Sync Commentator"),
                createPriest("Sync Priest"),
                createMinisterOfTheWord("Sync Word Minister"),
                createEucharisticMinister("Sync Eucharistic Minister")
        );
        try {
            for (Long personId : personIds) {
                UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
                assertTrue(personRepository.findById(personId).isPresent());
                assertEquals(Set.of("ROLE_OPERATOR"), roleAuthoritiesOfAccount(personId));
                assertTrue(account.isEnabled());
            }
        } finally {
            personIds.forEach(this::cleanupPerson);
        }
    }

    @Test
    void shouldSyncUsernameWithoutRecryptingPasswordWhenMinisterialUpdateChangesPhoneNumber() throws Exception {
        Long personId = null;
        try {
            personId = createReader("Sync Update Reader");
            UserAccount before = userAccountRepository.findByPersonId(personId).orElseThrow();
            Long accountId = before.getId();
            long tokenVersionBefore = before.getTokenVersion();

            String newPhone = uniquePhoneNumber();
            mockMvc.perform(put("/leitores/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ReaderUpdateRequestDTO("Sync Update Reader Renamed", newPhone, BIRTHDAY))))
                    .andExpect(status().isOk());

            UserAccount after = userAccountRepository.findByPersonId(personId).orElseThrow();

            assertEquals(accountId, after.getId());
            assertEquals(newPhone, after.getUsername());
            assertEquals(before.getPasswordHash(), after.getPasswordHash());
            assertEquals(tokenVersionBefore + 1, after.getTokenVersion());
            verify(passwordEncoder, times(1)).encode("123456");
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldRejectPasswordFieldOnMinisterialUpdateEvenForOrphanPerson() throws Exception {
        Long personId = createReader("Reader Attempting Password Update");
        try {
            String attemptedPhone = uniquePhoneNumber();
            mockMvc.perform(put("/leitores/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Reader Renamed\",\"phoneNumber\":\"" + attemptedPhone
                                    + "\",\"birthdayDate\":\"" + BIRTHDAY + "\",\"password\":\"new-password\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode")
                            .value("ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE"));

            UserAccount unchanged = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertNotEquals(attemptedPhone, unchanged.getUsername());
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
        jdbcTemplate.update("DELETE FROM tb_user_account_role WHERE user_account_id IN "
                + "(SELECT id FROM tb_user_account WHERE person_id = ?)", personId);
        jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);
        jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3496" + String.format("%07d", suffix);
    }
}
