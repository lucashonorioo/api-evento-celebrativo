package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryPersonUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.MinistryPersonMapper;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.repository.MinistryRepository;
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
    private final MinistryRepository ministryRepository;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public MinistryPersonManagementServiceImpl(
            MinistryPersonMapper ministryPersonMapper,
            MinistryRepository ministryRepository,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.ministryPersonMapper = ministryPersonMapper;
        this.ministryRepository = ministryRepository;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MinistryPersonResponseDTO> findPeople(Long ministryId, int page, int size) {
        validatePagination(page, size);
        Ministry ministry = requireMinistry(ministryId);
        Page<Person> people = personMinistryReadService.findActivePeopleByMinistryId(ministry.getId(), PageRequest.of(page, size));
        return people.map(ministryPersonMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public MinistryPersonResponseDTO findPersonById(Long ministryId, Long personId) {
        Ministry ministry = requireMinistry(ministryId);
        Person person = personMinistryCommandService.requireActiveMinistryPerson(personId, ministry, ENTITY_LABEL);
        return ministryPersonMapper.toDto(person);
    }

    @Override
    @Transactional
    public MinistryPersonResponseDTO create(Long ministryId, MinistryPersonCreateRequestDTO requestDTO) {
        Ministry ministry = requireMinistry(ministryId);
        requestDTO.rejectForbiddenFields();
        Person person = ministryPersonMapper.toEntity(requestDTO);
        Person saved = personMinistryCommandService.create(person, ministry);
        return ministryPersonMapper.toDto(saved);
    }

    @Override
    @Transactional
    public MinistryPersonResponseDTO update(Long ministryId, Long personId, MinistryPersonUpdateRequestDTO requestDTO) {
        Ministry ministry = requireMinistry(ministryId);
        requestDTO.rejectForbiddenFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(personId, ministry, ENTITY_LABEL);
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
    public MinistryPersonResponseDTO addOrReactivateMinistry(Long ministryId, Long personId) {
        Ministry ministry = requireMinistry(ministryId);
        Person person = personMinistryCommandService.addOrReactivateMinistry(personId, ministry);
        return ministryPersonMapper.toDto(person);
    }

    @Override
    @Transactional
    public void removeMinistry(Long ministryId, Long personId) {
        Ministry ministry = requireMinistry(ministryId);
        personMinistryCommandService.removeMinistry(personId, ministry, ENTITY_LABEL);
    }

    private Ministry requireMinistry(Long ministryId) {
        if (ministryId == null || ministryId <= 0) {
            throw new BadRequestException("O Id do ministério deve ser positivo e não nulo");
        }
        return ministryRepository.findById(ministryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministério", ministryId));
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
