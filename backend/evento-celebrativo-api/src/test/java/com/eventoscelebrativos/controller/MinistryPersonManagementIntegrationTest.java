package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.normalizedName;
import static com.eventoscelebrativos.support.LegacyMinistryTestFactory.personMinistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, com Spring Security e banco H2 reais (MinistryAuthorizationServiceImpl não mockado), a
 * feature/ministry-scoped-person-management: {@code /ministerios/{ministryId}/pessoas}. Cobre a
 * matriz de autorização, isolamento cross-ministry, ausência de UserAccount na criação escopada,
 * fail-closed de mass assignment, idempotência de vínculo, invariantes PASTOR/PRIEST e
 * ACTIVE/CANCELLED assignment na remoção, revogação imediata de coordenação e auto-remoção. Cada
 * teste cria suas próprias pessoas e recursos isolados; responsabilidades paroquiais/coordenação
 * criadas por este teste são desfeitas no @AfterEach por serem estado global compartilhado
 * (@SpringBootTest cacheado entre classes).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MinistryPersonManagementIntegrationTest {

    private static final AtomicLong PHONE_SEQ = new AtomicLong(37_910_000_000L);

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private MinistryRepository ministryRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdStaffPersonIds = new ArrayList<>();

    @AfterEach
    void deactivateStaffResponsibilitiesCreatedByThisTest() {
        for (Long personId : createdStaffPersonIds) {
            for (ParishResponsibilityType type : ParishResponsibilityType.values()) {
                parishStaffAssignmentRepository.findByPersonIdAndResponsibility(personId, type)
                        .filter(ParishStaffAssignment::isActive)
                        .ifPresent(assignment -> {
                            assignment.deactivate();
                            parishStaffAssignmentRepository.saveAndFlush(assignment);
                        });
            }
        }
        createdStaffPersonIds.clear();
    }

    // --- Matriz de autorizacao ---

    @Test
    void adminCanManageAnyMinistryTypeScope() throws Exception {
        long targetId = createReader("Admin Scope Target");
        RequestPostProcessor admin = asUser(2_000_001L, "ROLE_ADMIN");

        assertScopedEndpoints(admin, true, targetId, 403 /* unreachable when allowed=true */);
    }

    @Test
    void matchingCoordinatorCanManageOwnMinistryScope() throws Exception {
        long coordinatorId = createReaderCoordinator("Matching Coordinator");
        long targetId = createReader("Matching Coordinator Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        assertScopedEndpoints(coordinator, true, targetId, 403);
    }

    @Test
    void coordinatorOfDifferentMinistryIsForbidden() throws Exception {
        long coordinatorId = createCommentatorCoordinator("Wrong Ministry Coordinator");
        long targetId = createReader("Wrong Ministry Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        assertScopedEndpoints(coordinator, false, targetId, 403);
    }

    @Test
    void commonOperatorWithoutCoordinationIsForbidden() throws Exception {
        long operatorId = createPerson("Common Operator No Coordination");
        long targetId = createReader("Common Operator Target");
        RequestPostProcessor operator = asUser(operatorId, "ROLE_OPERATOR");

        assertScopedEndpoints(operator, false, targetId, 403);
    }

    @Test
    void activeParishSecretaryWithoutCoordinationIsForbidden() throws Exception {
        long secretaryId = createPerson("Secretary No Coordination");
        Person secretaryPerson = personRepository.findById(secretaryId).orElseThrow();
        ParishStaffAssignment assignment = new ParishStaffAssignment(secretaryPerson, ParishResponsibilityType.PARISH_SECRETARY);
        parishStaffAssignmentRepository.saveAndFlush(assignment);
        createdStaffPersonIds.add(secretaryId);
        long targetId = createReader("Secretary Scope Target");
        RequestPostProcessor secretary = asUser(secretaryId, "ROLE_OPERATOR");

        assertScopedEndpoints(secretary, false, targetId, 403);
    }

    @Test
    void activePastorWithoutCoordinationIsForbidden() throws Exception {
        long pastorId = createPerson("Pastor No Coordination");
        Person pastorPerson = personRepository.findById(pastorId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(pastorPerson, MinistryType.PRIEST, ministryRepository));
        ParishStaffAssignment assignment = new ParishStaffAssignment(pastorPerson, ParishResponsibilityType.PASTOR);
        parishStaffAssignmentRepository.saveAndFlush(assignment);
        createdStaffPersonIds.add(pastorId);
        long targetId = createReader("Pastor Scope Target");
        RequestPostProcessor pastor = asUser(pastorId, "ROLE_OPERATOR");

        assertScopedEndpoints(pastor, false, targetId, 403);
    }

    @Test
    void inactiveCoordinatorLinkIsForbidden() throws Exception {
        long coordinatorId = createPerson("Inactive Coordinator Link");
        Person coordinatorPerson = personRepository.findById(coordinatorId).orElseThrow();
        PersonMinistry ministry = personMinistry(coordinatorPerson, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        ministry.deactivate();
        personMinistryRepository.saveAndFlush(ministry);
        long targetId = createReader("Inactive Coordinator Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        assertScopedEndpoints(coordinator, false, targetId, 403);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        long targetId = createReader("Anonymous Scope Target");

        mockMvc.perform(get(ministryPeoplePath(MinistryType.READER))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, targetId))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ministryPeoplePath(MinistryType.READER)).contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Anon Create")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Anon Update")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, targetId))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, targetId))).andExpect(status().isUnauthorized());
    }

    private void assertScopedEndpoints(RequestPostProcessor actor, boolean allowed, long readerTargetId, int deniedStatus) throws Exception {
        int okList = allowed ? 200 : deniedStatus;
        mockMvc.perform(get(ministryPeoplePath(MinistryType.READER)).with(actor)).andExpect(status().is(okList));

        int okDetail = allowed ? 200 : deniedStatus;
        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, readerTargetId)).with(actor)).andExpect(status().is(okDetail));

        int okCreate = allowed ? 201 : deniedStatus;
        mockMvc.perform(post(ministryPeoplePath(MinistryType.READER)).with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Matrix Create " + PHONE_SEQ.incrementAndGet())))
                .andExpect(status().is(okCreate));

        int okUpdate = allowed ? 200 : deniedStatus;
        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, readerTargetId)).with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("Matrix Update")))
                .andExpect(status().is(okUpdate));

        int okVinculo = allowed ? 200 : deniedStatus;
        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, readerTargetId)).with(actor))
                .andExpect(status().is(okVinculo));

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, readerTargetId)).with(actor))
                .andExpect(status().is(allowed ? 204 : deniedStatus));
    }

    // --- Isolamento cross-ministry ---

    @Test
    void readerCoordinatorCannotReachCommentatorScope() throws Exception {
        long coordinatorId = createReaderCoordinator("Cross Scope Coordinator");
        long commentatorOnlyId = createCommentator("Commentator Only Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(get(ministryPeoplePath(MinistryType.COMMENTATOR)).with(coordinator)).andExpect(status().isForbidden());
        mockMvc.perform(get(ministryPersonPath(MinistryType.COMMENTATOR, commentatorOnlyId)).with(coordinator))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ministryPeoplePath(MinistryType.COMMENTATOR)).with(coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Cross Create")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ministryPersonPath(MinistryType.COMMENTATOR, commentatorOnlyId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(updatePayload("Cross Update")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ministryVinculoPath(MinistryType.COMMENTATOR, commentatorOnlyId)).with(coordinator))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ministryVinculoPath(MinistryType.COMMENTATOR, commentatorOnlyId)).with(coordinator))
                .andExpect(status().isForbidden());
    }

    @Test
    void readerCoordinatorCannotRemovePriestOrCommentatorLinkOfMultiMinistryPerson() throws Exception {
        long coordinatorId = createReaderCoordinator("Cross Scope Multi Coordinator");
        long multiMinistryId = createReader("Cross Scope Multi Target");
        Person multiMinistryPerson = personRepository.findById(multiMinistryId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(multiMinistryPerson, MinistryType.COMMENTATOR, ministryRepository));
        long priestId = createPerson("Cross Scope Priest Target");
        Person priest = personRepository.findById(priestId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(priest, MinistryType.PRIEST, ministryRepository));
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.COMMENTATOR, multiMinistryId)).with(coordinator))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ministryVinculoPath(MinistryType.PRIEST, priestId)).with(coordinator))
                .andExpect(status().isForbidden());

        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(multiMinistryId, MinistryType.READER).orElseThrow().getActive());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(multiMinistryId, MinistryType.COMMENTATOR).orElseThrow().getActive());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(priestId, MinistryType.PRIEST).orElseThrow().getActive());
    }

    @Test
    void personOutsideRequestedMinistryScopeReturns404NotLeakedGlobalRecord() throws Exception {
        long coordinatorId = createReaderCoordinator("Scope Leak Coordinator");
        long commentatorOnlyId = createCommentator("Scope Leak Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, commentatorOnlyId)).with(coordinator))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, commentatorOnlyId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(updatePayload("Leak Update")))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactivePersonWithActiveMinistryIsHiddenFromScopedReadAndMutation() throws Exception {
        long coordinatorId = createReaderCoordinator("Inactive Person Hidden Coordinator");
        long inactiveTargetId = createReader("Inactive Person Hidden Target");
        Person inactiveTarget = personRepository.findById(inactiveTargetId).orElseThrow();
        inactiveTarget.deactivate();
        personRepository.saveAndFlush(inactiveTarget);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(get(ministryPeoplePath(MinistryType.READER)).with(coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(inactiveTargetId)).isEmpty());
        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, inactiveTargetId)).with(coordinator))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, inactiveTargetId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(updatePayload("Inactive Update")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, inactiveTargetId)).with(coordinator))
                .andExpect(status().isNotFound());

        Person unchangedPerson = personRepository.findById(inactiveTargetId).orElseThrow();
        assertFalse(unchangedPerson.isActive());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(inactiveTargetId, MinistryType.READER)
                .orElseThrow().getActive());
    }

    // --- Criacao ---

    @Test
    void createCreatesPersonAndActiveMinistryWithoutUserAccount() throws Exception {
        long coordinatorId = createReaderCoordinator("Create Flow Coordinator");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");
        String phone = String.valueOf(PHONE_SEQ.incrementAndGet());

        String response = mockMvc.perform(post(ministryPeoplePath(MinistryType.READER)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Criado Pelo Coordenador", "phoneNumber": "%s", "birthdayDate": "1995-05-05"}
                                """.formatted(phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Criado Pelo Coordenador"))
                .andExpect(jsonPath("$.phoneNumber").value(phone))
                .andReturn().getResponse().getContentAsString();
        long createdId = extractId(response);

        Person created = personRepository.findById(createdId).orElseThrow();
        assertTrue(created.isActive());
        assertTrue(userAccountRepository.findByPersonId(createdId).isEmpty());
        PersonMinistry ministry = personMinistryRepository.findByPersonIdAndMinistryType(createdId, MinistryType.READER).orElseThrow();
        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
    }

    @Test
    void createRejectsAccountAndLifecycleFieldsEvenWhenFalseOrNull() throws Exception {
        long coordinatorId = createReaderCoordinator("Create MassAssignment Coordinator");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        String[][] forbiddenFields = {
                {"password", "\"senha123\""},
                {"createAccess", "false"},
                {"accessRole", "null"},
                {"roles", "[]"},
                {"role", "\"\""},
                {"ministries", "[]"},
                {"active", "false"},
                {"personActive", "null"},
                {"accountEnabled", "false"},
                {"enabled", "false"},
                {"username", "\"\""},
                {"coordinator", "false"}
        };

        for (String[] field : forbiddenFields) {
            String phone = String.valueOf(PHONE_SEQ.incrementAndGet());
            String payload = """
                    {"name": "X", "phoneNumber": "%s", "birthdayDate": "1995-05-05", "%s": %s}
                    """.formatted(phone, field[0], field[1]);
            mockMvc.perform(post(ministryPeoplePath(MinistryType.READER)).with(coordinator)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
            assertTrue(personRepository.findByPhoneNumber(phone).isEmpty(), field[0]);
        }
    }

    // --- Add/reactivate vinculo ---

    @Test
    void addVinculoCreatesActiveNonCoordinatorMinistryWhenAbsent() throws Exception {
        long coordinatorId = createReaderCoordinator("Add Vinculo Coordinator");
        long existingId = createCommentator("Existing Person No Reader Yet");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, existingId)).with(coordinator))
                .andExpect(status().isOk());

        PersonMinistry ministry = personMinistryRepository.findByPersonIdAndMinistryType(existingId, MinistryType.READER).orElseThrow();
        assertTrue(ministry.getActive());
        assertFalse(ministry.getCoordinator());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(existingId, MinistryType.COMMENTATOR).orElseThrow().getActive());
    }

    @Test
    void addVinculoReactivatesWithoutRestoringCoordination() throws Exception {
        long coordinatorId = createReaderCoordinator("Reactivate Vinculo Coordinator");
        long targetId = createReader("Reactivate Vinculo Target");
        PersonMinistry ministry = personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow();
        ministry.grantCoordination();
        ministry.deactivate();
        personMinistryRepository.saveAndFlush(ministry);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, targetId)).with(coordinator))
                .andExpect(status().isOk());

        PersonMinistry reactivated = personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow();
        assertTrue(reactivated.getActive());
        assertFalse(reactivated.getCoordinator());
    }

    @Test
    void addVinculoIsIdempotentAndPreservesExistingCoordination() throws Exception {
        long coordinatorId = createReaderCoordinator("Idempotent Vinculo Coordinator");
        long targetId = createReader("Idempotent Vinculo Target");
        PersonMinistry ministry = personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow();
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, targetId)).with(coordinator))
                .andExpect(status().isOk());

        PersonMinistry unchanged = personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow();
        assertTrue(unchanged.getActive());
        assertTrue(unchanged.getCoordinator());
    }

    @Test
    void addVinculoBlockedWhenPersonIsInactive() throws Exception {
        long coordinatorId = createReaderCoordinator("Inactive Person Vinculo Coordinator");
        long targetId = createPerson("Inactive Target Vinculo");
        Person target = personRepository.findById(targetId).orElseThrow();
        target.deactivate();
        personRepository.saveAndFlush(target);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryVinculoPath(MinistryType.READER, targetId)).with(coordinator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MINISTRY_PERSON_INACTIVE"));
    }

    // --- Update ---

    @Test
    void updateChangesNameAndBirthdayButRejectsPhoneAndAccountFields() throws Exception {
        long coordinatorId = createReaderCoordinator("Update Coordinator");
        long targetId = createReader("Update Target Original");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome Atualizado Pelo Coordenador", "birthdayDate": "1988-03-03"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Atualizado Pelo Coordenador"));

        Person updated = personRepository.findById(targetId).orElseThrow();
        assertEquals("Nome Atualizado Pelo Coordenador", updated.getName());
        assertEquals(LocalDate.of(1988, 3, 3), updated.getBirthdayDate());

        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "birthdayDate": "1988-03-03", "phoneNumber": "34988887777"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "birthdayDate": "1988-03-03", "active": false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRejectsAllAccountLifecycleAndMinistryFieldsEvenWhenFalseNullEmptyOrBlank() throws Exception {
        long coordinatorId = createReaderCoordinator("Update MassAssignment Coordinator");
        long targetId = createReader("Update MassAssignment Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        String[][] forbiddenFields = {
                {"phoneNumber", "\"34988887777\""},
                {"password", "\"senha123\""},
                {"createAccess", "false"},
                {"accessRole", "null"},
                {"roles", "[]"},
                {"role", "\"\""},
                {"ministries", "[]"},
                {"active", "false"},
                {"personActive", "null"},
                {"accountEnabled", "false"},
                {"enabled", "false"},
                {"username", "\"\""},
                {"coordinator", "false"},
                {"tokenVersion", "0"},
                {"parishResponsibilities", "[]"}
        };

        for (String[] field : forbiddenFields) {
            String payload = """
                    {"name": "X", "birthdayDate": "1988-03-03", "%s": %s}
                    """.formatted(field[0], field[1]);
            mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }

        Person unchanged = personRepository.findById(targetId).orElseThrow();
        assertEquals("Update MassAssignment Target", unchanged.getName());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow().getActive());
    }

    @Test
    void updatePreservesUserAccountWhenPersonHasOne() throws Exception {
        long coordinatorId = createReaderCoordinator("Update Account Preserved Coordinator");
        long targetId = createReader("Update Account Preserved Target");
        Person target = personRepository.findById(targetId).orElseThrow();
        String username = target.getPhoneNumber();
        LocalDateTime now = LocalDateTime.now();
        UserAccount account = new UserAccount(target, username, passwordEncoder.encode("senha123"), now, now);
        userAccountRepository.saveAndFlush(account);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(put(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome Com Conta Atualizado", "birthdayDate": "1980-01-01"}
                                """))
                .andExpect(status().isOk());

        UserAccount reloaded = userAccountRepository.findByPersonId(targetId).orElseThrow();
        assertEquals(username, reloaded.getUsername());
        assertTrue(reloaded.isEnabled());
        assertEquals(0L, reloaded.getTokenVersion());
    }

    // --- Remove vinculo ---

    @Test
    void removeVinculoDeactivatesOnlyThatMinistryPreservingPersonAndOtherMinistries() throws Exception {
        long coordinatorId = createReaderCoordinator("Remove Vinculo Coordinator");
        long targetId = createReader("Remove Vinculo Target");
        Person target = personRepository.findById(targetId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(target, MinistryType.COMMENTATOR, ministryRepository));
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, targetId)).with(coordinator))
                .andExpect(status().isNoContent());

        PersonMinistry readerMinistry = personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER).orElseThrow();
        assertFalse(readerMinistry.getActive());
        assertFalse(readerMinistry.getCoordinator());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.COMMENTATOR).orElseThrow().getActive());
        assertTrue(personRepository.findById(targetId).orElseThrow().isActive());
    }

    @Test
    void removeVinculoBlockedByActiveFutureAssignmentButNotByCancelledOnlyAssignment() throws Exception {
        long coordinatorId = createReaderCoordinator("Assignment Block Coordinator");
        long activeBlockedTargetId = createReader("Active Assignment Target");
        long cancelledOnlyTargetId = createReader("Cancelled Assignment Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        Person activeBlockedPerson = personRepository.findById(activeBlockedTargetId).orElseThrow();
        CelebrationEvent futureActiveEvent = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Evento Futuro Ativo Ministry Scope", LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(20).plusHours(1), true));
        eventAssignmentRepository.saveAndFlush(new EventAssignment(futureActiveEvent, activeBlockedPerson, EventAssignmentType.READER));

        Person cancelledOnlyPerson = personRepository.findById(cancelledOnlyTargetId).orElseThrow();
        CelebrationEvent futureCancelledEvent = celebrationEventRepository.saveAndFlush(new CelebrationEvent(
                null, "Evento Futuro Cancelado Ministry Scope", LocalDateTime.now().plusDays(21),
                LocalDateTime.now().plusDays(21).plusHours(1), true));
        futureCancelledEvent.cancel();
        celebrationEventRepository.saveAndFlush(futureCancelledEvent);
        eventAssignmentRepository.saveAndFlush(new EventAssignment(futureCancelledEvent, cancelledOnlyPerson, EventAssignmentType.READER));

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, activeBlockedTargetId)).with(coordinator))
                .andExpect(status().isConflict());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(activeBlockedTargetId, MinistryType.READER).orElseThrow().getActive());

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, cancelledOnlyTargetId)).with(coordinator))
                .andExpect(status().isNoContent());
        assertFalse(personMinistryRepository.findByPersonIdAndMinistryType(cancelledOnlyTargetId, MinistryType.READER).orElseThrow().getActive());
    }

    @Test
    void removePriestBlockedWhenPersonIsActivePastor() throws Exception {
        long coordinatorId = createPriestCoordinator("Pastor Invariant Coordinator");
        long pastorId = createPerson("Pastor Invariant Target");
        Person pastorPerson = personRepository.findById(pastorId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(pastorPerson, MinistryType.PRIEST, ministryRepository));
        ParishStaffAssignment pastorAssignment = new ParishStaffAssignment(pastorPerson, ParishResponsibilityType.PASTOR);
        parishStaffAssignmentRepository.saveAndFlush(pastorAssignment);
        createdStaffPersonIds.add(pastorId);
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.PRIEST, pastorId)).with(coordinator))
                .andExpect(status().isConflict());
        assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(pastorId, MinistryType.PRIEST).orElseThrow().getActive());
    }

    // --- Revogacao imediata e auto-remocao ---

    @Test
    void revokingCoordinatorImmediatelyBlocksScopedOperationWithoutReissuingPrincipal() throws Exception {
        long coordinatorId = createReaderCoordinator("Immediate Revoke Coordinator");
        long targetId = createReader("Immediate Revoke Target");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)).andExpect(status().isOk());

        PersonMinistry ministry = personMinistryRepository.findByPersonIdAndMinistryType(coordinatorId, MinistryType.READER).orElseThrow();
        ministry.revokeCoordination();
        personMinistryRepository.saveAndFlush(ministry);

        mockMvc.perform(get(ministryPersonPath(MinistryType.READER, targetId)).with(coordinator)).andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanRemoveOwnMinistryVinculoAndImmediatelyLosesAuthority() throws Exception {
        long coordinatorId = createReaderCoordinator("Self Removal Coordinator");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(delete(ministryVinculoPath(MinistryType.READER, coordinatorId)).with(coordinator))
                .andExpect(status().isNoContent());

        PersonMinistry selfMinistry = personMinistryRepository.findByPersonIdAndMinistryType(coordinatorId, MinistryType.READER).orElseThrow();
        assertFalse(selfMinistry.getActive());
        assertFalse(selfMinistry.getCoordinator());

        mockMvc.perform(get(ministryPeoplePath(MinistryType.READER)).with(coordinator)).andExpect(status().isForbidden());
    }

    // --- Regressao: endpoints administrativos globais continuam ROLE_ADMIN-only ---

    @Test
    void coordinatorRemainsForbiddenOnGlobalAdminPersonEndpoints() throws Exception {
        long coordinatorId = createReaderCoordinator("Global Admin Regression Coordinator");
        RequestPostProcessor coordinator = asUser(coordinatorId, "ROLE_OPERATOR");

        mockMvc.perform(get("/pessoas").with(coordinator)).andExpect(status().isForbidden());
        mockMvc.perform(get("/pessoas/" + coordinatorId).with(coordinator)).andExpect(status().isForbidden());
        mockMvc.perform(put("/pessoas/" + coordinatorId).with(coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X", "phoneNumber": "34988880000", "birthdayDate": "1990-01-01"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/pessoas/" + coordinatorId + "/ministries").with(coordinator)).andExpect(status().isForbidden());
        mockMvc.perform(put("/pessoas/" + coordinatorId + "/ministries").with(coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ministries": ["READER"]}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ministryCoordinatorPath(coordinatorId, MinistryType.READER)).with(coordinator))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ministryCoordinatorPath(coordinatorId, MinistryType.READER)).with(coordinator))
                .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    private RequestPostProcessor asUser(long personId, String... authorities) {
        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        String username = "3" + String.format("%09d", personId % 1_000_000_000L);
        AuthenticatedUser user = new AuthenticatedUser(1L, personId, username, 0L, granted);
        return authentication(new UsernamePasswordAuthenticationToken(user, null, granted));
    }

    private long createPerson(String name) {
        Person person = new Person(name, String.valueOf(PHONE_SEQ.incrementAndGet()), LocalDate.of(1990, 1, 10));
        return personRepository.saveAndFlush(person).getId();
    }

    private long createReader(String name) {
        long personId = createPerson(name);
        Person person = personRepository.findById(personId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.READER, ministryRepository));
        return personId;
    }

    private long createCommentator(String name) {
        long personId = createPerson(name);
        Person person = personRepository.findById(personId).orElseThrow();
        personMinistryRepository.saveAndFlush(personMinistry(person, MinistryType.COMMENTATOR, ministryRepository));
        return personId;
    }

    private long createReaderCoordinator(String name) {
        long personId = createPerson(name);
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = personMinistry(person, MinistryType.READER, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        return personId;
    }

    private long createCommentatorCoordinator(String name) {
        long personId = createPerson(name);
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = personMinistry(person, MinistryType.COMMENTATOR, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        return personId;
    }

    private long createPriestCoordinator(String name) {
        long personId = createPerson(name);
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = personMinistry(person, MinistryType.PRIEST, ministryRepository);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        return personId;
    }

    private String createPayload(String name) {
        return """
                {"name": "%s", "phoneNumber": "%d", "birthdayDate": "1992-06-15"}
                """.formatted(name, PHONE_SEQ.incrementAndGet());
    }

    private String updatePayload(String name) {
        return """
                {"name": "%s", "birthdayDate": "1992-06-15"}
                """.formatted(name);
    }

    private long extractId(String jsonResponse) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
        if (!matcher.find()) {
            throw new IllegalStateException("Resposta sem campo id: " + jsonResponse);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String ministryPeoplePath(MinistryType ministryType) {
        return "/ministerios/" + ministryId(ministryType) + "/pessoas";
    }

    private String ministryPersonPath(MinistryType ministryType, long personId) {
        return ministryPeoplePath(ministryType) + "/" + personId;
    }

    private String ministryVinculoPath(MinistryType ministryType, long personId) {
        return ministryPersonPath(ministryType, personId) + "/vinculo";
    }

    private String ministryCoordinatorPath(long personId, MinistryType ministryType) {
        return "/pessoas/" + personId + "/ministries/" + ministryId(ministryType) + "/coordinator";
    }

    private Long ministryId(MinistryType ministryType) {
        return ministryRepository.findByNormalizedName(normalizedName(ministryType)).orElseThrow().getId();
    }
}
