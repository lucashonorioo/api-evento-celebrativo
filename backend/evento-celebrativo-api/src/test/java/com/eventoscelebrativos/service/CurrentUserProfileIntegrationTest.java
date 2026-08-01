package com.eventoscelebrativos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta pelos endpoints reais GET/PUT /pessoas/me, que o perfil proprio e
 * identificado exclusivamente pelo phoneNumber do principal autenticado, que a atualizacao afeta
 * somente a pessoa autenticada preservando telefone, senha, roles, ministerios e vinculos com
 * escalas de outra pessoa, e que uma falha de persistencia reverte a transacao.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class CurrentUserProfileIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private PersonRepository personRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private EventAssignmentRepository eventAssignmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CelebrationEventRepository celebrationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnOnlyAuthenticatedPersonProfile() throws Exception {
        Long targetId = null;
        Long otherId = null;
        try {
            String targetPhone = uniquePhoneNumber();
            targetId = savePersonWithRole("Target Person", targetPhone, "ROLE_OPERATOR");
            otherId = savePersonWithRole("Other Person", uniquePhoneNumber(), "ROLE_OPERATOR");
            addMinistry(targetId, MinistryType.READER);
            Long finalTargetId = targetId;

            withAuthenticatedUser(finalTargetId, targetPhone, "OPERATOR", () -> mockMvc.perform(get("/pessoas/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(finalTargetId))
                    .andExpect(jsonPath("$.phoneNumber").value(targetPhone))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                    .andExpect(jsonPath("$.ministries[0]").value("READER"))
                    .andExpect(jsonPath("$.password").doesNotExist()));
        } finally {
            cleanupPerson(targetId);
            cleanupPerson(otherId);
        }
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedPrincipalDoesNotCorrespondToAnyPerson() throws Exception {
        withAuthenticatedUser(-1L, uniquePhoneNumber(), "OPERATOR", () -> mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND")));
    }

    @Test
    void shouldUpdateOnlyAuthenticatedPersonPreservingProtectedDataAndOtherPerson() throws Exception {
        Long targetId = null;
        Long otherId = null;
        Long eventId = null;
        try {
            String targetPhone = uniquePhoneNumber();
            String otherPhone = uniquePhoneNumber();
            targetId = savePersonWithRole("Old Name", targetPhone, "ROLE_OPERATOR");
            otherId = savePersonWithRole("Other Person", otherPhone, "ROLE_ADMIN");
            addMinistry(targetId, MinistryType.READER);

            CelebrationEvent event = saveEvent("Own Profile Mass");
            eventId = event.getId();
            saveAssignment(event, targetId, EventAssignmentType.READER);

            LocalDate newBirthday = LocalDate.of(1985, 6, 20);
            Long finalTargetId = targetId;

            withAuthenticatedUser(finalTargetId, targetPhone, "OPERATOR", () -> mockMvc.perform(put("/pessoas/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "New Name",
                                      "birthdayDate": "%s",
                                      "id": 999999,
                                      "phoneNumber": "00000000000",
                                      "roles": ["ROLE_ADMIN"],
                                      "ministries": ["PRIEST"],
                                      "password": "new-password"
                                    }
                                    """.formatted(newBirthday))
                            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(finalTargetId))
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.phoneNumber").value(targetPhone))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_OPERATOR"))
                    .andExpect(jsonPath("$.ministries[0]").value("READER"))
                    .andExpect(jsonPath("$.password").doesNotExist()));

            Person updatedTarget = personRepository.findByIdWithRoles(targetId).orElseThrow();
            assertEquals("New Name", updatedTarget.getName());
            assertEquals(newBirthday, updatedTarget.getBirthdayDate());
            assertEquals(targetPhone, updatedTarget.getPhoneNumber());
            assertEquals("encoded-password", updatedTarget.getPassword());
            assertTrue(updatedTarget.hasRole("ROLE_OPERATOR"));
            assertTrue(personMinistryRepository.findByPersonIdAndMinistryType(targetId, MinistryType.READER)
                    .map(PersonMinistry::getActive).orElse(false));
            assertEquals(1, countAssignments(targetId));

            Person unchangedOther = personRepository.findByIdWithRoles(otherId).orElseThrow();
            assertTrue(unchangedOther.getName().startsWith("Other Person"));
            assertEquals(otherPhone, unchangedOther.getPhoneNumber());
            assertTrue(unchangedOther.hasRole("ROLE_ADMIN"));
        } finally {
            cleanupAssignments(eventId);
            cleanupPerson(targetId);
            cleanupPerson(otherId);
        }
    }

    @Test
    @DirtiesContext
    void shouldRollBackTransactionWhenPersistenceFails() throws Exception {
        Long targetId = null;
        try {
            String targetPhone = uniquePhoneNumber();
            targetId = savePersonWithRole("Rollback Person", targetPhone, "ROLE_OPERATOR");
            Long finalTargetId = targetId;

            doThrow(new DataIntegrityViolationException("simulated persistence failure"))
                    .when(personRepository)
                    .save(argThat(person -> person != null && finalTargetId.equals(person.getId())));

            withAuthenticatedUser(finalTargetId, targetPhone, "OPERATOR", () -> mockMvc.perform(put("/pessoas/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Should Not Persist",
                                      "birthdayDate": "1985-06-20"
                                    }
                                    """))
                    .andExpect(status().isConflict()));

            Person unchanged = personRepository.findById(targetId).orElseThrow();
            assertTrue(unchanged.getName().startsWith("Rollback Person"));
            assertEquals(BIRTHDAY, unchanged.getBirthdayDate());
        } finally {
            cleanupPerson(targetId);
        }
    }

    private void withAuthenticatedUser(Long personId, String phoneNumber, String role, ThrowingRunnable action) throws Exception {
        try {
            java.util.Set<org.springframework.security.core.GrantedAuthority> authorities = java.util.Set.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
            com.eventoscelebrativos.security.AuthenticatedUser authenticatedUser =
                    new com.eventoscelebrativos.security.AuthenticatedUser(1L, personId, phoneNumber, authorities);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            authenticatedUser,
                            "n/a",
                            authorities
                    )
            );
            action.run();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Long savePersonWithRole(String name, String phoneNumber, String roleAuthority) {
        Person person = new Person();
        person.setName(name + " " + UUID.randomUUID());
        person.setPhoneNumber(phoneNumber);
        person.setBirthdayDate(BIRTHDAY);
        person.setPassword("encoded-password");
        person.addRole(roleRepository.findByAuthority(roleAuthority).orElseThrow());
        return personRepository.saveAndFlush(person).getId();
    }

    private void addMinistry(Long personId, MinistryType ministryType) {
        Person person = personRepository.findById(personId).orElseThrow();
        PersonMinistry ministry = new PersonMinistry(person, ministryType);
        ministry.setActive(true);
        personMinistryRepository.saveAndFlush(ministry);
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

    private int countAssignments(Long personId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_event_assignment WHERE person_id = ?",
                Integer.class,
                personId
        );
        return count == null ? 0 : count;
    }

    private void cleanupAssignments(Long eventId) {
        if (eventId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM tb_celebration_event WHERE id = ?", eventId);
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
        return "3494" + String.format("%07d", suffix);
    }
}
