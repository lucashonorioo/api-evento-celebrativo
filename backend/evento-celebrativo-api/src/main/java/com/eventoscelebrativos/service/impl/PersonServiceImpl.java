package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonAdminUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.MinistrySummaryDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistryMembershipResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CurrentUserProfileMapper;
import com.eventoscelebrativos.mapper.PersonAdminMapper;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.UserAccountRepository;
import com.eventoscelebrativos.repository.UserAccountRoleRepository;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.ParticipationResponseSnapshot;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import com.eventoscelebrativos.service.PersonMinistryCatalogSyncResult;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryMembershipView;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.service.UserAccountLifecycleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonServiceImpl implements PersonService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    private static final Set<String> ALLOWED_ROLES = Set.of(ROLE_ADMIN, ROLE_OPERATOR);

    private final PersonRepository personRepository;
    private final MinistryRepository ministryRepository;
    private final PersonAdminMapper personAdminMapper;
    private final PersonRoleUpdateMapper personRoleUpdateMapper;
    private final CurrentUserProfileMapper currentUserProfileMapper;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final EventParticipationResponseService eventParticipationResponseService;
    private final UserAccountLifecycleService userAccountLifecycleService;
    private final UserAccountRoleRepository userAccountRoleRepository;
    private final UserAccountRepository userAccountRepository;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public PersonServiceImpl(
            PersonRepository personRepository,
            MinistryRepository ministryRepository,
            PersonAdminMapper personAdminMapper,
            PersonRoleUpdateMapper personRoleUpdateMapper,
            CurrentUserProfileMapper currentUserProfileMapper,
            PersonMinistryReadService personMinistryReadService,
            PersonMinistryCommandService personMinistryCommandService,
            EventAssignmentRepository eventAssignmentRepository,
            EventParticipationResponseService eventParticipationResponseService,
            UserAccountLifecycleService userAccountLifecycleService,
            UserAccountRoleRepository userAccountRoleRepository,
            UserAccountRepository userAccountRepository,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.personRepository = personRepository;
        this.ministryRepository = ministryRepository;
        this.personAdminMapper = personAdminMapper;
        this.personRoleUpdateMapper = personRoleUpdateMapper;
        this.currentUserProfileMapper = currentUserProfileMapper;
        this.personMinistryReadService = personMinistryReadService;
        this.personMinistryCommandService = personMinistryCommandService;
        this.eventAssignmentRepository = eventAssignmentRepository;
        this.eventParticipationResponseService = eventParticipationResponseService;
        this.userAccountLifecycleService = userAccountLifecycleService;
        this.userAccountRoleRepository = userAccountRoleRepository;
        this.userAccountRepository = userAccountRepository;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonAdminResponseDTO> findPeople(
            String name,
            String phoneNumber,
            Long ministryId,
            String role,
            Boolean personActive,
            Boolean accountExists,
            Boolean accountEnabled,
            int page,
            int size
    ) {
        String normalizedName = normalizeOptionalFilter(name);
        String normalizedPhoneNumber = normalizeOptionalFilter(phoneNumber);
        Long normalizedMinistryId = normalizeMinistryIdFilter(ministryId);
        String normalizedRole = normalizeRoleFilter(role);
        validateAccountFilterCombination(accountExists, accountEnabled, normalizedRole);
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(page, size);
        Page<Long> idPage = personRepository.findAdminPageIds(
                normalizedName,
                normalizedPhoneNumber,
                normalizedMinistryId,
                normalizedRole,
                personActive,
                accountExists,
                accountEnabled,
                pageable
        );

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, idPage.getTotalElements());
        }

        List<Long> ids = idPage.getContent();
        Map<Long, Person> peopleById = personRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Map<Long, List<PersonMinistryMembershipView>> activeMinistriesById =
                personMinistryReadService.findActiveMinistryMembershipsByPersonIds(ids);
        Map<Long, List<String>> rolesById = userAccountRoleRepository.findRoleAuthoritiesByPersonIdsGroupedByPerson(ids);
        Map<Long, UserAccountRepository.AccountState> accountStatesById =
                userAccountRepository.findAccountStatesByPersonIdInGroupedByPerson(ids);

        List<PersonAdminResponseDTO> content = ids.stream()
                .map(peopleById::get)
                .map(person -> toAdminResponseDTO(
                        person,
                        activeMinistriesById.getOrDefault(person.getId(), List.of()),
                        rolesById.getOrDefault(person.getId(), List.of()),
                        accountStatesById.get(person.getId())
                ))
                .toList();

        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PersonAdminResponseDTO findPersonById(Long id) {
        validateId(id);
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));
        return toAdminResponseDTO(
                person,
                activeMinistryMembershipsForPerson(id),
                rolesForPerson(id),
                accountStateForPerson(id)
        );
    }

    @Override
    @Transactional
    public PersonAdminResponseDTO updatePersonAdmin(Long id, PersonAdminUpdateRequestDTO requestDTO) {
        validateId(id);
        if (requestDTO == null) {
            throw new BadRequestException("Os dados de atualização são obrigatórios");
        }
        requestDTO.rejectForbiddenFields();
        Person person = personRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));
        Person saved = personCadastralUpdateService.updateCadastral(
                person,
                requestDTO.getName(),
                requestDTO.getPhoneNumber(),
                requestDTO.getBirthdayDate()
        );
        return toAdminResponseDTO(
                saved,
                activeMinistryMembershipsForPerson(saved.getId()),
                rolesForPerson(saved.getId()),
                accountStateForPerson(saved.getId())
        );
    }

    private PersonAdminResponseDTO toAdminResponseDTO(
            Person person,
            List<PersonMinistryMembershipView> ministries,
            List<String> roles,
            UserAccountRepository.AccountState accountState
    ) {
        PersonAdminResponseDTO dto = personAdminMapper.toDto(person);
        dto.setMinistries(toMinistrySummaries(ministries));
        dto.setRoles(sortedRoles(roles));
        dto.setAccountExists(accountState != null);
        dto.setAccountEnabled(accountState == null ? null : accountState.isEnabled());
        dto.setUsername(accountState == null ? null : accountState.getUsername());
        return dto;
    }

    private UserAccountRepository.AccountState accountStateForPerson(Long personId) {
        return userAccountRepository.findAccountStatesByPersonIdInGroupedByPerson(List.of(personId)).get(personId);
    }

    private void validateAccountFilterCombination(Boolean accountExists, Boolean accountEnabled, String role) {
        if (Boolean.FALSE.equals(accountExists) && (accountEnabled != null || role != null)) {
            throw new BadRequestException(
                    "accountExists=false não pode ser combinado com accountEnabled ou role",
                    "PERSON_ADMIN_FILTERS_INVALID"
            );
        }
    }

    @Override
    @Transactional
    public PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO) {
        validateId(id);
        String requestedRole = normalizeRequiredRole(requestDTO.getRole());
        Person savedPerson = userAccountLifecycleService.updateRole(id, requestedRole);
        PersonRoleUpdateResponseDTO dto = personRoleUpdateMapper.toDto(savedPerson);
        return new PersonRoleUpdateResponseDTO(
                dto.getId(),
                dto.getName(),
                dto.getPhoneNumber(),
                toMinistrySummaries(activeMinistryMembershipsForPerson(savedPerson.getId())),
                sortedRoles(rolesForPerson(savedPerson.getId()))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PersonMinistriesResponseDTO findPersonMinistries(Long id) {
        validateId(id);
        if (!personRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pessoa", id);
        }
        return toMinistriesResponseDTO(id, activeMinistryMembershipsForPerson(id));
    }

    @Override
    @Transactional
    public PersonMinistriesResponseDTO updatePersonMinistries(Long id, PersonMinistriesUpdateRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new BusinessException("O conjunto de ministerios e obrigatorio");
        }
        List<Long> desiredMinistryIds = parseDesiredMinistryIds(requestDTO.getMinistryIds());
        PersonMinistryCatalogSyncResult result = personMinistryCommandService.syncMinistriesById(id, desiredMinistryIds);
        return toMinistriesResponseDTO(
                result.person().getId(),
                activeMinistryMembershipsForPerson(result.person().getId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserProfileResponseDTO getCurrentUserProfile(Long personId) {
        Person person = findAuthenticatedPerson(personId);
        return toCurrentUserProfileDTO(person);
    }

    @Override
    @Transactional
    public CurrentUserProfileResponseDTO updateCurrentUserProfile(Long personId, CurrentUserProfileUpdateRequestDTO requestDTO) {
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
        Person savedPerson = personCadastralUpdateService.updateCadastral(
                person,
                requestDTO.getName(),
                person.getPhoneNumber(),
                requestDTO.getBirthdayDate()
        );
        return toCurrentUserProfileDTO(savedPerson);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CurrentUserScheduleResponseDTO> findCurrentUserSchedules(
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        validateScheduleQuery(startDate, endDate, page, size);
        Person person = findAuthenticatedPerson(personId);

        PageRequest pageable = PageRequest.of(page, size);
        Page<PersonScheduleEventProjection> eventPage = eventAssignmentRepository.findScheduleEventsByPersonId(
                person.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                pageable
        );

        if (eventPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, eventPage.getTotalElements());
        }

        List<Long> eventIds = eventPage.getContent().stream()
                .map(PersonScheduleEventProjection::getEventId)
                .toList();

        Map<Long, EnumSet<EventAssignmentType>> assignmentsByEvent = eventAssignmentRepository
                .findAssignmentTypesByPersonIdAndEventIdIn(person.getId(), eventIds).stream()
                .collect(Collectors.groupingBy(
                        PersonScheduleAssignmentProjection::getEventId,
                        Collectors.mapping(
                                projection -> EventAssignmentType.valueOf(projection.getAssignmentType()),
                                Collectors.toCollection(() -> EnumSet.noneOf(EventAssignmentType.class))
                        )
                ));

        Map<Long, ParticipationResponseSnapshot> participationByEvent = eventParticipationResponseService
                .findByPersonIdAndEventIds(person.getId(), eventIds);

        List<CurrentUserScheduleResponseDTO> content = eventPage.getContent().stream()
                .map(event -> toCurrentUserScheduleDTO(
                        event,
                        assignmentsByEvent.getOrDefault(event.getEventId(), EnumSet.noneOf(EventAssignmentType.class)),
                        participationByEvent.get(event.getEventId())
                ))
                .toList();

        return new PageImpl<>(content, pageable, eventPage.getTotalElements());
    }

    private void validateScheduleQuery(LocalDate startDate, LocalDate endDate, int page, int size) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("As datas de início e fim são obrigatórias");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("A data inicial não pode ser posterior à data final");
        }
        validatePage(page, size);
    }

    private CurrentUserScheduleResponseDTO toCurrentUserScheduleDTO(
            PersonScheduleEventProjection event,
            EnumSet<EventAssignmentType> assignmentTypes,
            ParticipationResponseSnapshot participation
    ) {
        ParticipationStatus participationStatus = participation != null
                ? participation.status()
                : ParticipationStatus.PENDING;
        String declineReason = participation != null ? participation.declineReason() : null;
        LocalDateTime respondedAt = participation != null ? participation.respondedAt() : null;

        return new CurrentUserScheduleResponseDTO(
                event.getEventId(),
                event.getEventName(),
                event.getStartAt(),
                event.getEndAt(),
                event.getMassOrCelebration(),
                event.getLocationId(),
                event.getLocationName(),
                List.copyOf(assignmentTypes),
                participationStatus,
                declineReason,
                respondedAt
        );
    }

    private Person findAuthenticatedPerson(Long personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", personId));
    }

    private CurrentUserProfileResponseDTO toCurrentUserProfileDTO(Person person) {
        CurrentUserProfileResponseDTO dto = currentUserProfileMapper.toDto(person);
        dto.setMinistries(sortedMinistries(activeMinistriesForPerson(person.getId())));
        dto.setRoles(sortedRoles(rolesForPerson(person.getId())));
        return dto;
    }

    private PersonMinistriesResponseDTO toMinistriesResponseDTO(Long id, List<PersonMinistryMembershipView> ministries) {
        return new PersonMinistriesResponseDTO(
                id,
                ministries.stream()
                        .sorted(Comparator
                                .comparing(PersonMinistryMembershipView::name)
                                .thenComparing(PersonMinistryMembershipView::id))
                        .map(ministry -> new PersonMinistryMembershipResponseDTO(
                                ministry.id(),
                                ministry.name(),
                                ministry.coordinator()
                        ))
                        .toList()
        );
    }

    private List<MinistrySummaryDTO> toMinistrySummaries(List<PersonMinistryMembershipView> ministries) {
        if (ministries == null || ministries.isEmpty()) {
            return List.of();
        }
        return ministries.stream()
                .sorted(Comparator
                        .comparing(PersonMinistryMembershipView::name)
                        .thenComparing(PersonMinistryMembershipView::id))
                .map(ministry -> new MinistrySummaryDTO(ministry.id(), ministry.name()))
                .toList();
    }

    private List<MinistryType> sortedMinistries(Set<MinistryType> ministries) {
        if (ministries == null || ministries.isEmpty()) {
            return List.of();
        }
        return ministries.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private Set<MinistryType> activeMinistriesForPerson(Long personId) {
        return personMinistryReadService
                .findActiveMinistriesByPersonIds(List.of(personId))
                .getOrDefault(personId, Set.of());
    }

    private List<PersonMinistryMembershipView> activeMinistryMembershipsForPerson(Long personId) {
        return personMinistryReadService
                .findActiveMinistryMembershipsByPersonIds(List.of(personId))
                .getOrDefault(personId, List.of());
    }

    private List<String> rolesForPerson(Long personId) {
        return userAccountRoleRepository
                .findRoleAuthoritiesByPersonIdsGroupedByPerson(List.of(personId))
                .getOrDefault(personId, List.of());
    }

    private List<String> sortedRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream().sorted().toList();
    }

    private List<Long> parseDesiredMinistryIds(List<Long> rawMinistryIds) {
        if (rawMinistryIds == null) {
            throw new BusinessException("O conjunto de ministerios e obrigatorio");
        }
        Set<Long> desired = new LinkedHashSet<>();
        for (Long ministryId : rawMinistryIds) {
            if (ministryId == null || ministryId <= 0) {
                throw new BadRequestException("Id de ministerio invalido");
            }
            if (!desired.add(ministryId)) {
                throw new BusinessException("Ministerio duplicado no request: " + ministryId);
            }
        }
        return List.copyOf(desired);
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("O Id deve ser positivo e nao nulo");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("O numero da pagina deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da pagina deve ser maior que zero e menor ou igual a 100");
        }
    }

    private String normalizeOptionalFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long normalizeMinistryIdFilter(Long ministryId) {
        if (ministryId == null) {
            return null;
        }
        if (ministryId <= 0) {
            throw new BadRequestException("Id de ministerio invalido");
        }
        if (!ministryRepository.existsById(ministryId)) {
            throw new ResourceNotFoundException("Ministerio", ministryId);
        }
        return ministryId;
    }

    private String normalizeRoleFilter(String role) {
        String normalized = normalizeOptionalFilter(role);
        if (normalized != null && !ALLOWED_ROLES.contains(normalized)) {
            throw new BadRequestException("Perfil de acesso invalido");
        }
        return normalized;
    }

    private String normalizeRequiredRole(String role) {
        String normalized = normalizeOptionalFilter(role);
        if (normalized == null || !ALLOWED_ROLES.contains(normalized)) {
            throw new BadRequestException("Perfil de acesso invalido");
        }
        return normalized;
    }

}
