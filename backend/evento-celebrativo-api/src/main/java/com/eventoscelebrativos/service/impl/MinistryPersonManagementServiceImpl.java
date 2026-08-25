package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.MinistryPersonMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.MinistryPersonManagementService;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MinistryPersonManagementServiceImpl implements MinistryPersonManagementService {

    private static final String ENTITY_LABEL = "Pessoa";
    private static final int MAX_PAGE_SIZE = 100;

    private final MinistryPersonMapper ministryPersonMapper;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public MinistryPersonManagementServiceImpl(
            MinistryPersonMapper ministryPersonMapper,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.ministryPersonMapper = ministryPersonMapper;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MinistryPersonResponseDTO> findPeople(MinistryType ministryType, int page, int size) {
        requireMinistryType(ministryType);
        validatePagination(page, size);
        Page<Person> people = personMinistryReadService.findActivePeopleByMinistry(ministryType, PageRequest.of(page, size));
        return people.map(ministryPersonMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public MinistryPersonResponseDTO findPersonById(MinistryType ministryType, Long personId) {
        requireMinistryType(ministryType);
        Person person = personMinistryCommandService.requireActiveMinistryPerson(personId, ministryType, ENTITY_LABEL);
        return ministryPersonMapper.toDto(person);
    }

    @Override
    @Transactional
    public MinistryPersonResponseDTO create(MinistryType ministryType, MinistryPersonCreateRequestDTO requestDTO) {
        requireMinistryType(ministryType);
        requestDTO.rejectForbiddenFields();
        Person person = ministryPersonMapper.toEntity(requestDTO);
        Person saved = personMinistryCommandService.create(person, ministryType);
        return ministryPersonMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MinistryPersonResponseDTO update(MinistryType ministryType, Long personId, MinistryPersonUpdateRequestDTO requestDTO) {
        requireMinistryType(ministryType);
        requestDTO.rejectForbiddenFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(personId, ministryType, ENTITY_LABEL);
        Person saved = personCadastralUpdateService.updateCadastral(
                person,
                requestDTO.getName(),
                person.getPhoneNumber(),
                requestDTO.getBirthdayDate()
        );
        return ministryPersonMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MinistryPersonResponseDTO addOrReactivateMinistry(MinistryType ministryType, Long personId) {
        requireMinistryType(ministryType);
        Person person = personMinistryCommandService.addOrReactivateMinistry(personId, ministryType);
        return ministryPersonMapper.toDto(person);
    }

    @Override
    @Transactional
    public void removeMinistry(MinistryType ministryType, Long personId) {
        requireMinistryType(ministryType);
        personMinistryCommandService.removeMinistry(personId, ministryType, ENTITY_LABEL);
    }

    private void requireMinistryType(MinistryType ministryType) {
        if (ministryType == null) {
            throw new BusinessException("Função ministerial é obrigatória");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("O número da página deve ser maior ou igual a zero");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("O tamanho da página deve ser maior que zero e menor ou igual a 100");
        }
    }
}
