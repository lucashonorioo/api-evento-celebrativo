package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.NotificationDeliveryService;
import com.eventoscelebrativos.service.SystemNotificationCommand;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    private static final String CLIENT_ID = "myclientid";
    private static final String CLIENT_SECRET = "myclientsecret";
    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    void shouldCreateGlobalNotificationAsAdminAndIgnoreUnknownControlledFields() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account admin = createAccountAndToken("Notif Admin Creator", "ROLE_ADMIN", cleanupPersonIds);

            MvcResult result = mockMvc.perform(post("/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "audience": "GLOBAL",
                                      "title": "Aviso importante",
                                      "message": "Mensagem para todos",
                                      "origin": "SYSTEM",
                                      "sender": "hacker",
                                      "senderName": "Invasor",
                                      "resolvedAt": "2020-01-01T00:00:00",
                                      "category": "URGENT_HACKED"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.origin").value("ADMIN"))
                    .andExpect(jsonPath("$.audience").value("GLOBAL"))
                    .andExpect(jsonPath("$.title").value("Aviso importante"))
                    .andExpect(jsonPath("$.recipientCount").exists())
                    .andReturn();

            long notificationId = objectMapper.readTree(result.getResponse().getContentAsString()).get("notificationId").asLong();
            String senderNameSnapshot = jdbcTemplate.queryForObject(
                    "SELECT sender_name_snapshot FROM tb_notification WHERE id = ?", String.class, notificationId);
            assertEquals("Notif Admin Creator", senderNameSnapshot);
            String category = jdbcTemplate.queryForObject(
                    "SELECT category FROM tb_notification WHERE id = ?", String.class, notificationId);
            assertEquals("GENERAL", category);
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldForbidOperatorFromCreatingNotification() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account operator = createAccountAndToken("Notif Operator Forbidden", "ROLE_OPERATOR", cleanupPersonIds);

            mockMvc.perform(post("/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"audience": "GLOBAL", "title": "T", "message": "M"}
                                    """))
                    .andExpect(status().isForbidden());
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldRejectUnauthenticatedNotificationCreation() throws Exception {
        mockMvc.perform(post("/notificacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audience": "GLOBAL", "title": "T", "message": "M"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListAndDetailOwnInboxWithPreviewTruncationAndNotLeakOtherAccountsNotification() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account operator = createAccountAndToken("Inbox Operator", "ROLE_OPERATOR", cleanupPersonIds);
            Account otherOperator = createAccountAndToken("Other Operator", "ROLE_OPERATOR", cleanupPersonIds);

            String longMessage = "x".repeat(200);
            var response = notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.DIRECT, NotificationCategory.GENERAL, "Titulo Longo", longMessage,
                    null, List.of(operator.personId()), null, null, null, null));

            mockMvc.perform(get("/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token())
                            .param("filter", "ALL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].notificationId").value(response.getNotificationId()))
                    .andExpect(jsonPath("$.content[0].messagePreview").value("x".repeat(159) + "…"))
                    .andExpect(jsonPath("$.content[0].readAt").doesNotExist());

            mockMvc.perform(get("/notificacoes/{id}", response.getNotificationId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(longMessage));

            // Outra conta nao pode acessar a notificacao alheia por ID conhecido.
            mockMvc.perform(get("/notificacoes/{id}", response.getNotificationId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherOperator.token()))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldMarkAsReadIdempotentlyAndReturn404ForForeignOrUnknownNotification() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account operator = createAccountAndToken("Read Operator", "ROLE_OPERATOR", cleanupPersonIds);
            Account other = createAccountAndToken("Read Other", "ROLE_OPERATOR", cleanupPersonIds);

            var response = notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.DIRECT, NotificationCategory.GENERAL, "T", "M",
                    null, List.of(operator.personId()), null, null, null, null));

            mockMvc.perform(put("/notificacoes/{id}/leitura", response.getNotificationId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isNoContent());
            // Idempotente: repetir nao falha.
            mockMvc.perform(put("/notificacoes/{id}/leitura", response.getNotificationId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(put("/notificacoes/{id}/leitura", response.getNotificationId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/notificacoes/{id}/leitura", 999999999L)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldMarkAllAsReadAndReflectUnreadCount() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account operator = createAccountAndToken("MarkAll Operator", "ROLE_OPERATOR", cleanupPersonIds);

            notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.DIRECT, NotificationCategory.GENERAL, "T1", "M1",
                    null, List.of(operator.personId()), null, null, null, null));
            notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                    NotificationAudience.DIRECT, NotificationCategory.GENERAL, "T2", "M2",
                    null, List.of(operator.personId()), null, null, null, null));

            mockMvc.perform(get("/notificacoes/nao-lidas/contagem")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(2));

            mockMvc.perform(put("/notificacoes/leitura/todas")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/notificacoes/nao-lidas/contagem")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(0));
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldExposeAdminHistoryWithSenderPersonIdAndCounts() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account admin = createAccountAndToken("History Admin", "ROLE_ADMIN", cleanupPersonIds);
            Account recipient = createAccountAndToken("History Recipient", "ROLE_OPERATOR", cleanupPersonIds);

            MvcResult created = mockMvc.perform(post("/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"audience": "DIRECT", "title": "Historico", "message": "Msg historico", "personIds": [%d]}
                                    """.formatted(recipient.personId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            long notificationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("notificationId").asLong();

            mockMvc.perform(get("/admin/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                            .param("origin", "ADMIN")
                            .param("audience", "DIRECT")
                            .param("senderPersonId", String.valueOf(admin.personId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].notificationId").value(notificationId))
                    .andExpect(jsonPath("$.content[0].senderPersonId").value(admin.personId()))
                    .andExpect(jsonPath("$.content[0].recipientCount").value(1))
                    .andExpect(jsonPath("$.content[0].readCount").value(0));

            mockMvc.perform(get("/admin/notificacoes/{id}", notificationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.senderPersonId").value(admin.personId()))
                    .andExpect(jsonPath("$.recipientCount").value(1))
                    .andExpect(jsonPath("$.readCount").value(0));

            mockMvc.perform(get("/admin/notificacoes/{id}/destinatarios", notificationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].recipientPersonId").value(recipient.personId()))
                    .andExpect(jsonPath("$.content[0].recipientNameSnapshot").value("History Recipient"))
                    .andExpect(jsonPath("$.content[0].readAt").doesNotExist());
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    @Test
    void shouldForbidOperatorFromAllAdminNotificationEndpoints() throws Exception {
        List<Long> cleanupPersonIds = new ArrayList<>();
        try {
            Account operator = createAccountAndToken("Forbidden Admin Endpoints Operator", "ROLE_OPERATOR", cleanupPersonIds);

            mockMvc.perform(get("/admin/notificacoes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/notificacoes/{id}", 1L)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/notificacoes/{id}/destinatarios", 1L)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operator.token()))
                    .andExpect(status().isForbidden());
        } finally {
            cleanupPeople(cleanupPersonIds);
        }
    }

    private Account createAccountAndToken(String name, String authority, List<Long> cleanupPersonIds) throws Exception {
        String phone = uniquePhone();
        Person person = new Person(name, phone, BIRTHDAY);
        person.setActive(true);
        Person savedPerson = personRepository.saveAndFlush(person);
        cleanupPersonIds.add(savedPerson.getId());

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(savedPerson, phone, passwordEncoder.encode("123456"), now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByAuthority(authority).orElseThrow();
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));

        String token = obtainAccessToken(phone, "123456");
        return new Account(savedPerson.getId(), savedAccount.getId(), token);
    }

    private String obtainAccessToken(String username, String password) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("access_token").asText();
    }

    private void cleanupPeople(List<Long> personIds) {
        for (Long personId : personIds.reversed()) {
            jdbcTemplate.update(
                    "DELETE FROM tb_notification_recipient WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId);
            jdbcTemplate.update(
                    "DELETE FROM tb_notification WHERE sender_user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId);
            jdbcTemplate.update("DELETE FROM tb_event_assignment WHERE person_id = ?", personId);
            jdbcTemplate.update("DELETE FROM tb_person_ministry WHERE person_id = ?", personId);
            jdbcTemplate.update(
                    "DELETE FROM tb_user_account_role WHERE user_account_id IN (SELECT id FROM tb_user_account WHERE person_id = ?)",
                    personId);
            jdbcTemplate.update("DELETE FROM tb_user_account WHERE person_id = ?", personId);            jdbcTemplate.update("DELETE FROM tb_person WHERE id = ?", personId);
        }
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3498" + String.format("%07d", suffix);
    }

    private record Account(Long personId, Long accountId, String token) {
    }
}
