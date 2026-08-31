package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.normalizedName;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta pelos endpoints reais GET/PUT /pessoas/{id}/ministries, que a gestao
 * administrativa de ministerios aplica o conjunto desejado atomicamente (adicao, reativacao,
 * desativacao e preservacao), bloqueia remocao com EventAssignment do mesmo tipo sem aplicar
 * nenhuma mudanca parcial, e preserva dados comuns, roles e credenciais da pessoa.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class PersonMinistryManagementIntegrationTest {

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
    private MinistryRepository ministryRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnEmptyMinistriesForPersonWithoutAnyMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("No Ministry Person");

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.ministries").isEmpty());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAddSingleMinistryToPersonWithoutAnyMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Add Single Ministry Person");

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false))
                    .andExpect(jsonPath("$.ministries.length()").value(1));

            assertActiveMinistry(personId, MinistryType.READER, true);
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAddSeveralMinistriesAtOnce() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Add Several Ministries Person");

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER, MinistryType.COMMENTATOR, MinistryType.PRIEST)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries.length()").value(3));

            assertActiveMinistry(personId, MinistryType.READER, true);
            assertActiveMinistry(personId, MinistryType.COMMENTATOR, true);
            assertActiveMinistry(personId, MinistryType.PRIEST, true);
            assertEquals(3, countMinistries(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldManageArbitraryMinistryMembershipByMinistryIdWithoutLegacyMapping() throws Exception {
        Long personId = null;
        Long ministryId = null;
        String ministryName = "Acólitos " + UUID.randomUUID();
        try {
            personId = savePerson("Arbitrary Ministry Person");
            Ministry ministry = ministryRepository.saveAndFlush(new Ministry(ministryName));
            ministryId = ministry.getId();
            assertFalse(hasLegacyMapping(ministryId));

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayloadWithIds(ministryId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(personId))
                    .andExpect(jsonPath("$.ministries.length()").value(1))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            PersonMinistry created = findPersonMinistry(personId, ministryId).orElseThrow();
            assertTrue(created.getActive());
            assertFalse(created.getCoordinator());

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries.length()").value(1))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName));

            mockMvc.perform(get("/pessoas").param("ministryId", ministryId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(personId)).isNotEmpty());

            mockMvc.perform(put(coordinatorPath(personId, ministryId)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(true));

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayloadWithIds()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries").isEmpty());

            PersonMinistry deactivated = findPersonMinistry(personId, ministryId).orElseThrow();
            assertFalse(deactivated.getActive());
            assertFalse(deactivated.getCoordinator());

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayloadWithIds(ministryId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries.length()").value(1))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            PersonMinistry reactivated = findPersonMinistry(personId, ministryId).orElseThrow();
            assertTrue(reactivated.getActive());
            assertFalse(reactivated.getCoordinator());
        } finally {
            cleanupPerson(personId);
            cleanupMinistry(ministryId);
        }
    }

    @Test
    void shouldReactivateInactiveMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Reactivate Ministry Person");
            PersonMinistry inactiveMinistry = addMinistry(personId, MinistryType.READER, false);
            Long ministryId = inactiveMinistry.getId();

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            PersonMinistry reactivated = findPersonMinistry(personId, MinistryType.READER)
                    .orElseThrow();
            assertTrue(reactivated.getActive());
            assertEquals(ministryId, reactivated.getId());
            assertEquals(1, countMinistries(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldRemoveOneMinistryPreservingOthers() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Remove One Ministry Person");
            addMinistry(personId, MinistryType.READER, true);
            addMinistry(personId, MinistryType.COMMENTATOR, true);

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries.length()").value(1))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            assertActiveMinistry(personId, MinistryType.READER, true);
            assertActiveMinistry(personId, MinistryType.COMMENTATOR, false);
            assertEquals(2, countMinistries(personId));
            assertTrue(personRepository.existsById(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldBeNoOpWhenDesiredSetMatchesCurrentActiveSet() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("No Op Ministry Person");
            PersonMinistry ministry = addMinistry(personId, MinistryType.READER, true);
            Long ministryId = ministry.getId();

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            PersonMinistry unchanged = findPersonMinistry(personId, MinistryType.READER)
                    .orElseThrow();
            assertEquals(ministryId, unchanged.getId());
            assertTrue(unchanged.getActive());
            assertEquals(1, countMinistries(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnNotFoundForNonexistentPerson() throws Exception {
        mockMvc.perform(get("/pessoas/{id}/ministries", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/pessoas/{id}/ministries", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ministriesPayload(MinistryType.READER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnBadRequestForInvalidMinistryValue() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Invalid Ministry Value Person");

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayloadWithIds(0L)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

            assertEquals(0, countMinistries(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnUnprocessableEntityForDuplicateMinistryInRequest() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Duplicate Ministry Person");

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER, MinistryType.READER)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));

            assertEquals(0, countMinistries(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldBlockRemovalAndApplyNoChangesWhenMinistryHasEventAssignmentOfSameType() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            personId = savePerson("Assigned Ministry Person");
            addMinistry(personId, MinistryType.READER, true);
            CelebrationEvent event = saveEvent("Assigned Ministry Mass");
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.READER);

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.COMMENTATOR)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DATABASE_RULE_VIOLATION"));

            assertActiveMinistry(personId, MinistryType.READER, true);
            assertEquals(0, countMinistries(personId, MinistryType.COMMENTATOR));
            assertEquals(1, countMinistries(personId));
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAllowRemovalWhenEventAssignmentIsOfADifferentType() throws Exception {
        Long personId = null;
        Long eventId = null;
        try {
            personId = savePerson("Cross Type Assignment Person");
            addMinistry(personId, MinistryType.READER, true);
            CelebrationEvent event = saveEvent("Cross Type Assignment Mass");
            eventId = event.getId();
            saveAssignment(event, personId, EventAssignmentType.COMMENTATOR);

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries").isEmpty());

            assertActiveMinistry(personId, MinistryType.READER, false);
        } finally {
            cleanupEvent(eventId);
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldPreserveRoleAndPhoneNumberAfterMinistryUpdate() throws Exception {
        Long personId = null;
        try {
            personId = savePersonWithRole("Preserve Credentials Person", "ROLE_OPERATOR");
            Person before = personRepository.findById(personId).orElseThrow();
            String originalPhoneNumber = before.getPhoneNumber();

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER, MinistryType.PRIEST)))
                    .andExpect(status().isOk());

            Person after = personRepository.findById(personId).orElseThrow();
            assertEquals(originalPhoneNumber, after.getPhoneNumber());
            assertEquals(Set.of("ROLE_OPERATOR"), roleAuthoritiesOfPerson(personId));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldNotExposePersonalDataInMinistriesResponse() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Contract Ministry Person");

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.roles").doesNotExist())
                    .andExpect(jsonPath("$.personType").doesNotExist());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldGrantAndRevokeCoordinatorViaHttpEndpoints() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Grant Revoke Person");
            addMinistry(personId, MinistryType.READER, true);

            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(true))
                    .andExpect(jsonPath("$.ministries.length()").value(1));

            // idempotente
            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(delete(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));

            // idempotente
            mockMvc.perform(delete(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnConflictWhenGrantingCoordinatorOnInactiveMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Inactive Ministry Person");
            addMinistry(personId, MinistryType.READER, false);

            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("MINISTRY_COORDINATION_REQUIRES_ACTIVE_MINISTRY"));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnConflictWhenGrantingCoordinatorOnMissingMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Missing Ministry Person");

            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("MINISTRY_COORDINATION_REQUIRES_ACTIVE_MINISTRY"));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnNotFoundWhenGrantingCoordinatorForNonexistentPerson() throws Exception {
        mockMvc.perform(put(coordinatorPath(987654321L, MinistryType.READER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenGrantingCoordinatorForNonexistentMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Missing Ministry Id Person");

            mockMvc.perform(put(coordinatorPath(personId, 987654321L)))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldReturnBadRequestWhenMinistryIdIsNonNumeric() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Invalid Ministry Person");

            mockMvc.perform(put("/pessoas/{id}/ministries/{ministryId}/coordinator", personId, "BISHOP"))
                    .andExpect(status().isBadRequest());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldClearCoordinatedMinistriesFromResponseWhenSyncDeactivatesTheMinistry() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Sync Clears Person");
            addMinistry(personId, MinistryType.READER, true);
            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries").isEmpty())
                    .andExpect(jsonPath("$.ministries").isEmpty());

            PersonMinistry deactivated = findPersonMinistry(personId, MinistryType.READER)
                    .orElseThrow();
            assertFalse(deactivated.getActive());
            assertFalse(deactivated.getCoordinator());
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldNotRestoreCoordinatedMinistryAfterSyncReactivatesIt() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Sync Reactivate Person");
            addMinistry(personId, MinistryType.READER, true);
            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload()))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/pessoas/{id}/ministries", personId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ministriesPayload(MinistryType.READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].name").value(ministryName(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(false));
        } finally {
            cleanupPerson(personId);
        }
    }

    @Test
    void shouldAllowMultipleCoordinatorsForTheSameMinistrySimultaneously() throws Exception {
        Long personA = null;
        Long personB = null;
        try {
            personA = savePerson("Coordinator Multi A");
            personB = savePerson("Coordinator Multi B");
            addMinistry(personA, MinistryType.READER, true);
            addMinistry(personB, MinistryType.READER, true);

            mockMvc.perform(put(coordinatorPath(personA, MinistryType.READER)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put(coordinatorPath(personB, MinistryType.READER)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/pessoas/{id}/ministries", personA))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(true));
            mockMvc.perform(get("/pessoas/{id}/ministries", personB))
                    .andExpect(jsonPath("$.ministries[0].id").value(ministryId(MinistryType.READER)))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(true));
        } finally {
            cleanupPerson(personA);
            cleanupPerson(personB);
        }
    }

    @Test
    void shouldAllowOnePersonToCoordinateMultipleMinistries() throws Exception {
        Long personId = null;
        try {
            personId = savePerson("Coordinator Multi Ministry Person");
            addMinistry(personId, MinistryType.READER, true);
            addMinistry(personId, MinistryType.COMMENTATOR, true);

            mockMvc.perform(put(coordinatorPath(personId, MinistryType.READER)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put(coordinatorPath(personId, MinistryType.COMMENTATOR)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/pessoas/{id}/ministries", personId))
                    .andExpect(jsonPath("$.ministries.length()").value(2))
                    .andExpect(jsonPath("$.ministries[0].coordinator").value(true))
                    .andExpect(jsonPath("$.ministries[1].coordinator").value(true));
        } finally {
            cleanupPerson(personId);
        }
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

    private Set<String> roleAuthoritiesOfPerson(Long personId) {
        return userAccountRoleRepository.findRoleAuthoritiesByPersonIdsGroupedByPerson(List.of(personId))
                .getOrDefault(personId, List.of())
                .stream()
                .collect(java.util.stream.Collectors.toSet());
    }

    private Person person(String name) {
        return new Person(name + " " + UUID.randomUUID(), uniquePhoneNumber(), BIRTHDAY);
    }

    private PersonMinistry addMinistry(Long personId, MinistryType ministryType, boolean active) {
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = personMinistry(person, ministryType, ministryRepository);
        ministry.setActive(active);
        return personMinistryRepository.saveAndFlush(ministry);
    }

    private CelebrationEvent saveEvent(String name) {
        LocalDateTime startAt = LocalDateTime.of(LocalDate.now().plusDays(30), LocalTime.of(19, 0));
        CelebrationEvent event = new CelebrationEvent(
                null,
                name + " " + UUID.randomUUID(),
                startAt,
                startAt.plusHours(1),
                true
        );
        return celebrationEventRepository.saveAndFlush(event);
    }

    private void saveAssignment(CelebrationEvent event, Long personId, EventAssignmentType assignmentType) {
        Person person = personRepository.findById(personId).orElseThrow();
        eventAssignmentRepository.saveAndFlush(new EventAssignment(event, person, assignmentType));
    }

    private String ministriesPayload(MinistryType... ministries) throws Exception {
        List<Long> ministryIds = java.util.Arrays.stream(ministries)
                .map(this::ministryId)
                .toList();
        return objectMapper.writeValueAsString(new MinistriesPayload(ministryIds));
    }

    private String ministriesPayloadWithIds(Long... ministryIds) throws Exception {
        return objectMapper.writeValueAsString(new MinistriesPayload(List.of(ministryIds)));
    }

    private String coordinatorPath(Long personId, MinistryType ministryType) {
        return coordinatorPath(personId, ministryId(ministryType));
    }

    private String coordinatorPath(Long personId, Long ministryId) {
        return "/pessoas/" + personId + "/ministries/" + ministryId + "/coordinator";
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

    private java.util.Optional<PersonMinistry> findPersonMinistry(Long personId, MinistryType ministryType) {
        return personMinistryRepository.findByPersonIdAndMinistryId(personId, ministryId(ministryType));
    }

    private java.util.Optional<PersonMinistry> findPersonMinistry(Long personId, Long ministryId) {
        return personMinistryRepository.findByPersonIdAndMinistryId(personId, ministryId);
    }

    private void assertActiveMinistry(Long personId, MinistryType ministryType, boolean expectedActive) {
        boolean active = findPersonMinistry(personId, ministryType)
                .map(PersonMinistry::getActive)
                .orElse(false);
        assertEquals(expectedActive, active);
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
                  AND ministry_id = ?
                  AND active = TRUE
                """,
                Integer.class,
                personId,
                ministryId(ministryType)
        );
        return count == null ? 0 : count;
    }

    private boolean hasLegacyMapping(Long ministryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ministry_legacy_type_mapping WHERE ministry_id = ?",
                Integer.class,
                ministryId
        );
        return count != null && count > 0;
    }

    private void cleanupEvent(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_event_location WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
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

    private void cleanupMinistry(Long ministryId) {
        if (ministryId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE ministry_id = ?", ministryId);
        jdbcTemplate.update("DELETE FROM tb_ministry WHERE id = ?", ministryId);
    }

    private String uniquePhoneNumber() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3493" + String.format("%07d", suffix);
    }

    private record MinistriesPayload(List<Long> ministryIds) {
    }
}
