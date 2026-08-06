package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.mapper.ReaderMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.service.ReaderService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReaderServiceImpl implements ReaderService {

    private static final String ENTITY_LABEL = "Leitor";

    private final ReaderMapper readerMapper;
    private final PersonAccountCoordinator personAccountCoordinator;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public ReaderServiceImpl(
            ReaderMapper readerMapper,
            PersonAccountCoordinator personAccountCoordinator,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.readerMapper = readerMapper;
        this.personAccountCoordinator = personAccountCoordinator;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }


    @Override
    @Transactional
    public ReaderResponseDTO createReader(ReaderRequestDTO readerRequestDTO) {
        Person reader = readerMapper.toEntity(readerRequestDTO);
        Person saved = personMinistryCommandService.create(reader, MinistryType.READER);
        personAccountCoordinator.provisionAccess(saved, readerRequestDTO);
        return readerMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReaderResponseDTO> findAllReaders() {
        List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.READER);
        return readerMapper.toDtoPersonList(people);
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderResponseDTO findReaderById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positvio e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.READER, ENTITY_LABEL);
        return readerMapper.toDtoFromPerson(person);
    }

    @Override
    @Transactional
    public ReaderResponseDTO updateReader(Long id, ReaderUpdateRequestDTO readerUpdateRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        readerUpdateRequestDTO.rejectAccountFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.READER, ENTITY_LABEL);
        readerMapper.updateReaderFromDto(readerUpdateRequestDTO, person);

        Person saved = personMinistryCommandService.save(person);
        return readerMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional
    public void deleteReaderById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        personMinistryCommandService.removeMinistry(id, MinistryType.READER, ENTITY_LABEL);
    }
}
