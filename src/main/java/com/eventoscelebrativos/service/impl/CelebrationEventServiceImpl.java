package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class CelebrationEventServiceImpl implements CelebrationEventService {

    private final CelebrationEventRepository celebrationEventRepository;
    private final CelebrationEventMapper celebrationEventMapper;

    public CelebrationEventServiceImpl(CelebrationEventRepository celebrationEventRepository, CelebrationEventMapper celebrationEventMapper) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.celebrationEventMapper = celebrationEventMapper;
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
    public Page<EucharistScaleEventResponseDTO> findEucharistScale(Pageable pageable, LocalDate startDate , LocalDate endDate) {
        if (startDate  == null || endDate == null || startDate .isAfter(endDate)) {
            throw new BusinessException("As datas estão inválidas");
        }

        Page<EucharistScaleEventProjection> projections = celebrationEventRepository.findEucharistScale(pageable, startDate, endDate);

        Map<Object, List<EucharistScaleEventProjection>> groupedByEvent = projections.stream()
                .collect(Collectors.groupingBy(p -> p.getNameMassOrEvent() + p.getEventDate() + p.getEventTime()));

        List<EucharistScaleEventResponseDTO> dtoList = groupedByEvent.values().stream().map(list -> {
            EucharistScaleEventResponseDTO dto = new EucharistScaleEventResponseDTO(
                    list.get(0).getNameMassOrEvent(),
                    list.get(0).getEventDate(),
                    list.get(0).getEventTime(),
                    list.get(0).getChurchName()
            );
            dto.getNameMinisters().addAll(list.stream().map(EucharistScaleEventProjection::getMinisterName).toList());
            return dto;
        }).toList();

        return new PageImpl<>(dtoList, pageable, projections.getTotalElements());
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
            celebrationEvent = celebrationEventRepository.save(celebrationEvent);

            return celebrationEventMapper.toDto(celebrationEvent);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
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
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não foi possivel deletar evento, possui outras referencias no sistema");
        }
    }
}

