package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.service.UserAccountLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinisterOfTheWordServiceImpl implements MinisterOfTheWordService {

    private final MinisterOfTheWordMapper ministerOfTheWordMapper;
    private final UserAccountLifecycleService userAccountLifecycleService;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public MinisterOfTheWordServiceImpl(
            MinisterOfTheWordMapper ministerOfTheWordMapper,
            UserAccountLifecycleService userAccountLifecycleService,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
        this.userAccountLifecycleService = userAccountLifecycleService;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        Person ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);
        userAccountLifecycleService.applyCreationAccess(ministerOfTheWord, ministerOfTheWordRequestDTO);
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
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.MINISTER_OF_THE_WORD, "Ministro da Palavra");
        ministerOfTheWordMapper.updateMinisterOfTheWordFromDto(ministerOfTheWordRequestDTO, person);
        userAccountLifecycleService.applyMinisterialUpdateAccess(person, ministerOfTheWordRequestDTO);

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
