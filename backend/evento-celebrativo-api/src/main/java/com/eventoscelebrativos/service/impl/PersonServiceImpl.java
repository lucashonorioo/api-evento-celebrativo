package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonAdminMapper;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PersonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
    private static final Set<String> ALLOWED_PERSON_TYPES = Set.of(
            "reader",
            "commentator",
            "minister_of_the_word",
            "eucharistic_minister",
            "priest"
    );

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final PersonAdminMapper personAdminMapper;
    private final PersonRoleUpdateMapper personRoleUpdateMapper;

    public PersonServiceImpl(
            PersonRepository personRepository,
            RoleRepository roleRepository,
            PersonAdminMapper personAdminMapper,
            PersonRoleUpdateMapper personRoleUpdateMapper
    ) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personAdminMapper = personAdminMapper;
        this.personRoleUpdateMapper = personRoleUpdateMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonAdminResponseDTO> findPeople(
            String name,
            String phoneNumber,
            String personType,
            String role,
            int page,
            int size
    ) {
        String normalizedName = normalizeOptionalFilter(name);
        String normalizedPhoneNumber = normalizeOptionalFilter(phoneNumber);
        String normalizedPersonType = normalizePersonTypeFilter(personType);
        String normalizedRole = normalizeRoleFilter(role);
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(page, size);
        Page<Long> idPage = personRepository.findAdminPageIds(
                normalizedName,
                normalizedPhoneNumber,
                normalizedPersonType,
                normalizedRole,
                pageable
        );

        if (idPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, idPage.getTotalElements());
        }

        List<Long> ids = idPage.getContent();
        Map<Long, Person> peopleById = personRepository.findAllByIdInWithRoles(ids).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        List<PersonAdminResponseDTO> content = ids.stream()
                .map(peopleById::get)
                .map(personAdminMapper::toDto)
                .toList();

        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PersonAdminResponseDTO findPersonById(Long id) {
        validateId(id);
        Person person = personRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));
        return personAdminMapper.toDto(person);
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
        return personRoleUpdateMapper.toDto(savedPerson);
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

    private String normalizePersonTypeFilter(String personType) {
        String normalized = normalizeOptionalFilter(personType);
        if (normalized != null && !ALLOWED_PERSON_TYPES.contains(normalized)) {
            throw new BadRequestException("Tipo de pessoa invalido");
        }
        return normalized;
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
