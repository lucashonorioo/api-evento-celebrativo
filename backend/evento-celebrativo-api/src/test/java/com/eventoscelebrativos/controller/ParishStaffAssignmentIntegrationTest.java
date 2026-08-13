package com.eventoscelebrativos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.eventoscelebrativos.security.WithMockAuthenticatedUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo administrativo completo de responsabilidades paroquiais institucionais (PASTOR,
 * PARISH_SECRETARY) contra o contexto Spring real (H2, profile test). Cada teste insere suas
 * proprias pessoas isoladas via JdbcTemplate para nao depender nem interferir no estado de outras
 * classes de teste que compartilham o mesmo contexto/banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParishStaffAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldGrantPastorExposeInTeamAndHistoryAndBeIdempotent() throws Exception {
        long priestId = insertPerson("Padre Integracao A", "35900000001", true);
        insertActivePriestMinistry(priestId);

        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastor.personId").value(priestId))
                .andExpect(jsonPath("$.pastor.name").value("Padre Integracao A"));

        mockMvc.perform(get("/pessoas/" + priestId + "/responsabilidades-paroquiais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(priestId))
                .andExpect(jsonPath("$.name").value("Padre Integracao A"))
                .andExpect(jsonPath("$.responsibilities[0].responsibility").value("PASTOR"))
                .andExpect(jsonPath("$.responsibilities[0].active").value(true));

        String firstUpdatedAt = readUpdatedAt(priestId, "PASTOR");

        // idempotente: reenviar nao altera updatedAt
        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId))
                .andExpect(status().isNoContent());
        assertEquals(firstUpdatedAt, readUpdatedAt(priestId, "PASTOR"));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastor").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectSecondActivePastorAndAllowAfterFirstIsRevoked() throws Exception {
        long priestA = insertPerson("Padre Integracao B", "35900000002", true);
        long priestB = insertPerson("Padre Integracao C", "35900000003", true);
        insertActivePriestMinistry(priestA);
        insertActivePriestMinistry(priestB);

        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestA))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PARISH_ACTIVE_PASTOR_ALREADY_EXISTS"));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestA))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(jsonPath("$.pastor.personId").value(priestB));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestB))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectPastorGrantWithoutActivePriestMinistry() throws Exception {
        long personId = insertPerson("Pessoa Sem Ministerio", "35900000004", true);

        mockMvc.perform(put("/paroquia/equipe/pastor/" + personId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PASTOR_PRIEST_MINISTRY_REQUIRED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReactivatePastorReusingSameRowAfterRevocation() throws Exception {
        long priestId = insertPerson("Padre Integracao D", "35900000005", true);
        insertActivePriestMinistry(priestId);

        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());
        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = 'PASTOR'",
                Integer.class, priestId);
        assertEquals(1, rowCount);
        mockMvc.perform(get("/pessoas/" + priestId + "/responsabilidades-paroquiais"))
                .andExpect(jsonPath("$.responsibilities[0].active").value(true));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldGrantMultipleActiveSecretariesWithoutAccount() throws Exception {
        long personA = insertPerson("Secretaria Integracao A", "35900000006", true);
        long personB = insertPerson("Secretaria Integracao B", "35900000007", true);

        mockMvc.perform(put("/paroquia/equipe/secretarios/" + personA)).andExpect(status().isNoContent());
        mockMvc.perform(put("/paroquia/equipe/secretarios/" + personB)).andExpect(status().isNoContent());

        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretaries.length()").value(2))
                .andExpect(jsonPath("$.secretaries[0].personId").value(personA))
                .andExpect(jsonPath("$.secretaries[0].name").value("Secretaria Integracao A"))
                .andExpect(jsonPath("$.secretaries[1].personId").value(personB))
                .andExpect(jsonPath("$.secretaries[1].name").value("Secretaria Integracao B"));

        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user_account WHERE person_id IN (?, ?)", Integer.class, personA, personB);
        assertEquals(0, accountCount);

        mockMvc.perform(delete("/paroquia/equipe/secretarios/" + personA)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/paroquia/equipe/secretarios/" + personB)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectGrantingResponsibilityToInactivePerson() throws Exception {
        long inactivePersonId = insertPerson("Pessoa Inativa", "35900000008", false);

        mockMvc.perform(put("/paroquia/equipe/secretarios/" + inactivePersonId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSON_INACTIVE"));

        insertActivePriestMinistry(inactivePersonId);
        mockMvc.perform(put("/paroquia/equipe/pastor/" + inactivePersonId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSON_INACTIVE"));
    }

    @Test
    @WithMockAuthenticatedUser(accountId = 14L, personId = 14L, username = "34999887766", authorities = {"ROLE_ADMIN"})
    void shouldBlockPersonDeactivationWhileResponsibilityIsActive() throws Exception {
        long personId = insertPerson("Secretaria Bloqueio", "35900000009", true);
        mockMvc.perform(put("/paroquia/equipe/secretarios/" + personId)).andExpect(status().isNoContent());

        mockMvc.perform(put("/pessoas/" + personId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSON_HAS_ACTIVE_PARISH_RESPONSIBILITIES"));

        assertEquals(true, jdbcTemplate.queryForObject(
                "SELECT active FROM tb_person WHERE id = ?", Boolean.class, personId));

        mockMvc.perform(delete("/paroquia/equipe/secretarios/" + personId)).andExpect(status().isNoContent());

        mockMvc.perform(put("/pessoas/" + personId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldBlockPriestRemovalViaDedicatedEndpointWhilePastorIsActive() throws Exception {
        long priestId = insertPerson("Padre Integracao E", "35900000010", true);
        insertActivePriestMinistry(priestId);
        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());

        mockMvc.perform(delete("/padres/" + priestId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PASTOR_PRIEST_MINISTRY_REQUIRED"));

        assertEquals(true, jdbcTemplate.queryForObject(
                "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'PRIEST'",
                Boolean.class, priestId));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldBlockPriestRemovalViaMinistriesSyncWhilePastorIsActive() throws Exception {
        long priestId = insertPerson("Padre Integracao F", "35900000011", true);
        insertActivePriestMinistry(priestId);
        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());

        mockMvc.perform(put("/pessoas/" + priestId + "/ministries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ministries\": []}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PASTOR_PRIEST_MINISTRY_REQUIRED"));

        assertEquals(true, jdbcTemplate.queryForObject(
                "SELECT active FROM tb_person_ministry WHERE person_id = ? AND ministry_type = 'PRIEST'",
                Boolean.class, priestId));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundForNonexistentPersonOnAllEndpoints() throws Exception {
        long missingId = 987654321L;

        mockMvc.perform(get("/pessoas/" + missingId + "/responsabilidades-paroquiais"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/paroquia/equipe/pastor/" + missingId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/paroquia/equipe/pastor/" + missingId))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/paroquia/equipe/secretarios/" + missingId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/paroquia/equipe/secretarios/" + missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnHistoryIncludingInactiveResponsibilities() throws Exception {
        long personId = insertPerson("Secretaria Historico", "35900000012", true);
        mockMvc.perform(put("/paroquia/equipe/secretarios/" + personId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/paroquia/equipe/secretarios/" + personId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/" + personId + "/responsabilidades-paroquiais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibilities[0].responsibility").value("PARISH_SECRETARY"))
                .andExpect(jsonPath("$.responsibilities[0].active").value(false));

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE person_id = ?", Integer.class, personId);
        assertEquals(1, rowCount);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDetectMultipleActivePastorsAsIntegrityViolationAndAllowRecoveryViaRevoke() throws Exception {
        long priestA = insertPerson("Padre Corrompido A", "35900000013", true);
        long priestB = insertPerson("Padre Corrompido B", "35900000014", true);
        insertActivePriestMinistry(priestA);
        insertActivePriestMinistry(priestB);
        // Corrupcao inserida deliberadamente por fora do service, bypassando o mutex de
        // ParishProfile(id=1), para simular um estado impossivel ja persistido.
        insertActivePastorAssignment(priestA);
        insertActivePastorAssignment(priestB);

        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("PARISH_STAFF_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.error").value("Inconsistência detectada na configuração da equipe paroquial."));

        long priestC = insertPerson("Padre Corrompido C", "35900000015", true);
        insertActivePriestMinistry(priestC);
        mockMvc.perform(put("/paroquia/equipe/pastor/" + priestC))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("PARISH_STAFF_INTEGRITY_VIOLATION"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_parish_staff_assignment WHERE person_id = ?", Integer.class, priestC));

        // Recuperacao administrativa: revokePastor continua funcionando especificamente sobre a
        // Person solicitada mesmo com o banco corrompido, sem tentar decidir automaticamente.
        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestA))
                .andExpect(status().isNoContent());

        // Depois da recuperacao, com exatamente 1 PASTOR ativo restante, a leitura volta ao normal.
        mockMvc.perform(get("/paroquia/equipe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastor.personId").value(priestB));

        mockMvc.perform(delete("/paroquia/equipe/pastor/" + priestB))
                .andExpect(status().isNoContent());
    }

    private void insertActivePastorAssignment(long personId) {
        jdbcTemplate.update(
                "INSERT INTO tb_parish_staff_assignment(person_id, responsibility, active, created_at, updated_at) "
                        + "VALUES (?, 'PASTOR', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                personId);
    }

    private long insertPerson(String name, String phoneNumber, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO tb_person(name, phone_number, active) VALUES (?, ?, ?)", name, phoneNumber, active);
        return jdbcTemplate.queryForObject("SELECT id FROM tb_person WHERE phone_number = ?", Long.class, phoneNumber);
    }

    private void insertActivePriestMinistry(long personId) {
        jdbcTemplate.update(
                "INSERT INTO tb_person_ministry(person_id, ministry_type, active, created_at, updated_at) "
                        + "VALUES (?, 'PRIEST', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                personId);
    }

    private String readUpdatedAt(long personId, String responsibility) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM tb_parish_staff_assignment WHERE person_id = ? AND responsibility = ?",
                String.class, personId, responsibility);
    }
}
