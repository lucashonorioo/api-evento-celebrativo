package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.response.InvalidRecipientDTO;
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
import com.eventoscelebrativos.model.NotificationTargetMinistry;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.PersonMinistry;
import com.eventoscelebrativos.model.UserAccount;
import com.eventoscelebrativos.repository.NotificationRecipientRepository;
import com.eventoscelebrativos.repository.NotificationRepository;
import com.eventoscelebrativos.repository.NotificationTargetMinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.service.NotificationDeliveryService;
import com.eventoscelebrativos.service.SystemNotificationCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementacao unica de envio para ADMIN e SYSTEM: a resolucao de publico, elegibilidade, locks e
 * persistencia e compartilhada entre {@link #sendAdministrativeNotification} e
 * {@link #sendSystemNotification}, que apenas resolvem/validam o que e especifico de cada origin
 * (sender, category, reference, source) antes de delegar.
 */
@Service
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final Set<String> VALID_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_OPERATOR");
    private static final int MAX_REPORTED_INVALID_RECIPIENTS = 50;
    private static final int MAX_REFERENCE_TYPE_LENGTH = 50;
    private static final int MAX_SOURCE_TYPE_LENGTH = 50;
    private static final int MAX_SOURCE_KEY_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationTargetMinistryRepository notificationTargetMinistryRepository;
    private final PersonRepository personRepository;
    private final UserAccountRepository userAccountRepository;
    private final PersonMinistryRepository personMinistryRepository;
    private final AdminRoleMutexService adminRoleMutexService;
    private final Clock clock;

    public NotificationDeliveryServiceImpl(
            NotificationRepository notificationRepository,
            NotificationRecipientRepository notificationRecipientRepository,
            NotificationTargetMinistryRepository notificationTargetMinistryRepository,
            PersonRepository personRepository,
            UserAccountRepository userAccountRepository,
            PersonMinistryRepository personMinistryRepository,
            AdminRoleMutexService adminRoleMutexService,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.notificationTargetMinistryRepository = notificationTargetMinistryRepository;
        this.personRepository = personRepository;
        this.userAccountRepository = userAccountRepository;
        this.personMinistryRepository = personMinistryRepository;
        this.adminRoleMutexService = adminRoleMutexService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NotificationCreateResponseDTO sendAdministrativeNotification(
            Long senderAccountId,
            NotificationCreateRequestDTO request
    ) {
        NotificationAudience audience = request.getAudience();
        if (audience == null) {
            throw new BadRequestException("O campo audience é obrigatório");
        }
        String title = NotificationContentValidator.normalizeTitle(request.getTitle());
        String message = NotificationContentValidator.normalizeMessage(request.getMessage());
        List<MinistryType> ministryTypes = dedupeMinistryTypes(request.getMinistryTypes());
        List<Long> personIds = dedupePersonIds(request.getPersonIds());
        validateAudienceFields(audience, ministryTypes, personIds);

        RequestContext context = new RequestContext(
                NotificationOrigin.ADMIN, senderAccountId, NotificationCategory.GENERAL,
                title, message, null, null, null, null, null
        );
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        return audience == NotificationAudience.DIRECT
                ? deliverDirect(context, personIds, currentSecond)
                : deliverBroadcast(context, audience, ministryTypes, currentSecond);
    }

    @Override
    @Transactional
    public NotificationCreateResponseDTO sendSystemNotification(SystemNotificationCommand command) {
        LocalDateTime currentSecond = LocalDateTime.now(clock).withNano(0);
        return doSendSystem(command, null, currentSecond);
    }

    @Override
    @Transactional
    public NotificationCreateResponseDTO sendSystemNotificationForConflictReconciliation(
            SystemNotificationCommand command,
            String activeSourceKey,
            LocalDateTime currentSecond
    ) {
        Objects.requireNonNull(activeSourceKey, "activeSourceKey é obrigatório");
        return doSendSystem(command, activeSourceKey, currentSecond);
    }

    private NotificationCreateResponseDTO doSendSystem(
            SystemNotificationCommand command,
            String activeSourceKey,
            LocalDateTime currentSecond
    ) {
        String title = NotificationContentValidator.normalizeTitle(command.title());
        String message = NotificationContentValidator.normalizeMessage(command.message());
        List<MinistryType> ministryTypes = dedupeMinistryTypes(command.ministryTypes());
        List<Long> personIds = dedupePersonIds(command.personIds());
        validateAudienceFields(command.audience(), ministryTypes, personIds);
        validateSystemMetadata(command);

        RequestContext context = new RequestContext(
                NotificationOrigin.SYSTEM, null, command.category(),
                title, message, command.referenceType(), command.referenceId(),
                command.sourceType(), command.sourceKey(), activeSourceKey
        );
        return command.audience() == NotificationAudience.DIRECT
                ? deliverDirect(context, personIds, currentSecond)
                : deliverBroadcast(context, command.audience(), ministryTypes, currentSecond);
    }

    private NotificationCreateResponseDTO deliverBroadcast(
            RequestContext context,
            NotificationAudience audience,
            List<MinistryType> ministryTypes,
            LocalDateTime currentSecond
    ) {
        adminRoleMutexService.lockAdminRole();

        Map<Long, Long> personIdByAccountId = new LinkedHashMap<>();

        if (audience == NotificationAudience.MINISTRY) {
            Map<MinistryType, Set<Long>> accountsByMinistryType = new EnumMap<>(MinistryType.class);
            for (MinistryType type : ministryTypes) {
                accountsByMinistryType.put(type, new LinkedHashSet<>());
            }
            for (UserAccountRepository.EligibleMinistryAccount row
                    : userAccountRepository.findEligibleAccountsByMinistryTypes(ministryTypes)) {
                personIdByAccountId.put(row.getAccountId(), row.getPersonId());
                accountsByMinistryType.get(row.getMinistryType()).add(row.getAccountId());
            }
            requireMinistryCoverage(ministryTypes, accountsByMinistryType);
        } else {
            String requiredAuthority = audience == NotificationAudience.ADMIN ? ROLE_ADMIN : null;
            for (UserAccountRepository.EligibleAccount row : userAccountRepository.findEligibleAccounts(requiredAuthority)) {
                personIdByAccountId.put(row.getAccountId(), row.getPersonId());
            }
        }

        if (context.origin() == NotificationOrigin.ADMIN) {
            UserAccount senderPreview = userAccountRepository.findById(context.senderAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conta de usuário", context.senderAccountId()));
            personIdByAccountId.putIfAbsent(context.senderAccountId(), senderPreview.getPerson().getId());
        }

        List<Long> personIdsToLock = personIdByAccountId.values().stream().distinct().sorted().toList();
        Map<Long, Person> lockedPersons = lockPersons(personIdsToLock);

        List<Long> accountIdsToLock = personIdByAccountId.keySet().stream().sorted().toList();
        Map<Long, UserAccount> lockedAccounts = lockAccounts(accountIdsToLock);

        Map<Long, List<PersonMinistry>> lockedMinistriesByPersonId = new LinkedHashMap<>();
        if (audience == NotificationAudience.MINISTRY) {
            for (Long personId : personIdsToLock) {
                lockedMinistriesByPersonId.put(
                        personId,
                        personMinistryRepository.findByPersonIdAndMinistryTypeInForUpdate(personId, ministryTypes)
                );
            }
        }

        UserAccount sender = null;
        if (context.origin() == NotificationOrigin.ADMIN) {
            sender = lockedAccounts.get(context.senderAccountId());
            requireStillEligibleAdmin(sender);
        }

        List<Long> finalAccountIds = audience == NotificationAudience.MINISTRY
                ? resolveMinistryRecipients(ministryTypes, personIdByAccountId, lockedAccounts, lockedPersons, lockedMinistriesByPersonId)
                : resolveSimpleRecipients(audience, personIdByAccountId, lockedAccounts, lockedPersons);

        List<MinistryType> ministryTypesToPersist = audience == NotificationAudience.MINISTRY ? ministryTypes : List.of();
        return persistAndRespond(context, audience, sender, finalAccountIds, lockedAccounts, ministryTypesToPersist, currentSecond);
    }

    private List<Long> resolveMinistryRecipients(
            List<MinistryType> ministryTypes,
            Map<Long, Long> personIdByAccountId,
            Map<Long, UserAccount> lockedAccounts,
            Map<Long, Person> lockedPersons,
            Map<Long, List<PersonMinistry>> lockedMinistriesByPersonId
    ) {
        Map<MinistryType, List<Long>> stillCovered = new EnumMap<>(MinistryType.class);
        for (MinistryType type : ministryTypes) {
            stillCovered.put(type, new ArrayList<>());
        }
        Set<Long> includedAccountIds = new LinkedHashSet<>();
        for (Map.Entry<Long, Long> entry : personIdByAccountId.entrySet()) {
            Long accountId = entry.getKey();
            Long personId = entry.getValue();
            UserAccount account = lockedAccounts.get(accountId);
            Person person = lockedPersons.get(personId);
            if (!isEligibleAccount(account, person)) {
                continue;
            }
            List<PersonMinistry> ministries = lockedMinistriesByPersonId.getOrDefault(personId, List.of());
            for (PersonMinistry personMinistry : ministries) {
                if (Boolean.TRUE.equals(personMinistry.getActive()) && ministryTypes.contains(personMinistry.getMinistryType())) {
                    stillCovered.get(personMinistry.getMinistryType()).add(accountId);
                    includedAccountIds.add(accountId);
                }
            }
        }
        requireMinistryCoverage(ministryTypes, mapToSets(stillCovered));
        return new ArrayList<>(includedAccountIds);
    }

    private void requireMinistryCoverage(List<MinistryType> ministryTypes, Map<MinistryType, Set<Long>> accountsByMinistryType) {
        List<MinistryType> uncovered = ministryTypes.stream()
                .filter(type -> accountsByMinistryType.getOrDefault(type, Set.of()).isEmpty())
                .toList();
        if (!uncovered.isEmpty()) {
            throw new LifecycleConflictException(
                    "Os seguintes ministérios não possuem nenhum destinatário elegível: " + uncovered,
                    "NOTIFICATION_MINISTRY_WITHOUT_RECIPIENTS"
            );
        }
    }

    private Map<MinistryType, Set<Long>> mapToSets(Map<MinistryType, List<Long>> source) {
        Map<MinistryType, Set<Long>> result = new EnumMap<>(MinistryType.class);
        source.forEach((type, ids) -> result.put(type, new LinkedHashSet<>(ids)));
        return result;
    }

    private List<Long> resolveSimpleRecipients(
            NotificationAudience audience,
            Map<Long, Long> personIdByAccountId,
            Map<Long, UserAccount> lockedAccounts,
            Map<Long, Person> lockedPersons
    ) {
        List<Long> finalAccountIds = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : personIdByAccountId.entrySet()) {
            Long accountId = entry.getKey();
            Long personId = entry.getValue();
            UserAccount account = lockedAccounts.get(accountId);
            Person person = lockedPersons.get(personId);
            boolean audienceSpecificallyValid = audience != NotificationAudience.ADMIN || hasRole(account, ROLE_ADMIN);
            if (isEligibleAccount(account, person) && audienceSpecificallyValid) {
                finalAccountIds.add(accountId);
            }
        }
        if (finalAccountIds.isEmpty()) {
            throw noEligibleRecipients();
        }
        return finalAccountIds;
    }

    private NotificationCreateResponseDTO deliverDirect(
            RequestContext context,
            List<Long> personIds,
            LocalDateTime currentSecond
    ) {
        adminRoleMutexService.lockAdminRole();

        List<Long> sortedPersonIds = personIds.stream().sorted().toList();

        Long senderAccountId = context.senderAccountId();
        Long senderPersonId = null;
        if (context.origin() == NotificationOrigin.ADMIN) {
            UserAccount senderPreview = userAccountRepository.findById(senderAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta de usuário", senderAccountId));
            senderPersonId = senderPreview.getPerson().getId();
        }

        Set<Long> personIdsToLock = new LinkedHashSet<>(sortedPersonIds);
        if (senderPersonId != null) {
            personIdsToLock.add(senderPersonId);
        }
        List<Long> sortedPersonIdsToLock = personIdsToLock.stream().sorted().toList();
        Map<Long, Person> lockedPersons = lockPersons(sortedPersonIdsToLock);

        Map<Long, Long> accountIdByPersonId = new LinkedHashMap<>();
        for (Long personId : sortedPersonIdsToLock) {
            if (!lockedPersons.containsKey(personId)) {
                continue;
            }
            userAccountRepository.findByPersonId(personId)
                    .ifPresent(account -> accountIdByPersonId.put(personId, account.getId()));
        }

        List<Long> accountIdsToLock = accountIdByPersonId.values().stream().distinct().sorted().toList();
        Map<Long, UserAccount> lockedAccounts = lockAccounts(accountIdsToLock);

        List<InvalidRecipientDTO> invalidRecipients = new ArrayList<>();
        List<Long> finalAccountIds = new ArrayList<>();
        for (Long personId : sortedPersonIds) {
            Person person = lockedPersons.get(personId);
            if (person == null) {
                invalidRecipients.add(new InvalidRecipientDTO(personId, InvalidRecipientReason.PERSON_NOT_FOUND));
                continue;
            }
            Long accountId = accountIdByPersonId.get(personId);
            UserAccount account = accountId == null ? null : lockedAccounts.get(accountId);
            if (account == null) {
                invalidRecipients.add(new InvalidRecipientDTO(personId, InvalidRecipientReason.USER_ACCOUNT_NOT_FOUND));
                continue;
            }
            if (!account.isEnabled()) {
                invalidRecipients.add(new InvalidRecipientDTO(personId, InvalidRecipientReason.USER_ACCOUNT_DISABLED));
                continue;
            }
            if (!person.isActive()) {
                invalidRecipients.add(new InvalidRecipientDTO(personId, InvalidRecipientReason.PERSON_INACTIVE));
                continue;
            }
            if (!hasValidRole(account)) {
                invalidRecipients.add(new InvalidRecipientDTO(personId, InvalidRecipientReason.USER_ACCOUNT_ROLE_INVALID));
                continue;
            }
            finalAccountIds.add(accountId);
        }

        if (!invalidRecipients.isEmpty()) {
            throw new NotificationInvalidRecipientsException(cap(invalidRecipients));
        }

        UserAccount sender = null;
        if (context.origin() == NotificationOrigin.ADMIN) {
            sender = lockedAccounts.get(senderAccountId);
            requireStillEligibleAdmin(sender);
        }

        return persistAndRespond(context, NotificationAudience.DIRECT, sender, finalAccountIds, lockedAccounts, List.of(), currentSecond);
    }

    private NotificationCreateResponseDTO persistAndRespond(
            RequestContext context,
            NotificationAudience audience,
            UserAccount sender,
            List<Long> finalAccountIds,
            Map<Long, UserAccount> lockedAccounts,
            List<MinistryType> ministryTypesToPersist,
            LocalDateTime currentSecond
    ) {
        Notification notification;
        if (context.origin() == NotificationOrigin.ADMIN) {
            notification = Notification.administrative(
                    sender, sender.getPerson().getName(), audience, context.title(), context.message(), currentSecond);
        } else if (context.activeSourceKey() != null) {
            notification = Notification.scheduleConflict(
                    audience, context.title(), context.message(), context.referenceType(), context.referenceId(),
                    context.sourceType(), context.sourceKey(), context.activeSourceKey(), currentSecond);
        } else {
            notification = Notification.system(
                    audience, context.category(), context.title(), context.message(),
                    context.referenceType(), context.referenceId(), context.sourceType(), context.sourceKey(),
                    currentSecond);
        }
        notification = notificationRepository.save(notification);

        for (MinistryType type : ministryTypesToPersist) {
            notificationTargetMinistryRepository.save(new NotificationTargetMinistry(notification, type));
        }

        for (Long accountId : finalAccountIds) {
            UserAccount account = lockedAccounts.get(accountId);
            notificationRecipientRepository.save(
                    new NotificationRecipient(notification, account, account.getPerson().getName())
            );
        }

        return new NotificationCreateResponseDTO(
                notification.getId(), context.origin(), audience, context.title(), currentSecond, finalAccountIds.size()
        );
    }

    private void requireStillEligibleAdmin(UserAccount sender) {
        if (sender == null || !isEligibleAccount(sender, sender.getPerson()) || !hasRole(sender, ROLE_ADMIN)) {
            throw new LifecycleConflictException(
                    "O remetente não é mais um administrador ativo e habilitado.",
                    "NOTIFICATION_SENDER_NOT_ELIGIBLE"
            );
        }
    }

    private LifecycleConflictException noEligibleRecipients() {
        return new LifecycleConflictException(
                "Não há destinatários elegíveis para o público selecionado.",
                "NOTIFICATION_NO_ELIGIBLE_RECIPIENTS"
        );
    }

    private boolean isEligibleAccount(UserAccount account, Person person) {
        return account != null && person != null
                && account.isEnabled() && person.isActive()
                && hasValidRole(account);
    }

    private boolean hasValidRole(UserAccount account) {
        return account.getRoles().size() == 1
                && VALID_AUTHORITIES.contains(account.getRoles().iterator().next().getRole().getAuthority());
    }

    private boolean hasRole(UserAccount account, String authority) {
        return account != null && account.getRoles().stream()
                .anyMatch(userAccountRole -> authority.equals(userAccountRole.getRole().getAuthority()));
    }

    private Map<Long, Person> lockPersons(List<Long> personIds) {
        Map<Long, Person> result = new LinkedHashMap<>();
        for (Long personId : personIds) {
            personRepository.findByIdForUpdate(personId).ifPresent(person -> result.put(personId, person));
        }
        return result;
    }

    private Map<Long, UserAccount> lockAccounts(List<Long> accountIds) {
        Map<Long, UserAccount> result = new LinkedHashMap<>();
        for (Long accountId : accountIds) {
            userAccountRepository.findByIdForUpdate(accountId).ifPresent(account -> result.put(accountId, account));
        }
        return result;
    }

    private List<InvalidRecipientDTO> cap(List<InvalidRecipientDTO> invalidRecipients) {
        if (invalidRecipients.size() <= MAX_REPORTED_INVALID_RECIPIENTS) {
            return invalidRecipients;
        }
        return new ArrayList<>(invalidRecipients.subList(0, MAX_REPORTED_INVALID_RECIPIENTS));
    }

    private void validateSystemMetadata(SystemNotificationCommand command) {
        boolean hasReferenceType = command.referenceType() != null && !command.referenceType().isBlank();
        boolean hasReferenceId = command.referenceId() != null;
        if (hasReferenceType != hasReferenceId) {
            throw new BadRequestException(
                    "referenceType e referenceId devem ser ambos ausentes ou ambos presentes.",
                    "NOTIFICATION_REFERENCE_PAIR_INVALID");
        }
        if (hasReferenceType && command.referenceType().length() > MAX_REFERENCE_TYPE_LENGTH) {
            throw new BadRequestException(
                    "referenceType deve ter no máximo " + MAX_REFERENCE_TYPE_LENGTH + " caracteres.",
                    "NOTIFICATION_REFERENCE_TYPE_TOO_LONG");
        }
        if (hasReferenceId && command.referenceId() <= 0) {
            throw new BadRequestException("referenceId deve ser positivo.", "NOTIFICATION_REFERENCE_ID_INVALID");
        }

        boolean hasSourceType = command.sourceType() != null && !command.sourceType().isBlank();
        boolean hasSourceKey = command.sourceKey() != null && !command.sourceKey().isBlank();
        if (hasSourceType != hasSourceKey) {
            throw new BadRequestException(
                    "sourceType e sourceKey devem ser ambos ausentes ou ambos presentes.",
                    "NOTIFICATION_SOURCE_PAIR_INVALID");
        }
        if (hasSourceType && command.sourceType().length() > MAX_SOURCE_TYPE_LENGTH) {
            throw new BadRequestException(
                    "sourceType deve ter no máximo " + MAX_SOURCE_TYPE_LENGTH + " caracteres.",
                    "NOTIFICATION_SOURCE_TYPE_TOO_LONG");
        }
        if (hasSourceKey && command.sourceKey().length() > MAX_SOURCE_KEY_LENGTH) {
            throw new BadRequestException(
                    "sourceKey deve ter no máximo " + MAX_SOURCE_KEY_LENGTH + " caracteres.",
                    "NOTIFICATION_SOURCE_KEY_TOO_LONG");
        }
    }

    private void validateAudienceFields(NotificationAudience audience, List<MinistryType> ministryTypes, List<Long> personIds) {
        boolean hasMinistryTypes = !ministryTypes.isEmpty();
        boolean hasPersonIds = !personIds.isEmpty();
        boolean invalid = switch (audience) {
            case GLOBAL, ADMIN -> hasMinistryTypes || hasPersonIds;
            case MINISTRY -> !hasMinistryTypes || hasPersonIds;
            case DIRECT -> hasMinistryTypes || !hasPersonIds;
        };
        if (invalid) {
            throw new BadRequestException(
                    "Combinação de audience, ministryTypes e personIds inválida para " + audience + ".",
                    "NOTIFICATION_AUDIENCE_FIELDS_INVALID"
            );
        }
    }

    private List<MinistryType> dedupeMinistryTypes(List<MinistryType> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<Long> dedupePersonIds(List<Long> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record RequestContext(
            NotificationOrigin origin,
            Long senderAccountId,
            NotificationCategory category,
            String title,
            String message,
            String referenceType,
            Long referenceId,
            String sourceType,
            String sourceKey,
            String activeSourceKey
    ) {
    }
}
