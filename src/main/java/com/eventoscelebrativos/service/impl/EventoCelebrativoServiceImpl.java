package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.mapper.EventoCelebrativoMapper;
import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.repository.EventoCelebrativoRepository;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class EventoCelebrativoServiceImpl implements EventoCelebrativoService {

    private final EventoCelebrativoRepository eventoCelebrativoRepository;
    private final EventoCelebrativoMapper eventoCelebrativoMapper;

    public EventoCelebrativoServiceImpl(EventoCelebrativoRepository eventoCelebrativoRepository, EventoCelebrativoMapper eventoCelebrativoMapper) {
        this.eventoCelebrativoRepository = eventoCelebrativoRepository;
        this.eventoCelebrativoMapper = eventoCelebrativoMapper;
    }

    @Override
    @Transactional
    public EventoCelebrativoResponseDTO criarEvento(EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO) {
        EventoCelebrativo eventoCelebrativo = eventoCelebrativoMapper.toEntity(eventoCelebrativoRequestDTO);
        eventoCelebrativo = eventoCelebrativoRepository.save(eventoCelebrativo);

        return eventoCelebrativoMapper.toDto(eventoCelebrativo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoCelebrativoResponseDTO> listarTodosEventos() {
        List<EventoCelebrativo> eventosCelebrativo = eventoCelebrativoRepository.findAll();
        return eventoCelebrativoMapper.toDtoList(eventosCelebrativo);
    }

    @Override
    @Transactional(readOnly = true)
    public EventoCelebrativoResponseDTO buscarEventoPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        EventoCelebrativo eventoCelebrativo = eventoCelebrativoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        return eventoCelebrativoMapper.toDto(eventoCelebrativo);
    }

    @Override
    @Transactional
    public EventoCelebrativoResponseDTO atualizarEvento(Long id, EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        EventoCelebrativo eventoCelebrativo = eventoCelebrativoRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Evento celebrativo", id));
        eventoCelebrativo = eventoCelebrativoRepository.save(eventoCelebrativo);

        return eventoCelebrativoMapper.toDto(eventoCelebrativo);
    }

    @Override
    @Transactional
    public void deletarEvento(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        EventoCelebrativo eventoCelebrativo = eventoCelebrativoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        eventoCelebrativoRepository.deleteById(id);
    }
}
