package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.dto.response.EventoEscalaMinistrosResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.EventoCelebrativoMapper;
import com.eventoscelebrativos.model.EventoCelebrativo;
import com.eventoscelebrativos.projection.EventoEscalaMinistrosProjection;
import com.eventoscelebrativos.repository.EventoCelebrativoRepository;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
    public Page<EventoEscalaMinistrosResponseDTO> listarEscalaMinsEucaristia(Pageable pageable, LocalDate dataInicial, LocalDate dataFinal) {
        if (dataInicial == null || dataFinal == null || dataInicial.isAfter(dataFinal)) {
            throw new BusinessException("As datas estão inválidas");
        }

        Page<EventoEscalaMinistrosProjection> eventoEscalaMinistrosProjection = eventoCelebrativoRepository.buscarEscalaMinistro(pageable, dataInicial, dataFinal);

        Map<String, EventoEscalaMinistrosResponseDTO> agrupado = new LinkedHashMap<>();
        for (EventoEscalaMinistrosProjection proj : eventoEscalaMinistrosProjection.getContent()) {
            String chave = proj.getNomeEvento() + "|" + proj.getDataEvento() + "|" + proj.getHoraEvento();

            EventoEscalaMinistrosResponseDTO dto = agrupado.computeIfAbsent(chave, k ->
                    new EventoEscalaMinistrosResponseDTO(
                            proj.getNomeEvento(),
                            proj.getDataEvento(),
                            proj.getHoraEvento(),
                            proj.getNomeIgreja()
                    ));

            dto.getNomeMinistros().add(proj.getNomeMinistro());
        }

        List<EventoEscalaMinistrosResponseDTO> dtoList = new ArrayList<>(agrupado.values());
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtoList.size());

        List<EventoEscalaMinistrosResponseDTO> pagedList = dtoList.subList(start, end);

        return new PageImpl<>(pagedList, pageable, dtoList.size());
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
        if(!eventoCelebrativoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Evento celebrativo", id);
        }
        try{
            eventoCelebrativoRepository.deleteById(id);
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não foi possivel deletar evento, possui outras referencias no sistema");
        }
    }
}

