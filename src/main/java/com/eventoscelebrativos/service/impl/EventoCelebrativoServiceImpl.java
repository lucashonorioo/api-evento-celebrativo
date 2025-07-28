package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
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

    private final CelebrationEventRepository celebrationEventRepository;
    private final CelebrationEventMapper celebrationEventMapper;

    public EventoCelebrativoServiceImpl(CelebrationEventRepository celebrationEventRepository, CelebrationEventMapper celebrationEventMapper) {
        this.celebrationEventRepository = celebrationEventRepository;
        this.celebrationEventMapper = celebrationEventMapper;
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO criarEvento(CelebrationEventRequestDTO celebrationEventRequestDTO) {
        CelebrationEvent celebrationEvent = celebrationEventMapper.toEntity(celebrationEventRequestDTO);
        celebrationEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CelebrationEventResponseDTO> listarTodosEventos() {
        List<CelebrationEvent> eventosCelebrativo = celebrationEventRepository.findAll();
        return celebrationEventMapper.toDtoList(eventosCelebrativo);
    }

    @Override
    public Page<EucharistScaleEventResponseDTO> listarEscalaMinsEucaristia(Pageable pageable, LocalDate dataInicial, LocalDate dataFinal) {
        if (dataInicial == null || dataFinal == null || dataInicial.isAfter(dataFinal)) {
            throw new BusinessException("As datas estão inválidas");
        }

        Page<EucharistScaleEventProjection> eventoEscalaMinistrosProjection = celebrationEventRepository.buscarEscalaMinistro(pageable, dataInicial, dataFinal);

        Map<String, EucharistScaleEventResponseDTO> agrupado = new LinkedHashMap<>();
        for (EucharistScaleEventProjection proj : eventoEscalaMinistrosProjection.getContent()) {
            String chave = proj.getNomeEvento() + "|" + proj.getDataEvento() + "|" + proj.getHoraEvento();

            EucharistScaleEventResponseDTO dto = agrupado.computeIfAbsent(chave, k ->
                    new EucharistScaleEventResponseDTO(
                            proj.getNomeEvento(),
                            proj.getDataEvento(),
                            proj.getHoraEvento(),
                            proj.getNomeIgreja()
                    ));

            dto.getNomeMinistros().add(proj.getNomeMinistro());
        }

        List<EucharistScaleEventResponseDTO> dtoList = new ArrayList<>(agrupado.values());
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtoList.size());

        List<EucharistScaleEventResponseDTO> pagedList = dtoList.subList(start, end);

        return new PageImpl<>(pagedList, pageable, dtoList.size());
    }


    @Override
    @Transactional(readOnly = true)
    public CelebrationEventResponseDTO buscarEventoPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento celebrativo", id));
        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional
    public CelebrationEventResponseDTO atualizarEvento(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        CelebrationEvent celebrationEvent = celebrationEventRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Evento celebrativo", id));
        celebrationEvent = celebrationEventRepository.save(celebrationEvent);

        return celebrationEventMapper.toDto(celebrationEvent);
    }

    @Override
    @Transactional
    public void deletarEvento(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!celebrationEventRepository.existsById(id)) {
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

