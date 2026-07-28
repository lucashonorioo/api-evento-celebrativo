package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
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
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.projection.PersonScheduleAssignmentProjection;
import com.eventoscelebrativos.projection.PersonScheduleEventProjection;
import com.eventoscelebrativos.repository.EventAssignmentRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.PersonMinistrySyncResult;
import com.eventoscelebrativos.service.PersonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final RoleRepository roleRepository;
    private final PersonAdminMapper personAdminMapper;
    private final PersonRoleUpdateMapper personRoleUpdateMapper;
    private final CurrentUserProfileMapper currentUserProfileMapper;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final EventAssignmentRepository eventAssignmentRepository;

    public PersonServiceImpl(
            PersonRepository personRepository,
            RoleRepository roleRepository,
            PersonAdminMapper personAdminMapper,
            PersonRoleUpdateMapper personRoleUpdateMapper,
            CurrentUserProfileMapper currentUserProfileMapper,
            PersonMinistryReadService personMinistryReadService,
            PersonMinistryCommandService personMinistryCommandService,
            EventAssignmentRepository eventAssignmentRepository
    ) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personAdminMapper = personAdminMapper;
        this.personRoleUpdateMapper = personRoleUpdateMapper;
        this.currentUserProfileMapper = currentUserProfileMapper;
        this.personMinistryReadService = personMinistryReadService;
        this.personMinistryCommandService = personMinistryCommandService;
        this.eventAssignmentRepository = eventAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonAdminResponseDTO> findPeople(
            String name,
            String phoneNumber,
            String ministry,
            String role,
            int page,
            int size
    ) {
        String normalizedName = normalizeOptionalFilter(name);
        String normalizedPhoneNumber = normalizeOptionalFilter(phoneNumber);
        MinistryType ministryType = normalizeMinistryFilter(ministry);
        String normalizedRole = normalizeRoleFilter(role);
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(page, size);
        Page<Long> idPage = personRepository.findAdminPageIds(
                normalizedName,
                normalizedPhoneNumber,
                ministryType,
                normalizedRole,
                pageable
        );

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, idPage.getTotalElements());
        }

        List<Long> ids = idPage.getContent();
        Map<Long, Person> peopleById = personRepository.findAllByIdInWithRoles(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Map<Long, Set<MinistryType>> activeMinistriesById = personMinistryReadService.findActiveMinistriesByPersonIds(ids);

        List<PersonAdminResponseDTO> content = ids.stream()
                .map(peopleById::get)
                .map(person -> {
                    PersonAdminResponseDTO dto = personAdminMapper.toDto(person);
                    dto.setMinistries(sortedMinistries(activeMinistriesById.getOrDefault(person.getId(), Set.of())));
                    return dto;
                })
                .toList();

        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PersonAdminResponseDTO findPersonById(Long id) {
        validateId(id);
        Person person = personRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));
        PersonAdminResponseDTO dto = personAdminMapper.toDto(person);
        dto.setMinistries(sortedMinistries(activeMinistriesForPerson(id)));
        return dto;
    }

    @Override
    @Transactional
    public PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO) {
        validateId(id);
        String requestedRole = normalizeRequiredRole(requestDTO.getRole());

        Person person = personRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));

        Role role = roleRepository.findByAuthority(requestedRole)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", requestedRole));

        if (removesAdminRole(person, requestedRole)) {
            validateSelfAdminDemotion(person);
            validateLastAdministrator();
        }

        person.getRoles().clear();
        person.addRole(role);

        Person savedPerson = personRepository.save(person);
        PersonRoleUpdateResponseDTO dto = personRoleUpdateMapper.toDto(savedPerson);
        return new PersonRoleUpdateResponseDTO(
                dto.getId(),
                dto.getName(),
                dto.getPhoneNumber(),
                sortedMinistries(activeMinistriesForPerson(savedPerson.getId())),
                dto.getRoles()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PersonMinistriesResponseDTO findPersonMinistries(Long id) {
        validateId(id);
        if (!personRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pessoa", id);
        }
        Set<MinistryType> activeMinistries = personMinistryReadService
                .findActiveMinistriesByPersonIds(List.of(id))
                .getOrDefault(id, Set.of());
        return toMinistriesResponseDTO(id, activeMinistries);
    }

    @Override
    @Transactional
    public PersonMinistriesResponseDTO updatePersonMinistries(Long id, PersonMinistriesUpdateRequestDTO requestDTO) {
        Set<MinistryType> desiredMinistries = parseDesiredMinistries(requestDTO.getMinistries());
        PersonMinistrySyncResult result = personMinistryCommandService.syncMinistries(id, desiredMinistries);
        return toMinistriesResponseDTO(result.person().getId(), result.activeMinistries());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserProfileResponseDTO getCurrentUserProfile(String phoneNumber) {
        Person person = findAuthenticatedPerson(phoneNumber);
        return toCurrentUserProfileDTO(person);
    }

    @Override
    @Transactional
    public CurrentUserProfileResponseDTO updateCurrentUserProfile(String phoneNumber, CurrentUserProfileUpdateRequestDTO requestDTO) {
        Person person = findAuthenticatedPerson(phoneNumber);
        person.setName(normalizeRequiredName(requestDTO.getName()));
        person.setBirthdayDate(requestDTO.getBirthdayDate());
        Person savedPerson = personRepository.save(person);
        return toCurrentUserProfileDTO(savedPerson);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CurrentUserScheduleResponseDTO> findCurrentUserSchedules(
            String phoneNumber,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        validateScheduleQuery(startDate, endDate, page, size);
        Person person = findAuthenticatedPerson(phoneNumber);

        PageRequest pageable = PageRequest.of(page, size);
        Page<PersonScheduleEventProjection> eventPage = eventAssignmentRepository.findScheduleEventsByPersonId(
                person.getId(),
                startDate,
                endDate,
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

        List<CurrentUserScheduleResponseDTO> content = eventPage.getContent().stream()
                .map(event -> toCurrentUserScheduleDTO(
                        event,
                        assignmentsByEvent.getOrDefault(event.getEventId(), EnumSet.noneOf(EventAssignmentType.class))
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
            EnumSet<EventAssignmentType> assignmentTypes
    ) {
        return new CurrentUserScheduleResponseDTO(
                event.getEventId(),
                event.getEventName(),
                event.getEventDate(),
                event.getEventTime(),
                event.getMassOrCelebration(),
                event.getLocationId(),
                event.getLocationName(),
                List.copyOf(assignmentTypes)
        );
    }

    private Person findAuthenticatedPerson(String phoneNumber) {
        return personRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", phoneNumber));
    }

    private CurrentUserProfileResponseDTO toCurrentUserProfileDTO(Person person) {
        CurrentUserProfileResponseDTO dto = currentUserProfileMapper.toDto(person);
        dto.setMinistries(sortedMinistries(activeMinistriesForPerson(person.getId())));
        return dto;
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("O campo nome não pode ser vazio");
        }
        return name.trim();
    }

    private PersonMinistriesResponseDTO toMinistriesResponseDTO(Long id, Set<MinistryType> ministries) {
        return new PersonMinistriesResponseDTO(id, sortedMinistries(ministries));
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

    private Set<MinistryType> parseDesiredMinistries(List<String> rawMinistries) {
        if (rawMinistries == null) {
            throw new BusinessException("O conjunto de ministerios e obrigatorio");
        }
        Set<MinistryType> desired = new LinkedHashSet<>();
        for (String rawMinistry : rawMinistries) {
            MinistryType ministryType = parseMinistryType(rawMinistry);
            if (!desired.add(ministryType)) {
                throw new BusinessException("Ministerio duplicado no request: " + ministryType.name());
            }
        }
        return desired;
    }

    private MinistryType parseMinistryType(String rawMinistry) {
        String normalized = normalizeOptionalFilter(rawMinistry);
        if (normalized == null) {
            throw new BadRequestException("Tipo de ministerio invalido");
        }
        try {
            return MinistryType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de ministerio invalido: " + rawMinistry);
        }
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

    private MinistryType normalizeMinistryFilter(String ministry) {
        String normalized = normalizeOptionalFilter(ministry);
        if (normalized == null) {
            return null;
        }
        try {
            return MinistryType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Ministerio invalido");
        }
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

    private boolean removesAdminRole(Person person, String requestedRole) {
        return person.hasRole(ROLE_ADMIN) && !ROLE_ADMIN.equals(requestedRole);
    }

    private void validateSelfAdminDemotion(Person person) {
        String username = currentAuthenticatedUsername();
        if (username != null && username.equals(person.getPhoneNumber())) {
            throw new ConflictException("Voce nao pode remover o seu proprio perfil administrativo.");
        }
    }

    private void validateLastAdministrator() {
        long totalAdministrators = personRepository.findPeopleByRoleForUpdate(ROLE_ADMIN).stream()
                .map(Person::getId)
                .distinct()
                .count();
        if (totalAdministrators <= 1) {
            throw new ConflictException("O ultimo administrador do sistema nao pode ter seu perfil alterado.");
        }
    }

    private String currentAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
