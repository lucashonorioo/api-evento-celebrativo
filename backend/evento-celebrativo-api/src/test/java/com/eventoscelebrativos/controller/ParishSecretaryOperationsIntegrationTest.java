package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParishResponsibilityType;
import com.eventoscelebrativos.model.ParishStaffAssignment;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.ParishStaffAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, com Spring Security e banco H2 reais (ParishAuthorizationServiceImpl não mockado), a matriz
 * de autorização da feature de secretaria paroquial sobre os cinco endpoints liberados nesta branch
 * (PUT /paroquia/contato, POST/PUT /locais, POST/PUT /eventos) e a regressão explícita dos endpoints
 * que continuam exclusivos de ROLE_ADMIN (POST /eventos/com-escala, PUT /eventos/{id}/escala,
 * DELETE /locais/{id}, DELETE /eventos/{id}). Cada teste cria suas próprias pessoas e recursos
 * isolados para não depender da ordem de execução em relação a outras classes que compartilham o
 * mesmo contexto/banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParishSecretaryOperationsIntegrationTest {

    private static final AtomicLong PHONE_SEQ = new AtomicLong(37_900_000_000L);
    private static final long ADMIN_PERSON_ID = 1_000_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ParishStaffAssignmentRepository parishStaffAssignmentRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    // Pessoas com ParishStaffAssignment/PersonMinistry criadas por este teste: o ParishProfile e as
    // demais tabelas de responsabilidade são estado global compartilhado por todo o contexto Spring
    // (@SpringBootTest cacheado entre classes), então cada teste desativa no @AfterEach o que criou,
    // para não inflar contagens (ex. GET /paroquia/equipe) nem quebrar invariantes (ex. PASTOR único
    // ativo) assumidos por outras classes de teste que compartilham o mesmo contexto/banco.
    private final List<Long> createdStaffPersonIds = new ArrayList<>();
    private final List<Long> createdCoordinatorMinistryPersonIds = new ArrayList<>();

    @AfterEach
    void deactivateStaffResponsibilitiesCreatedByThisTest() {
        for (Long personId : createdStaffPersonIds) {
            parishStaffAssignmentRepository.findByPersonIdAndResponsibility(personId, ParishResponsibilityType.PARISH_SECRETARY)
                    .filter(ParishStaffAssignment::isActive)
                    .ifPresent(assignment -> {
                        assignment.deactivate();
                        parishStaffAssignmentRepository.saveAndFlush(assignment);
                    });
            parishStaffAssignmentRepository.findByPersonIdAndResponsibility(personId, ParishResponsibilityType.PASTOR)
                    .filter(ParishStaffAssignment::isActive)
                    .ifPresent(assignment -> {
                        assignment.deactivate();
                        parishStaffAssignmentRepository.saveAndFlush(assignment);
                    });
        }
        createdStaffPersonIds.clear();

        for (Long personId : createdCoordinatorMinistryPersonIds) {
            for (PersonMinistry ministry : personMinistryRepository.findAllByPersonId(personId)) {
                if (Boolean.TRUE.equals(ministry.getCoordinator()) || Boolean.TRUE.equals(ministry.getActive())) {
                    ministry.deactivate();
                    personMinistryRepository.saveAndFlush(ministry);
                }
            }
        }
        createdCoordinatorMinistryPersonIds.clear();
    }

    // --- Matriz de autorização: ADMIN, secretaria ativa, operator comum, PASTOR-only,
    // coordinator-only, secretaria inativa e anonymous, sobre os 5 endpoints liberados ---

    @Test
    void adminCanPerformAllSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Admin Setup");
        long eventId = createEvent(admin, "Evento Admin Setup");

        assertSecretaryProtectedEndpoints(admin, true, locationId, eventId, 0);
    }

    @Test
    void activeSecretaryCanPerformAllSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Secretaria Setup");
        long eventId = createEvent(admin, "Evento Secretaria Setup");

        long secretaryPersonId = createSecretary("Secretaria Ativa", true);
        RequestPostProcessor secretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        assertSecretaryProtectedEndpoints(secretary, true, locationId, eventId, 0);
    }

    @Test
    void plainOperatorIsForbiddenFromSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Operador Setup");
        long eventId = createEvent(admin, "Evento Operador Setup");

        long operatorPersonId = createPerson("Operador Comum");
        RequestPostProcessor operator = asUser(operatorPersonId, "ROLE_OPERATOR");

        assertSecretaryProtectedEndpoints(operator, false, locationId, eventId, 403);
    }

    @Test
    void pastorOnlyIsForbiddenFromSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Pastor Setup");
        long eventId = createEvent(admin, "Evento Pastor Setup");

        long pastorPersonId = createPastorOnly("Pastor Sem Secretaria");
        RequestPostProcessor pastor = asUser(pastorPersonId, "ROLE_OPERATOR");

        assertSecretaryProtectedEndpoints(pastor, false, locationId, eventId, 403);
    }

    @Test
    void coordinatorOnlyIsForbiddenFromSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Coordenador Setup");
        long eventId = createEvent(admin, "Evento Coordenador Setup");

        long coordinatorPersonId = createCoordinatorOnly("Coordenador Sem Secretaria");
        RequestPostProcessor coordinator = asUser(coordinatorPersonId, "ROLE_OPERATOR");

        assertSecretaryProtectedEndpoints(coordinator, false, locationId, eventId, 403);
    }

    @Test
    void inactiveSecretaryIsForbiddenFromSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Secretaria Inativa Setup");
        long eventId = createEvent(admin, "Evento Secretaria Inativa Setup");

        long secretaryPersonId = createSecretary("Secretaria Inativa", false);
        RequestPostProcessor inactiveSecretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        assertSecretaryProtectedEndpoints(inactiveSecretary, false, locationId, eventId, 403);
    }

    @Test
    void anonymousIsUnauthorizedFromSecretaryProtectedOperations() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Anonimo Setup");
        long eventId = createEvent(admin, "Evento Anonimo Setup");

        assertSecretaryProtectedEndpoints(null, false, locationId, eventId, 401);
    }

    // --- Regras específicas exigidas pela feature ---

    @Test
    void revokingSecretaryImmediatelyBlocksSameAuthenticatedUserWithoutReissuingPrincipal() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);

        long secretaryPersonId = createSecretary("Secretaria Revogacao", true);
        RequestPostProcessor secretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        mockMvc.perform(post("/locais").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload("Local Antes Da Revogacao")))
                .andExpect(status().isCreated());

        ParishStaffAssignment assignment = parishStaffAssignmentRepository
                .findByPersonIdAndResponsibility(secretaryPersonId, ParishResponsibilityType.PARISH_SECRETARY)
                .orElseThrow();
        assignment.deactivate();
        parishStaffAssignmentRepository.saveAndFlush(assignment);

        mockMvc.perform(post("/locais").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload("Local Depois Da Revogacao")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpointsRemainForbiddenForActiveSecretary() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);
        long locationId = createLocation(admin, "Local Admin Only Setup");
        long eventId = createEvent(admin, "Evento Admin Only Setup");

        long secretaryPersonId = createSecretary("Secretaria Admin Only", true);
        RequestPostProcessor secretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        mockMvc.perform(post("/eventos/com-escala").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content(eventWithScalePayload(locationId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/eventos/" + eventId + "/escala").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\": " + locationId + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/locais/" + locationId)
                        .with(secretary))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/eventos/" + eventId)
                        .with(secretary))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretaryCannotAlterNameOrDioceseAndReceivesBadRequestForUnknownFields() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        mockMvc.perform(put("/paroquia").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia Original Mass Assignment",
                                  "diocese": "Diocese Original Mass Assignment",
                                  "institutionalPhone": "34999990000"
                                }
                                """))
                .andExpect(status().isOk());

        long secretaryPersonId = createSecretary("Secretaria Mass Assignment", true);
        RequestPostProcessor secretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        mockMvc.perform(put("/paroquia/contato").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institutionalPhone": "34988887777",
                                  "name": "Paróquia Adulterada"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(put("/paroquia/contato").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institutionalPhone": "34988887777",
                                  "diocese": "Diocese Adulterada"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/paroquia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia Original Mass Assignment"))
                .andExpect(jsonPath("$.diocese").value("Diocese Original Mass Assignment"))
                .andExpect(jsonPath("$.institutionalPhone").value("34999990000"));
    }

    @Test
    void secretaryContactUpdateNormalizesFieldsAndSucceeds() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        ensureParishProfileConfigured(admin);

        long secretaryPersonId = createSecretary("Secretaria Normalizacao", true);
        RequestPostProcessor secretary = asUser(secretaryPersonId, "ROLE_OPERATOR");

        mockMvc.perform(put("/paroquia/contato").with(secretary).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institutionalPhone": "  34977776666  ",
                                  "institutionalEmail": "  secretaria.normalizacao@paroquia.org.br  ",
                                  "officeAddress": "  Rua das Flores, 22  ",
                                  "officeHours": "  Segunda a sexta, das 9h às 17h  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionalPhone").value("34977776666"))
                .andExpect(jsonPath("$.institutionalEmail").value("secretaria.normalizacao@paroquia.org.br"))
                .andExpect(jsonPath("$.officeAddress").value("Rua das Flores, 22"))
                .andExpect(jsonPath("$.officeHours").value("Segunda a sexta, das 9h às 17h"));
    }

    // O cenário configured=false -> PARISH_PROFILE_NOT_CONFIGURED em PUT /paroquia/contato não é
    // testável de forma determinística aqui: o ParishProfile é um singleton compartilhado por todo
    // o contexto Spring (@SpringBootTest cacheado entre classes), e outras classes desta suíte já
    // configuram o perfil antes desta classe rodar, sem um endpoint para "desconfigurar". Esse
    // comportamento é coberto deterministicamente em ParishProfileServiceImplTest
    // (updateContactShouldThrowNotConfiguredExceptionWhenProfileIsNotConfigured) e em
    // ParishProfileControllerTest (shouldReturnNotConfiguredWhenUpdatingContactBeforeInitialConfiguration).

    // --- Regressão: admin full PUT continua funcionando ---

    @Test
    void adminFullUpdateStillUpdatesAllSixFields() throws Exception {
        RequestPostProcessor admin = asUser(ADMIN_PERSON_ID, "ROLE_ADMIN");
        mockMvc.perform(put("/paroquia").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia Regressão Admin",
                                  "diocese": "Diocese Regressão Admin",
                                  "institutionalPhone": "34955554444",
                                  "institutionalEmail": "regressao@paroquia.org.br",
                                  "officeAddress": "Av. Regressão, 10",
                                  "officeHours": "Terça a sábado, das 8h às 12h"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia Regressão Admin"))
                .andExpect(jsonPath("$.diocese").value("Diocese Regressão Admin"))
                .andExpect(jsonPath("$.institutionalPhone").value("34955554444"))
                .andExpect(jsonPath("$.institutionalEmail").value("regressao@paroquia.org.br"))
                .andExpect(jsonPath("$.officeAddress").value("Av. Regressão, 10"))
                .andExpect(jsonPath("$.officeHours").value("Terça a sábado, das 8h às 12h"));
    }

    // --- Helpers ---

    private void assertSecretaryProtectedEndpoints(
            RequestPostProcessor actor, boolean allowed, long locationId, long eventId, int deniedStatus
    ) throws Exception {
        int postStatus = allowed ? 201 : deniedStatus;
        int putStatus = allowed ? 200 : deniedStatus;

        mockMvc.perform(withActor(put("/paroquia/contato"), actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactPayload()))
                .andExpect(status().is(putStatus));

        mockMvc.perform(withActor(post("/locais"), actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload("Local Matriz " + PHONE_SEQ.incrementAndGet())))
                .andExpect(status().is(postStatus));

        mockMvc.perform(withActor(put("/locais/" + locationId), actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload("Local Matriz Atualizado")))
                .andExpect(status().is(putStatus));

        mockMvc.perform(withActor(post("/eventos"), actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Evento Matriz")))
                .andExpect(status().is(postStatus));

        mockMvc.perform(withActor(put("/eventos/" + eventId), actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Evento Matriz Atualizado")))
                .andExpect(status().is(putStatus));
    }

    private MockHttpServletRequestBuilder withActor(MockHttpServletRequestBuilder builder, RequestPostProcessor actor) {
        return actor == null ? builder : builder.with(actor);
    }

    private void ensureParishProfileConfigured(RequestPostProcessor admin) throws Exception {
        mockMvc.perform(put("/paroquia").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia Matriz Secretaria",
                                  "diocese": "Diocese Matriz Secretaria"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private long createLocation(RequestPostProcessor admin, String churchName) throws Exception {
        String response = mockMvc.perform(post("/locais").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(churchName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(response);
    }

    private long createEvent(RequestPostProcessor admin, String name) throws Exception {
        String response = mockMvc.perform(post("/eventos").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(response);
    }

    private long extractId(String jsonResponse) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
        if (!matcher.find()) {
            throw new IllegalStateException("Resposta sem campo id: " + jsonResponse);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String contactPayload() {
        return """
                {
                  "institutionalPhone": "34966665555",
                  "institutionalEmail": "matriz@paroquia.org.br",
                  "officeAddress": "Rua Matriz, 1",
                  "officeHours": "Segunda a sexta, das 8h às 17h"
                }
                """;
    }

    private String locationPayload(String churchName) {
        return """
                {
                  "churchName": "%s",
                  "address": "Rua Central, 100"
                }
                """.formatted(churchName);
    }

    private String eventPayload(String name) {
        return """
                {
                  "nameMassOrEvent": "%s",
                  "startAt": "2027-06-15T19:00:00",
                  "endAt": "2027-06-15T20:00:00",
                  "massOrCelebration": true
                }
                """.formatted(name);
    }

    private String eventWithScalePayload(long locationId) {
        return """
                {
                  "nameMassOrEvent": "Evento Com Escala Bloqueado",
                  "startAt": "2027-06-16T19:00:00",
                  "endAt": "2027-06-16T20:00:00",
                  "massOrCelebration": true,
                  "locationId": %d
                }
                """.formatted(locationId);
    }

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
        return savePerson(name).getId();
    }

    private long createSecretary(String name, boolean active) {
        Person person = savePerson(name);
        ParishStaffAssignment assignment = new ParishStaffAssignment(person, ParishResponsibilityType.PARISH_SECRETARY);
        if (!active) {
            assignment.deactivate();
        }
        parishStaffAssignmentRepository.saveAndFlush(assignment);
        createdStaffPersonIds.add(person.getId());
        return person.getId();
    }

    private long createPastorOnly(String name) {
        Person person = savePerson(name);
        ParishStaffAssignment assignment = new ParishStaffAssignment(person, ParishResponsibilityType.PASTOR);
        parishStaffAssignmentRepository.saveAndFlush(assignment);
        createdStaffPersonIds.add(person.getId());
        return person.getId();
    }

    private long createCoordinatorOnly(String name) {
        Person person = savePerson(name);
        PersonMinistry ministry = new PersonMinistry(person, MinistryType.READER);
        ministry.grantCoordination();
        personMinistryRepository.saveAndFlush(ministry);
        createdCoordinatorMinistryPersonIds.add(person.getId());
        return person.getId();
    }

    private Person savePerson(String name) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber("3" + PHONE_SEQ.incrementAndGet());
        person.setBirthdayDate(LocalDate.of(1990, 1, 10));
        return personRepository.saveAndFlush(person);
    }
}
