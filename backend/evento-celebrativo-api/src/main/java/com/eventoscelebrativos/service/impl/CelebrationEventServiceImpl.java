package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.mapper.CelebrationEventScaleMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.repository.PersonRepository;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class CelebrationEventServiceImpl implements CelebrationEventService {

    private final CelebrationEventRepository celebrationEventRepository;
    private final LocationRepository locationRepository;
    private final PersonRepository personRepository;
    private final CelebrationEventMapper celebrationEventMapper;
    private final CelebrationEventScaleMapper celebrationEventScaleMapper;

    public CelebrationEventServiceImpl(
            CelebrationEventRepository celebrationEventRepository,
            LocationRepository locationRepository,
            PersonRepository personRepository,
            CelebrationEventMapper celebrationEventMapper,
            CelebrationEventScaleMapper celebrationEventScaleMapper
    ) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.locationRepository = locationRepository;
        this.personRepository = personRepository;
        this.celebrationEventMapper = celebrationEventMapper;
        this.celebrationEventScaleMapper = celebrationEventScaleMapper;
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO createEvent(CelebrationEventRequestDTO celebrationEventRequestDTO) {
        CelebrationEvent celebrationEvent = celebrationEventMapper.toEntity(celebrationEventRequestDTO);
        celebrationEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CelebrationEventResponseDTO> findAllEvents() {
        List<CelebrationEvent> celebrationEvents = celebrationEventRepository.findAll();
        return celebrationEventMapper.toDtoList(celebrationEvents);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EucharistScaleEventResponseDTO> findEucharistScale(Pageable pageable, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException("As datas estão inválidas");
        }

        Page<EucharistScaleEventProjection> projections =
                celebrationEventRepository.findEucharistScale(pageable, startDate, endDate);

        return projections.map(projection -> {
            EucharistScaleEventResponseDTO dto = new EucharistScaleEventResponseDTO(
                    projection.getNameMassOrEvent(),
                    projection.getEventDate(),
                    projection.getEventTime(),
                    projection.getChurchName()
            );

            if (projection.getMinisterNames() != null && !projection.getMinisterNames().isBlank()) {
                Arrays.stream(projection.getMinisterNames().split(","))
                        .map(String::trim)
                        .filter(name -> !name.isBlank())
                        .forEach(dto.getNameMinisters()::add);
            }

            return dto;
        });
    }


    @Override
    @Transactional(readOnly = true)
    public CelebrationEventResponseDTO findEventById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO updateEvent(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try{
            CelebrationEvent celebrationEvent = celebrationEventRepository.getReferenceById(id);

            celebrationEventMapper.updateCelebrationEventMapperFromDto(celebrationEventRequestDTO, celebrationEvent);
            celebrationEvent = celebrationEventRepository.save(celebrationEvent);

            return celebrationEventMapper.toDto(celebrationEvent);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO updateEventScale(Long id, CelebrationEventScaleRequestDTO celebrationEventScaleRequestDTO) {
        validateId(id);
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));

        applyScaleToEvent(celebrationEvent, celebrationEventScaleRequestDTO);
        CelebrationEvent savedEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventScaleMapper.toDto(savedEvent);
    }

    @Override
    @Transactional
    public CelebrationEventScaleResponseDTO createEventWithScale(CelebrationEventWithScaleRequestDTO celebrationEventWithScaleRequestDTO) {
        CelebrationEvent celebrationEvent = new CelebrationEvent();
        celebrationEvent.setNameMassOrEvent(celebrationEventWithScaleRequestDTO.getNameMassOrEvent());
        celebrationEvent.setEventDate(celebrationEventWithScaleRequestDTO.getEventDate());
        celebrationEvent.setEventTime(celebrationEventWithScaleRequestDTO.getEventTime());
        celebrationEvent.setMassOrCelebration(celebrationEventWithScaleRequestDTO.getMassOrCelebration());

        applyScaleToEvent(celebrationEvent, toScaleRequest(celebrationEventWithScaleRequestDTO));
        CelebrationEvent savedEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventScaleMapper.toDto(savedEvent);
    }

    @Override
    @Transactional
    public void deleteEventById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!celebrationEventRepository.existsById(id)){
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
        try{
            celebrationEventRepository.deleteById(id);
            celebrationEventRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
    }

    private void applyScaleToEvent(CelebrationEvent celebrationEvent, CelebrationEventScaleRequestDTO dto) {
        validateId(dto.getLocationId());

        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Local", dto.getLocationId()));

        List<Person> people = new ArrayList<>();
        Set<Long> usedPersonIds = new HashSet<>();

        addOptionalPerson(people, usedPersonIds, dto.getPriestId(), Priest.class, "padre");
        addPeople(people, usedPersonIds, safeList(dto.getReaderIds()), Reader.class, "leitor");
        addPeople(people, usedPersonIds, safeList(dto.getCommentatorIds()), Commentator.class, "comentarista");
        addPeople(people, usedPersonIds, safeList(dto.getMinisterOfTheWordIds()), MinisterOfTheWord.class, "ministro da Palavra");
        addPeople(people, usedPersonIds, safeList(dto.getEucharisticMinisterIds()), EucharisticMinister.class, "ministro da Eucaristia");

        celebrationEvent.getLocations().clear();
        celebrationEvent.getLocations().add(location);
        celebrationEvent.getPeople().clear();
        celebrationEvent.getPeople().addAll(people);
    }

    private CelebrationEventScaleRequestDTO toScaleRequest(CelebrationEventWithScaleRequestDTO dto) {
        return new CelebrationEventScaleRequestDTO(
                dto.getLocationId(),
                dto.getPriestId(),
                dto.getReaderIds(),
                dto.getCommentatorIds(),
                dto.getMinisterOfTheWordIds(),
                dto.getEucharisticMinisterIds()
        );
    }

    private void addPeople(
            List<Person> people,
            Set<Long> usedPersonIds,
            List<Long> ids,
            Class<? extends Person> expectedType,
            String roleName
    ) {
        Set<Long> idsInSameRole = new HashSet<>();
        for (Long id : ids) {
            if (!idsInSameRole.add(id)) {
                throw new BusinessException("Não é permitido informar IDs duplicados para " + roleName);
            }
        }
        for (Long id : ids) {
            addOptionalPerson(people, usedPersonIds, id, expectedType, roleName);
        }
    }

    private void addOptionalPerson(
            List<Person> people,
            Set<Long> usedPersonIds,
            Long id,
            Class<? extends Person> expectedType,
            String roleName
    ) {
        if (id == null) {
            return;
        }
        validateId(id);
        if (!usedPersonIds.add(id)) {
            throw new BusinessException("A mesma pessoa não pode ocupar mais de uma função na mesma escala");
        }

        Person person = personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa", id));

        if (!expectedType.isInstance(person)) {
            throw new BusinessException("A pessoa informada para " + roleName + " não possui o tipo correto");
        }

        people.add(person);
    }

    private List<Long> safeList(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids;
    }

    private void validateId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
    }
}

