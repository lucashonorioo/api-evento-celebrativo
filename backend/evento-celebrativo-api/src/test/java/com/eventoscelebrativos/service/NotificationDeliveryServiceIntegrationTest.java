package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.response.InvalidRecipientReason;
import com.eventoscelebrativos.dto.response.NotificationCreateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.NotificationInvalidRecipientsException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Notification;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationCategory;
import com.eventoscelebrativos.model.NotificationOrigin;
import com.eventoscelebrativos.model.NotificationRecipient;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.model.UserAccountRole;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.NotificationTargetMinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
        "logging.level.com.eventoscelebrativos=WARN"
})
@Transactional
class NotificationDeliveryServiceIntegrationTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 1, 10);

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountRoleRepository userAccountRoleRepository;

    @Autowired
    private PersonMinistryRepository personMinistryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private NotificationTargetMinistryRepository notificationTargetMinistryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSendAdminGlobalNotificationToAllEligibleAccountsIncludingSenderSnapshotsAndNullResolvedAt() {
        Fixture sender = createPersonWithAccount("Admin Global Sender", List.of("ROLE_ADMIN"), true, true);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(), request(NotificationAudience.GLOBAL, "Aviso Geral", "Mensagem geral", null, null));

        Notification notification = notificationRepository.findById(response.getNotificationId()).orElseThrow();
        assertEquals(NotificationOrigin.ADMIN, notification.getOrigin());
        assertEquals(NotificationCategory.GENERAL, notification.getCategory());
        assertEquals(NotificationAudience.GLOBAL, notification.getAudience());
        assertEquals(sender.accountId(), notification.getSenderUserAccount().getId());
        assertEquals("Admin Global Sender", notification.getSenderNameSnapshot());
        assertNull(notification.getResolvedAt());
        assertNull(notification.getReferenceType());
        assertNull(notification.getSourceType());

        // As 15 contas de fixture (todas single-role desde a V18) + o proprio sender.
        assertEquals(16, response.getRecipientCount());
        NotificationRecipient senderRecipient = notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(notification.getId(), sender.accountId())
                .orElseThrow();
        assertEquals("Admin Global Sender", senderRecipient.getRecipientNameSnapshot());
        assertNull(senderRecipient.getReadAt());
    }

    @Test
    void shouldKeepSnapshotsAndHistoricalDataUnchangedAfterSubsequentStateChanges() {
        Fixture sender = createPersonWithAccount("Snapshot Sender Original", List.of("ROLE_ADMIN"), true, true);
        Fixture recipient = createPersonWithAccount("Snapshot Recipient Original", List.of("ROLE_OPERATOR"), true, true);
        addPersonMinistry(recipient.personId(), MinistryType.READER);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(),
                request(NotificationAudience.MINISTRY, "Titulo Original", "Mensagem original", List.of(MinistryType.READER), null));
        Long notificationId = response.getNotificationId();

        Notification original = notificationRepository.findById(notificationId).orElseThrow();
        NotificationRecipient originalRecipient = notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(notificationId, recipient.accountId()).orElseThrow();
        String originalSenderSnapshot = original.getSenderNameSnapshot();
        String originalRecipientSnapshot = originalRecipient.getRecipientNameSnapshot();
        String originalTitle = original.getTitle();
        String originalMessage = original.getMessage();
        NotificationOrigin originalOrigin = original.getOrigin();
        NotificationAudience originalAudience = original.getAudience();
        NotificationCategory originalCategory = original.getCategory();
        List<MinistryType> originalMinistryTypes = notificationTargetMinistryRepository.findMinistryTypesByNotificationId(notificationId);

        // 1. altera o nome do remetente
        Person senderPerson = personRepository.findById(sender.personId()).orElseThrow();
        senderPerson.setName("Sender Renomeado");
        personRepository.saveAndFlush(senderPerson);

        // 2. altera o nome do destinatario
        Person recipientPerson = personRepository.findById(recipient.personId()).orElseThrow();
        recipientPerson.setName("Recipient Renomeado");
        personRepository.saveAndFlush(recipientPerson);

        // 3. altera a role do destinatario
        UserAccount recipientAccount = userAccountRepository.findById(recipient.accountId()).orElseThrow();
        userAccountRoleRepository.deleteAllByUserAccountId(recipientAccount.getId());
        userAccountRoleRepository.saveAndFlush(new UserAccountRole(recipientAccount, roleRepository.findByAuthority("ROLE_ADMIN").orElseThrow()));

        // 4. altera o ministerio (desativa o vinculo usado no envio)
        deactivatePersonMinistry(recipient.personId(), MinistryType.READER);

        // 5. desabilita a conta do destinatario
        UserAccount toToggle = userAccountRepository.findById(recipient.accountId()).orElseThrow();
        toToggle.disable(LocalDateTime.now().withNano(0));
        userAccountRepository.saveAndFlush(toToggle);

        // 6. desativa a pessoa destinataria
        recipientPerson = personRepository.findById(recipient.personId()).orElseThrow();
        recipientPerson.setActive(false);
        personRepository.saveAndFlush(recipientPerson);

        // 7. reabilita a conta e reativa a pessoa
        toToggle = userAccountRepository.findById(recipient.accountId()).orElseThrow();
        toToggle.enable(LocalDateTime.now().withNano(0));
        userAccountRepository.saveAndFlush(toToggle);
        recipientPerson = personRepository.findById(recipient.personId()).orElseThrow();
        recipientPerson.setActive(true);
        personRepository.saveAndFlush(recipientPerson);
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
        NotificationRecipient reloadedRecipient = notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(notificationId, recipient.accountId()).orElseThrow();

        assertEquals(originalSenderSnapshot, reloaded.getSenderNameSnapshot());
        assertEquals(originalRecipientSnapshot, reloadedRecipient.getRecipientNameSnapshot());
        assertEquals(originalTitle, reloaded.getTitle());
        assertEquals(originalMessage, reloaded.getMessage());
        assertEquals(originalOrigin, reloaded.getOrigin());
        assertEquals(originalAudience, reloaded.getAudience());
        assertEquals(originalCategory, reloaded.getCategory());
        assertEquals(originalMinistryTypes, notificationTargetMinistryRepository.findMinistryTypesByNotificationId(notificationId));
        assertNull(reloaded.getResolvedAt());
        assertNull(reloadedRecipient.getReadAt());
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(notificationId, recipient.accountId()));
    }

    @Test
    void shouldSendAdminAudienceNotificationOnlyToSingleRoleAdminAccounts() {
        Fixture sender = createPersonWithAccount("Admin Audience Sender", List.of("ROLE_ADMIN"), true, true);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(), request(NotificationAudience.ADMIN, "Aviso Admin", "Mensagem admin", null, null));

        // fixture: apenas a conta 14 (Padre Paulo) tem exatamente uma role e e ROLE_ADMIN.
        assertEquals(2, response.getRecipientCount());
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 14L));
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), sender.accountId()));
    }

    @Test
    void shouldSendMinistryNotificationAndExcludeInactivePersonMinistry() {
        Fixture sender = createPersonWithAccount("Ministry Sender", List.of("ROLE_ADMIN"), true, true);
        deactivatePersonMinistry(4L, MinistryType.READER);
        deactivatePersonMinistry(6L, MinistryType.READER);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(),
                request(NotificationAudience.MINISTRY, "Aviso Leitores", "Mensagem leitores", List.of(MinistryType.READER), null));

        // fixture READER: contas 4, 5 e 6; 4 e 6 desativadas nesta chamada -> so a conta 5 permanece.
        assertEquals(1, response.getRecipientCount());
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 5L));
        assertFalse(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 4L));
        assertFalse(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 6L));
        List<MinistryType> ministryTypes = notificationTargetMinistryRepository.findMinistryTypesByNotificationId(response.getNotificationId());
        assertEquals(List.of(MinistryType.READER), ministryTypes);
    }

    @Test
    void shouldDeduplicateAccountReachableByMultipleSelectedMinistries() {
        Fixture sender = createPersonWithAccount("Overlap Sender", List.of("ROLE_ADMIN"), true, true);
        addPersonMinistry(5L, MinistryType.EUCHARISTIC_MINISTER);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(),
                request(NotificationAudience.MINISTRY, "Aviso Sobreposto", "Mensagem sobreposta",
                        List.of(MinistryType.READER, MinistryType.EUCHARISTIC_MINISTER), null));

        // READER: 4,5,6 | EUCHARISTIC_MINISTER: 10,11,12 e agora tambem 5 (sobreposicao) -> uniao = {4,5,6,10,11,12} = 6.
        assertEquals(6, response.getRecipientCount());
        long rowsForAccount5 = notificationRecipientRepository
                .findByNotificationIdAndUserAccountId(response.getNotificationId(), 5L)
                .stream().count();
        assertEquals(1, rowsForAccount5);
    }

    @Test
    void shouldRejectMinistrySendWhenAnySelectedTypeHasNoEligibleRecipient() {
        Fixture sender = createPersonWithAccount("Ministry Empty Sender", List.of("ROLE_ADMIN"), true, true);
        deactivatePersonMinistry(4L, MinistryType.READER);
        deactivatePersonMinistry(5L, MinistryType.READER);
        deactivatePersonMinistry(6L, MinistryType.READER);

        long notificationsBefore = notificationRepository.count();

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.MINISTRY, "Sem destinatario", "Mensagem",
                                List.of(MinistryType.READER), null)));

        assertEquals("NOTIFICATION_MINISTRY_WITHOUT_RECIPIENTS", exception.getErrorCode());
        assertEquals(notificationsBefore, notificationRepository.count());
    }

    @Test
    void shouldSendDirectNotificationIncludingSenderSelfWithDeduplication() {
        Fixture sender = createPersonWithAccount("Direct Sender", List.of("ROLE_ADMIN"), true, true);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                sender.accountId(),
                request(NotificationAudience.DIRECT, "Aviso Direto", "Mensagem direta",
                        null, List.of(sender.personId(), 2L, 2L)));

        assertEquals(2, response.getRecipientCount());
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), sender.accountId()));
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 2L));
    }

    @Test
    void shouldRejectDirectSendWhenPersonNotFound() {
        Fixture sender = createPersonWithAccount("Direct Invalid Sender 1", List.of("ROLE_ADMIN"), true, true);

        NotificationInvalidRecipientsException exception = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(999999999L))));

        assertEquals(1, exception.getInvalidRecipients().size());
        assertEquals(InvalidRecipientReason.PERSON_NOT_FOUND, exception.getInvalidRecipients().get(0).getReason());
    }

    @Test
    void shouldRejectDirectSendWhenPersonHasNoAccount() {
        Fixture sender = createPersonWithAccount("Direct Invalid Sender 2", List.of("ROLE_ADMIN"), true, true);
        Person personWithoutAccount = createPersonWithoutAccount("Sem Conta", true);

        NotificationInvalidRecipientsException exception = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(personWithoutAccount.getId()))));

        assertEquals(InvalidRecipientReason.USER_ACCOUNT_NOT_FOUND, exception.getInvalidRecipients().get(0).getReason());
    }

    @Test
    void shouldRejectDirectSendWhenAccountDisabled() {
        Fixture sender = createPersonWithAccount("Direct Invalid Sender 3", List.of("ROLE_ADMIN"), true, true);
        Fixture disabled = createPersonWithAccount("Disabled Person", List.of("ROLE_OPERATOR"), true, false);

        NotificationInvalidRecipientsException exception = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(disabled.personId()))));

        assertEquals(InvalidRecipientReason.USER_ACCOUNT_DISABLED, exception.getInvalidRecipients().get(0).getReason());
    }

    @Test
    void shouldRejectDirectSendWhenPersonInactive() {
        Fixture sender = createPersonWithAccount("Direct Invalid Sender 4", List.of("ROLE_ADMIN"), true, true);
        Fixture inactive = createPersonWithAccount("Inactive Person", List.of("ROLE_OPERATOR"), false, true);

        NotificationInvalidRecipientsException exception = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(inactive.personId()))));

        assertEquals(InvalidRecipientReason.PERSON_INACTIVE, exception.getInvalidRecipients().get(0).getReason());
    }

    @Test
    void shouldRejectDirectSendWhenAccountHasZeroRoles() {
        // Uma conta com mais de uma role deixou de ser um estado representavel desde a V18
        // (UNIQUE(user_account_id) em tb_user_account_role); zero roles continua sendo o unico
        // estado USER_ACCOUNT_ROLE_INVALID que pode ser fisicamente montado neste teste.
        Fixture sender = createPersonWithAccount("Direct Invalid Sender 5", List.of("ROLE_ADMIN"), true, true);
        Fixture noRoles = createPersonWithAccount("No Roles Person", List.of(), true, true);

        NotificationInvalidRecipientsException exceptionNoRoles = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(noRoles.personId()))));
        assertEquals(InvalidRecipientReason.USER_ACCOUNT_ROLE_INVALID, exceptionNoRoles.getInvalidRecipients().get(0).getReason());
    }

    @Test
    void shouldRejectEntireDirectSendWhenAnyRecipientInvalidEvenIfOthersValid() {
        Fixture sender = createPersonWithAccount("Direct Mixed Sender", List.of("ROLE_ADMIN"), true, true);
        long notificationsBefore = notificationRepository.count();

        NotificationInvalidRecipientsException exception = assertThrows(NotificationInvalidRecipientsException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(),
                        request(NotificationAudience.DIRECT, "T", "M", null, List.of(2L, 999999999L))));

        assertEquals(1, exception.getInvalidRecipients().size());
        assertEquals(notificationsBefore, notificationRepository.count());
    }

    @Test
    void shouldRejectAudienceFieldCombinationMismatches() {
        Fixture sender = createPersonWithAccount("Audience Fields Sender", List.of("ROLE_ADMIN"), true, true);

        assertAudienceFieldsInvalid(sender, NotificationAudience.GLOBAL, List.of(MinistryType.READER), null);
        assertAudienceFieldsInvalid(sender, NotificationAudience.GLOBAL, null, List.of(2L));
        assertAudienceFieldsInvalid(sender, NotificationAudience.ADMIN, List.of(MinistryType.READER), null);
        assertAudienceFieldsInvalid(sender, NotificationAudience.MINISTRY, null, null);
        assertAudienceFieldsInvalid(sender, NotificationAudience.MINISTRY, List.of(MinistryType.READER), List.of(2L));
        assertAudienceFieldsInvalid(sender, NotificationAudience.DIRECT, null, null);
        assertAudienceFieldsInvalid(sender, NotificationAudience.DIRECT, List.of(MinistryType.READER), List.of(2L));
    }

    private void assertAudienceFieldsInvalid(Fixture sender, NotificationAudience audience, List<MinistryType> ministryTypes, List<Long> personIds) {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(), request(audience, "T", "M", ministryTypes, personIds)));
        assertEquals("NOTIFICATION_AUDIENCE_FIELDS_INVALID", exception.getErrorCode());
    }

    @Test
    void shouldRejectSystemGlobalSendWhenNoEligibleAccountsExist() {
        // SYSTEM nao tem sender, entao (diferente de ADMIN) o publico pode ficar vazio de fato:
        // um envio ADMIN sempre inclui o proprio remetente valido como candidato GLOBAL/ADMIN.
        jdbcTemplate.update("UPDATE tb_user_account SET enabled = FALSE");
        long notificationsBefore = notificationRepository.count();

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () ->
                notificationDeliveryService.sendSystemNotification(new SystemNotificationCommand(
                        NotificationAudience.GLOBAL, NotificationCategory.GENERAL, "T", "M",
                        null, null, null, null, null, null)));

        assertEquals("NOTIFICATION_NO_ELIGIBLE_RECIPIENTS", exception.getErrorCode());
        assertEquals(notificationsBefore, notificationRepository.count());
    }

    @Test
    void shouldRejectAdminSendWhenSenderIsNotActuallyAdmin() {
        Fixture operator = createPersonWithAccount("Not Admin Sender", List.of("ROLE_OPERATOR"), true, true);

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        operator.accountId(), request(NotificationAudience.GLOBAL, "T", "M", null, null)));

        assertEquals("NOTIFICATION_SENDER_NOT_ELIGIBLE", exception.getErrorCode());
    }

    @Test
    void shouldRejectAdminSendWhenSenderAccountDisabled() {
        Fixture sender = createPersonWithAccount("Disabled Admin Sender", List.of("ROLE_ADMIN"), true, false);

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        sender.accountId(), request(NotificationAudience.GLOBAL, "T", "M", null, null)));

        assertEquals("NOTIFICATION_SENDER_NOT_ELIGIBLE", exception.getErrorCode());
    }

    @Test
    void shouldRejectAdminSendWhenSenderAccountDoesNotExist() {
        assertThrows(ResourceNotFoundException.class, () ->
                notificationDeliveryService.sendAdministrativeNotification(
                        999999999L, request(NotificationAudience.GLOBAL, "T", "M", null, null)));
    }

    @Test
    void shouldSendSystemNotificationWithNullSenderAndSistemaSnapshotAndReferenceAndSource() {
        NotificationCreateResponseDTO response = notificationDeliveryService.sendSystemNotification(
                new SystemNotificationCommand(
                        NotificationAudience.DIRECT,
                        NotificationCategory.GENERAL,
                        "Conflito de escala",
                        "Mensagem de sistema",
                        null,
                        List.of(2L),
                        "CELEBRATION_EVENT",
                        99L,
                        "SCHEDULE_UNAVAILABILITY_CONFLICT",
                        "99:2"
                ));

        Notification notification = notificationRepository.findById(response.getNotificationId()).orElseThrow();
        assertEquals(NotificationOrigin.SYSTEM, notification.getOrigin());
        assertNull(notification.getSenderUserAccount());
        assertEquals("Sistema", notification.getSenderNameSnapshot());
        assertEquals("CELEBRATION_EVENT", notification.getReferenceType());
        assertEquals(99L, notification.getReferenceId());
        assertEquals("SCHEDULE_UNAVAILABILITY_CONFLICT", notification.getSourceType());
        assertEquals("99:2", notification.getSourceKey());
        assertNull(notification.getResolvedAt());
    }

    @Test
    void shouldSendSystemMinistryNotificationUsingSameEligibilityRulesAsAdmin() {
        deactivatePersonMinistry(7L, MinistryType.MINISTER_OF_THE_WORD);
        deactivatePersonMinistry(9L, MinistryType.MINISTER_OF_THE_WORD);

        NotificationCreateResponseDTO response = notificationDeliveryService.sendSystemNotification(
                new SystemNotificationCommand(
                        NotificationAudience.MINISTRY,
                        NotificationCategory.GENERAL,
                        "Aviso sistema ministerio",
                        "Mensagem",
                        List.of(MinistryType.MINISTER_OF_THE_WORD),
                        null,
                        null, null, null, null
                ));

        assertEquals(1, response.getRecipientCount());
        assertTrue(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 8L));
        assertFalse(notificationRecipientRepository.existsByNotificationIdAndUserAccountId(response.getNotificationId(), 9L));
    }

    private NotificationCreateRequestDTO request(
            NotificationAudience audience, String title, String message, List<MinistryType> ministryTypes, List<Long> personIds
    ) {
        return new NotificationCreateRequestDTO(audience, title, message, ministryTypes, personIds);
    }

    private void deactivatePersonMinistry(Long personId, MinistryType ministryType) {
        PersonMinistry personMinistry = personMinistryRepository.findByPersonIdAndMinistryType(personId, ministryType).orElseThrow();
        personMinistry.deactivate();
        personMinistryRepository.saveAndFlush(personMinistry);
        entityManager.clear();
    }

    private void addPersonMinistry(Long personId, MinistryType ministryType) {
        Person person = personRepository.findById(personId).orElseThrow();
        personMinistryRepository.saveAndFlush(new PersonMinistry(person, ministryType));
        entityManager.clear();
    }

    private Person createPersonWithoutAccount(String name, boolean active) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(active);
        Person saved = personRepository.saveAndFlush(person);
        entityManager.clear();
        return saved;
    }

    private Fixture createPersonWithAccount(String name, List<String> authorities, boolean personActive, boolean accountEnabled) {
        Person person = new Person();
        person.setName(name);
        person.setPhoneNumber(uniquePhone());
        person.setBirthdayDate(BIRTHDAY);
        person.setActive(personActive);
        Person savedPerson = personRepository.saveAndFlush(person);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserAccount account = new UserAccount(savedPerson, savedPerson.getPhoneNumber(), "hash", now, now);
        UserAccount savedAccount = userAccountRepository.saveAndFlush(account);
        if (!accountEnabled) {
            savedAccount.disable(now);
            savedAccount = userAccountRepository.saveAndFlush(savedAccount);
        }
        for (String authority : authorities) {
            Role role = roleRepository.findByAuthority(authority).orElseThrow();
            userAccountRoleRepository.saveAndFlush(new UserAccountRole(savedAccount, role));
        }
        Fixture fixture = new Fixture(savedPerson.getId(), savedAccount.getId());
        entityManager.clear();
        return fixture;
    }

    private String uniquePhone() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "3497" + String.format("%07d", suffix);
    }

    private record Fixture(Long personId, Long accountId) {
    }
}
