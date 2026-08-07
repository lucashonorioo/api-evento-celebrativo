package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.request.EucharisticMinisterUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EucharisticMinisterServiceImpl implements EucharisticMinisterService {

    private final EucharisticMinisterMapper eucharisticMinisterMapper;
    private final PersonAccountCoordinator personAccountCoordinator;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public EucharisticMinisterServiceImpl(
            EucharisticMinisterMapper eucharisticMinisterMapper,
            PersonAccountCoordinator personAccountCoordinator,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.eucharisticMinisterMapper = eucharisticMinisterMapper;
        this.personAccountCoordinator = personAccountCoordinator;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }


    @Override
    @Transactional
    public EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        Person eucharisticMinister = eucharisticMinisterMapper.toEntity(eucharisticMinisterRequestDTO);
        Person saved = personMinistryCommandService.create(eucharisticMinister, MinistryType.EUCHARISTIC_MINISTER);
        personAccountCoordinator.provisionAccess(saved, eucharisticMinisterRequestDTO);
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
    public EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterUpdateRequestDTO eucharisticMinisterUpdateRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        eucharisticMinisterUpdateRequestDTO.rejectAccountFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.EUCHARISTIC_MINISTER, "Ministro de Eucaristia");
        eucharisticMinisterMapper.updateEucharisticMinisterFromDto(eucharisticMinisterUpdateRequestDTO, person);

        Person saved = personCadastralUpdateService.updateCadastral(person);
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
