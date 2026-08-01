package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.PriestService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PriestServiceImpl implements PriestService {

    private static final String ENTITY_LABEL = "Padre";

    private final PriestMapper priestMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public PriestServiceImpl(
            PriestMapper priestMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.priestMapper = priestMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }


    @Override
    @Transactional
    public PriestResponseDTO createPriest(PriestRequestDTO priestRequestDTO) {
        Person priest = priestMapper.toEntity(priestRequestDTO);

        priest.setPassword(passwordEncoder.encode(priestRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        priest.addRole(operatorRole);

        Person saved = personMinistryCommandService.create(priest, MinistryType.PRIEST);
        return priestMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriestResponseDTO> findAllPriests() {
        List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST);
        return priestMapper.toDtoPersonList(people);
    }

    @Override
    @Transactional(readOnly = true)
    public PriestResponseDTO findPriestById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.PRIEST, ENTITY_LABEL);
        return priestMapper.toDtoFromPerson(person);
    }

    @Override
    @Transactional
    public PriestResponseDTO updatePriest(Long id, PriestRequestDTO priestRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.PRIEST, ENTITY_LABEL);
        priestMapper.updatePriestFromDto(priestRequestDTO, person);
        person.setPassword(passwordEncoder.encode(priestRequestDTO.getPassword()));

        Person saved = personMinistryCommandService.save(person);
        return priestMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional
    public void deletePriestById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        personMinistryCommandService.removeMinistry(id, MinistryType.PRIEST, ENTITY_LABEL);
    }
}
