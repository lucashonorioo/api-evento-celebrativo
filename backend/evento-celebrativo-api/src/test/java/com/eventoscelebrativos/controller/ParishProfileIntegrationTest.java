package com.eventoscelebrativos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo administrativo completo de {@code /paroquia} contra o contexto Spring real (H2, profile
 * test): perfil ainda nao configurado, primeira configuracao, consulta publica apos configurado, e
 * atualizacao posterior reaproveitando a mesma linha singleton. Verifica diretamente no banco que
 * {@code tb_parish_profile} nunca tem mais de uma linha e que ela permanece com id=1.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParishProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldConfigureThenPubliclyExposeThenUpdateTheSameSingletonRow() throws Exception {
        assertEquals(1, singletonRowCount());
        assertEquals(1L, singletonId());

        mockMvc.perform(put("/paroquia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia São José",
                                  "diocese": "Diocese de Uberlândia",
                                  "institutionalPhone": "34999990000",
                                  "institutionalEmail": "secretaria@paroquia.org.br",
                                  "officeAddress": "Praça Central, 1",
                                  "officeHours": "Segunda a sexta, das 8h às 17h"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia São José"))
                .andExpect(jsonPath("$.diocese").value("Diocese de Uberlândia"));

        assertEquals(1, singletonRowCount());
        assertEquals(1L, singletonId());

        mockMvc.perform(get("/paroquia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia São José"))
                .andExpect(jsonPath("$.diocese").value("Diocese de Uberlândia"))
                .andExpect(jsonPath("$.institutionalPhone").value("34999990000"))
                .andExpect(jsonPath("$.institutionalEmail").value("secretaria@paroquia.org.br"))
                .andExpect(jsonPath("$.officeAddress").value("Praça Central, 1"))
                .andExpect(jsonPath("$.officeHours").value("Segunda a sexta, das 8h às 17h"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.configured").doesNotExist());

        mockMvc.perform(put("/paroquia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia Nossa Senhora do Rosário",
                                  "diocese": "Diocese de Uberlândia"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paróquia Nossa Senhora do Rosário"))
                .andExpect(jsonPath("$.institutionalPhone").doesNotExist());

        assertEquals(1, singletonRowCount());
        assertEquals(1L, singletonId());
        assertEquals(
                "Paróquia Nossa Senhora do Rosário",
                jdbcTemplate.queryForObject("SELECT name FROM tb_parish_profile WHERE id = 1", String.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(put("/paroquia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "diocese": "Diocese de Uberlândia"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/paroquia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Paróquia Teste Inválido",
                                  "diocese": "Diocese Teste",
                                  "institutionalEmail": "nao-e-um-email"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private int singletonRowCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_parish_profile", Integer.class);
        return count == null ? 0 : count;
    }

    private long singletonId() {
        return jdbcTemplate.queryForObject("SELECT id FROM tb_parish_profile", Long.class);
    }
}
