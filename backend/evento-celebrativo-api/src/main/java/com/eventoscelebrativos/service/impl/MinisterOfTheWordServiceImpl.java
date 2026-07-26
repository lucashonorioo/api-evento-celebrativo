package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinisterOfTheWordServiceImpl implements MinisterOfTheWordService {

    private final MinisterOfTheWordMapper ministerOfTheWordMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public MinisterOfTheWordServiceImpl(
            MinisterOfTheWordMapper ministerOfTheWordMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        Person ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);

        ministerOfTheWord.setPassword(passwordEncoder.encode(ministerOfTheWordRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        ministerOfTheWord.addRole(operatorRole);

        Person saved = personMinistryCommandService.create(ministerOfTheWord, MinistryType.MINISTER_OF_THE_WORD);
        return ministerOfTheWordMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinisterOfTheWordResponseDTO> findAllMinistersOfTheWord() {
        List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD);
        return ministerOfTheWordMapper.toDtoPersonList(people);
    }

    @Override
    @Transactional(readOnly = true)
    public MinisterOfTheWordResponseDTO findMinisterOfTheWordById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.MINISTER_OF_THE_WORD, "Ministro Da Palavra");
        return ministerOfTheWordMapper.toDtoFromPerson(person);
    }

    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO updateMinisterOfTheWord(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.MINISTER_OF_THE_WORD, "Ministro da Palavra");
        ministerOfTheWordMapper.updateMinisterOfTheWordFromDto(ministerOfTheWordRequestDTO, person);
        person.setPassword(passwordEncoder.encode(ministerOfTheWordRequestDTO.getPassword()));

        Person saved = personMinistryCommandService.save(person);
        return ministerOfTheWordMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional
    public void deleteMinisterOfTheWord(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        personMinistryCommandService.removeMinistry(id, MinistryType.MINISTER_OF_THE_WORD, "Ministro da Palavra");
    }
}
