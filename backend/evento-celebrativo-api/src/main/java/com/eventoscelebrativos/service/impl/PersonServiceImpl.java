package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PersonRoleUpdateMapper;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PersonService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonServiceImpl implements PersonService {

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final PersonRoleUpdateMapper personRoleUpdateMapper;

    public PersonServiceImpl(PersonRepository personRepository, RoleRepository roleRepository, PersonRoleUpdateMapper personRoleUpdateMapper) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personRoleUpdateMapper = personRoleUpdateMapper;
    }

    @Override
    @Transactional
    public PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO) {
        if (id == null || id <= 0) {
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }

        Person person = personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));

        Role role = roleRepository.findByAuthority(requestDTO.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", requestDTO.getRole()));

        person.getRoles().clear();
        person.addRole(role);

        Person savedPerson = personRepository.save(person);
        return personRoleUpdateMapper.toDto(savedPerson);
    }
}
