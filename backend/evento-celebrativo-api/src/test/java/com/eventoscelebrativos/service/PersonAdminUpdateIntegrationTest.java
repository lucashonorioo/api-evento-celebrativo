package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.normalizedName;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta pelo endpoint real PUT /pessoas/{id}, o update cadastral administrativo:
 * whitelist estrita de campos, unicidade de telefone, sincronizacao de username/tokenVersion,
 * ausencia de efeitos colaterais sobre role/ministerio/active/enabled/conta e rollback em conflito.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class PersonAdminUpdateIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateNamePhoneAndBirthdayForPersonWithAccountAndSyncUsername() throws Exception {
        Long personId = null;
        try {
            personId = savePersonWithRole("Admin Update Person", "ROLE_OPERATOR");
            String newPhone = uniquePhoneNumber();
            UserAccount accountBefore = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertEquals(0L, accountBefore.getTokenVersion());

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload("Renamed Person", newPhone, "1985-06-15")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.name").value("Renamed Person"))
                    .andExpect(jsonPath("$.phoneNumber").value(newPhone))
                    .andExpect(jsonPath("$.birthdayDate").value("1985-06-15"))
                    .andExpect(jsonPath("$.accountExists").value(true))
                    .andExpect(jsonPath("$.username").value(newPhone))
                    .andExpect(jsonPath("$.accountEnabled").value(true))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"));

            UserAccount accountAfter = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertEquals(newPhone, accountAfter.getUsername());
            assertEquals(1L, accountAfter.getTokenVersion());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldNotIncrementTokenVersionWhenPhoneNumberIsUnchanged() throws Exception {
        Long personId = null;
        try {
            personId = savePersonWithRole("Same Phone Person", "ROLE_OPERATOR");
            Person before = personRepository.findById(personId).orElseThrow();
            String samePhone = before.getPhoneNumber();

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload("Same Phone Renamed", samePhone, "1985-06-15")))
                    .andExpect(status().isOk());

            UserAccount account = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertEquals(0L, account.getTokenVersion());
            assertEquals(samePhone, account.getUsername());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdatePersonWithoutAccountTouchingOnlyPerson() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("No Account Update Person");
            String newPhone = uniquePhoneNumber();

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload("No Account Renamed", newPhone, "1985-06-15")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountExists").value(false))
                    .andExpect(jsonPath("$.accountEnabled").doesNotExist())
                    .andExpect(jsonPath("$.username").doesNotExist())
                    .andExpect(jsonPath("$.roles").isEmpty());

            assertTrue(userAccountRepository.findByPersonId(personId).isEmpty());
            Person after = personRepository.findById(personId).orElseThrow();
            assertEquals(newPhone, after.getPhoneNumber());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectAndRollbackWhenPhoneNumberAlreadyBelongsToAnotherPerson() throws Exception {
        Long personId = null;
        Long otherId = null;
        try {
            personId = savePersonWithRole("Conflict Source Person", "ROLE_OPERATOR");
            otherId = savePerson("Conflict Target Person");
            String takenPhone = personRepository.findById(otherId).orElseThrow().getPhoneNumber();
            UserAccount accountBefore = userAccountRepository.findByPersonId(personId).orElseThrow();

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload("Should Not Persist", takenPhone, "1985-06-15")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PERSON_PHONE_NUMBER_CONFLICT"));

            Person personAfter = personRepository.findById(personId).orElseThrow();
            assertEquals("Conflict Source Person", stripSuffix(personAfter.getName(), "Conflict Source Person"));
            UserAccount accountAfter = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertEquals(accountBefore.getUsername(), accountAfter.getUsername());
            assertEquals(accountBefore.getTokenVersion(), accountAfter.getTokenVersion());
        } finally {
            cleanupPerson(personId);
            cleanupPerson(otherId);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldNotChangeMinistriesRoleActiveOrEnabledOnAdminUpdate() throws Exception {
        Long personId = null;
        try {
            personId = savePersonWithRole("Preserve State Person", "ROLE_ADMIN");
            addMinistry(personId, MinistryType.READER, true);
            UserAccount accountBefore = userAccountRepository.findByPersonId(personId).orElseThrow();
            assertTrue(accountBefore.isEnabled());

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updatePayload("Preserve State Renamed", uniquePhoneNumber(), "1985-06-15")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                    .andExpect(jsonPath("$.personActive").value(true))
                    .andExpect(jsonPath("$.accountEnabled").value(true));

            assertTrue(personRepository.findById(personId).orElseThrow().isActive());
            assertTrue(userAccountRepository.findByPersonId(personId).orElseThrow().isEnabled());
            assertEquals(1, countMinistries(personId, MinistryType.READER));
        } finally {
            cleanupPerson(personId);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password", "createAccess", "accessRole", "role", "roles", "ministries",
            "active", "personActive", "accountEnabled", "enabled", "username",
            "accountId", "tokenVersion", "passwordHash", "account", "unknownField"
    })
    @WithMockUser(roles = "ADMIN")
    void shouldRejectForbiddenOrUnknownFieldEvenWhenFalsyValue(String forbiddenField) throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Forbidden Field Person");
            String phone = personRepository.findById(personId).orElseThrow().getPhoneNumber();
            String payload = """
                    {
                      "name": "Should Not Persist",
                      "phoneNumber": "%s",
                      "birthdayDate": "1985-06-15",
                      "%s": null
                    }
                    """.formatted(phone, forbiddenField);

            mockMvc.perform(put("/pessoas/{id}", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PERSON_ADMIN_UPDATE_FIELDS_INVALID"));

            Person unchanged = personRepository.findById(personId).orElseThrow();
            assertFalse(unchanged.getName().startsWith("Should Not Persist"));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnForbiddenWhenOperatorUpdatesPerson() throws Exception {
        mockMvc.perform(put("/pessoas/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Name", "34900000000", "1985-06-15")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenUpdatingPersonWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/pessoas/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Name", "34900000000", "1985-06-15")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundForNonexistentPerson() throws Exception {
        mockMvc.perform(put("/pessoas/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Name", uniquePhoneNumber(), "1985-06-15")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private String stripSuffix(String actualName, String expectedPrefix) {
        return actualName.startsWith(expectedPrefix) ? expectedPrefix : actualName;
    }

    private String updatePayload(String name, String phoneNumber, String birthdayDate) {
        return """
                {
                  "name": "%s",
                  "phoneNumber": "%s",
                  "birthdayDate": "%s"
                }
                """.formatted(name, phoneNumber, birthdayDate);
    }

    private Long savePerson(String name) {
        Person person = person(name);
        return personRepository.saveAndFlush(person).getId();
    }

    private Long savePersonWithRole(String name, String roleAuthority) {
        Person person = person(name);
        Person saved = personRepository.saveAndFlush(person);
        Role role = roleRepository.findByAuthority(roleAuthority).orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = userAccountRepository.saveAndFlush(
                new UserAccount(saved, saved.getPhoneNumber(), "encoded-password", now, now));
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(account, role));
        return saved.getId();
    }

    private Person person(String name) {
        return new Person(name + " " + UUID.randomUUID(), uniquePhoneNumber(), BIRTHDAY);
    }

    private void addMinistry(Long personId, MinistryType ministryType, boolean active) {
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = personMinistry(person, ministryType, ministryRepository);
        ministry.setActive(active);
        personMinistryRepository.saveAndFlush(ministry);
    }

    private int countMinistries(Long personId, MinistryType ministryType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tb_person_ministry
                WHERE person_id = ?
                  AND ministry_type = ?
                  AND active = TRUE
                """,
                Integer.class,
                personId,
                ministryType.name()
        );
        return count == null ? 0 : count;
    }

    private Long ministryId(MinistryType ministryType) {
        return ministryRepository.findByNormalizedName(normalizedName(ministryType))
                .orElseThrow()
                .getId();
    }

    private String ministryName(MinistryType ministryType) {
        return ministryRepository.findByNormalizedName(normalizedName(ministryType))
                .orElseThrow()
                .getName();
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
        return "3494" + String.format("%07d", suffix);
    }
}
