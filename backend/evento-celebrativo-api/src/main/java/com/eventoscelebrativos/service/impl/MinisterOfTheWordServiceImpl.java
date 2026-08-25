package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.request.MinisterOfTheWordUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinisterOfTheWordServiceImpl implements MinisterOfTheWordService {

    private final MinisterOfTheWordMapper ministerOfTheWordMapper;
    private final PersonAccountCoordinator personAccountCoordinator;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public MinisterOfTheWordServiceImpl(
            MinisterOfTheWordMapper ministerOfTheWordMapper,
            PersonAccountCoordinator personAccountCoordinator,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
        this.personAccountCoordinator = personAccountCoordinator;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        Person ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);
        Person saved = personMinistryCommandService.create(ministerOfTheWord, MinistryType.MINISTER_OF_THE_WORD);
        personAccountCoordinator.provisionAccess(saved, ministerOfTheWordRequestDTO);
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
    public MinisterOfTheWordResponseDTO updateMinisterOfTheWord(Long id, MinisterOfTheWordUpdateRequestDTO ministerOfTheWordUpdateRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        ministerOfTheWordUpdateRequestDTO.rejectAccountFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.MINISTER_OF_THE_WORD, "Ministro da Palavra");

        Person saved = personCadastralUpdateService.updateCadastral(
                person,
                ministerOfTheWordUpdateRequestDTO.getName(),
                ministerOfTheWordUpdateRequestDTO.getPhoneNumber(),
                ministerOfTheWordUpdateRequestDTO.getBirthdayDate()
        );
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
