package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EucharisticMinisterServiceImpl implements EucharisticMinisterService {

    private final EucharisticMinisterMapper eucharisticMinisterMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public EucharisticMinisterServiceImpl(
            EucharisticMinisterMapper eucharisticMinisterMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.eucharisticMinisterMapper = eucharisticMinisterMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }


    @Override
    @Transactional
    public EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        Person eucharisticMinister = eucharisticMinisterMapper.toEntity(eucharisticMinisterRequestDTO);

        eucharisticMinister.setPassword(passwordEncoder.encode(eucharisticMinisterRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso,", "ROLE_OPERATOR"));

        eucharisticMinister.addRole(operatorRole);

        Person saved = personMinistryCommandService.create(eucharisticMinister, MinistryType.EUCHARISTIC_MINISTER);
        return eucharisticMinisterMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EucharisticMinisterResponseDTO> findAllEucharisticMinisters() {
        List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.EUCHARISTIC_MINISTER);
        return eucharisticMinisterMapper.toDtoPersonList(people);
    }

    @Override
    @Transactional(readOnly = true)
    public EucharisticMinisterResponseDTO findEucharisticMinistersById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.EUCHARISTIC_MINISTER, "Ministro De Eucaristia");
        return eucharisticMinisterMapper.toDtoFromPerson(person);
    }

    @Override
    @Transactional
    public EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.EUCHARISTIC_MINISTER, "Ministro de Eucaristia");
        eucharisticMinisterMapper.updateEucharisticMinisterFromDto(eucharisticMinisterRequestDTO, person);
        person.setPassword(passwordEncoder.encode(eucharisticMinisterRequestDTO.getPassword()));

        Person saved = personMinistryCommandService.save(person);
        return eucharisticMinisterMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional
    public void deleteEucharisticMinisterById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        personMinistryCommandService.removeMinistry(id, MinistryType.EUCHARISTIC_MINISTER, "Ministro de Eucaristia");
    }
}
