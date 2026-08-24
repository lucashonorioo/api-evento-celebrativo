package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationRecipient;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, para findMine (inbox) e findHistory (admin), todas as combinacoes de leitura (ALL/UNREAD/READ)
 * x NotificationResolutionFilter (ALL/ACTIVE/RESOLVED) - secao 13/14. Fixture fixa: uma notificacao
 * GENERAL lida, uma GENERAL nao lida, um conflito ativo lido, um conflito ativo nao lido, um conflito
 * resolvido lido e um conflito resolvido nao lido - 6 notificacoes cobrindo as 9 combinacoes (3x3,
 * superconjunto das 8 exigidas). ACTIVE/RESOLVED tambem restringem implicitamente a categoria
 * (somente SCHEDULE_CONFLICT aparece), o que e verificado explicitamente excluindo GENERAL das
 * contagens de ACTIVE/RESOLVED.
 */
@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class NotificationResolutionFilterCombinationIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);
    private static final LocalDateTime CURRENT_SECOND = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired
    private NotificationInboxService notificationInboxService;

    @Autowired
    private NotificationAdminService notificationAdminService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    private Long accountId;

    @BeforeEach
    void setUpFixture() {
        Person person = new Person("Filter Combination Recipient " + UUID.randomUUID(), uniquePhone(), BIRTHDAY);
        person.setActive(true);
        Person savedPerson = personRepository.saveAndFlush(person);

        UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", CURRENT_SECOND, CURRENT_SECOND);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow();
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));
        accountId = savedAccount.getId();

        createRecipientNotification(NotificationCategory.GENERAL, false, true);   // 1: GENERAL / read
        createRecipientNotification(NotificationCategory.GENERAL, false, false);  // 2: GENERAL / unread
        createRecipientNotification(NotificationCategory.SCHEDULE_CONFLICT, false, true);  // 3: active / read
        createRecipientNotification(NotificationCategory.SCHEDULE_CONFLICT, false, false); // 4: active / unread
        createRecipientNotification(NotificationCategory.SCHEDULE_CONFLICT, true, true);   // 5: resolved / read
        createRecipientNotification(NotificationCategory.SCHEDULE_CONFLICT, true, false);  // 6: resolved / unread
    }

    @Test
    void shouldCombineReadFilterAndResolutionFilterInPersonalInbox() {
        assertEquals(6, findMineTotal("ALL", "ALL"));
        assertEquals(3, findMineTotal("UNREAD", "ALL"));
        assertEquals(3, findMineTotal("READ", "ALL"));

        assertEquals(2, findMineTotal("ALL", "ACTIVE"));
        assertEquals(1, findMineTotal("UNREAD", "ACTIVE"));
        assertEquals(1, findMineTotal("READ", "ACTIVE"));

        assertEquals(2, findMineTotal("ALL", "RESOLVED"));
        assertEquals(1, findMineTotal("UNREAD", "RESOLVED"));
        assertEquals(1, findMineTotal("READ", "RESOLVED"));
    }

    @Test
    void shouldExcludeGeneralNotificationsWhenResolutionFilterIsActiveOrResolved() {
        Page<?> active = notificationInboxService.findMine(accountId, "ALL", "ACTIVE", 0, 100);
        Page<?> resolved = notificationInboxService.findMine(accountId, "ALL", "RESOLVED", 0, 100);
        assertTrue(active.getContent().stream().noneMatch(this::isGeneralSummary));
        assertTrue(resolved.getContent().stream().noneMatch(this::isGeneralSummary));
    }

    @Test
    void shouldCombineResolutionFilterInAdminHistoryIgnoringReadState() {
        assertEquals(6, findHistoryTotal("ALL"));
        assertEquals(2, findHistoryTotal("ACTIVE"));
        assertEquals(2, findHistoryTotal("RESOLVED"));
    }

    private boolean isGeneralSummary(Object summary) {
        try {
            var method = summary.getClass().getMethod("getCategory");
            return NotificationCategory.GENERAL.equals(method.invoke(summary));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private long findMineTotal(String filter, String resolutionFilter) {
        return notificationInboxService.findMine(accountId, filter, resolutionFilter, 0, 100).getTotalElements();
    }

    private long findHistoryTotal(String resolutionFilter) {
        return notificationAdminService.findHistory(null, null, null, null, null, resolutionFilter, 0, 100).getTotalElements();
    }

    private void createRecipientNotification(NotificationCategory category, boolean resolved, boolean read) {
        Notification notification = category == NotificationCategory.SCHEDULE_CONFLICT
                ? Notification.scheduleConflict(
                        NotificationAudience.ADMIN, "Conflito de escala detectado", "Mensagem de teste",
                        "CELEBRATION_EVENT", 1L, "SCHEDULE_UNAVAILABILITY_CONFLICT",
                        "1:" + UUID.randomUUID().hashCode(),
                        resolved ? null : "SCHEDULE_UNAVAILABILITY_CONFLICT:1:" + UUID.randomUUID().hashCode(),
                        CURRENT_SECOND)
                : Notification.system(
                        NotificationAudience.ADMIN, NotificationCategory.GENERAL, "Aviso geral", "Mensagem geral",
                        null, null, null, null, CURRENT_SECOND);
        notification = notificationRepository.saveAndFlush(notification);
        if (category == NotificationCategory.SCHEDULE_CONFLICT && resolved) {
            notification.resolve(CURRENT_SECOND.plusHours(1));
            notification = notificationRepository.saveAndFlush(notification);
        }

        UserAccount account = userAccountRepository.findById(accountId).orElseThrow();
        NotificationRecipient recipient = notificationRecipientRepository.saveAndFlush(
                new NotificationRecipient(notification, account, account.getPerson().getName()));
        if (read) {
            notificationRecipientRepository.markAsRead(notification.getId(), accountId, CURRENT_SECOND.plusMinutes(1));
        }
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3494" + String.format("%07d", suffix);
    }
}
