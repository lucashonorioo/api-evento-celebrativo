package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.ComentaristaRequestDTO;
import com.eventoscelebrativos.dto.response.ComentaristaResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.ComentaristaMapper;
import com.eventoscelebrativos.model.Comentarista;
import com.eventoscelebrativos.repository.ComentaristaRepository;
import com.eventoscelebrativos.service.ComentaristaService;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ComentaristaServiceImpl implements ComentaristaService {

    private final ComentaristaRepository comentaristaRepository;
    private final ComentaristaMapper comentaristaMapper;

    public ComentaristaServiceImpl(ComentaristaRepository comentaristaRepository, ComentaristaMapper comentaristaMapper) {
        this.comentaristaRepository = comentaristaRepository;
        this.comentaristaMapper = comentaristaMapper;
    }

    @Override
    @Transactional
    public ComentaristaResponseDTO criarComentarista(ComentaristaRequestDTO comentaristaRequestDTO) {
        Comentarista comentarista = comentaristaMapper.toEntity(comentaristaRequestDTO);
        comentarista = comentaristaRepository.save(comentarista);
        return comentaristaMapper.toDto(comentarista);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComentaristaResponseDTO> listarTodosComentaristas() {
        List<Comentarista> comentaristas = comentaristaRepository.findAll();
        return comentaristaMapper.toDtoList(comentaristas);
    }

    @Override
    @Transactional(readOnly = true)
    public ComentaristaResponseDTO buscarComentaristaPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Comentarista comentarista = comentaristaRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        return comentaristaMapper.toDto(comentarista);
    }

    @Override
    @Transactional
    public ComentaristaResponseDTO atualizarComentarista(Long id, ComentaristaRequestDTO comentaristaRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Comentarista comentarista = comentaristaRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        comentaristaMapper.atualizarComentaristaFromDto(comentaristaRequestDTO, comentarista);
        Comentarista comentaristaSalvo = comentaristaRepository.save(comentarista);

        return comentaristaMapper.toDto(comentaristaSalvo);
    }

    @Override
    @Transactional
    public void deletarComentarista(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Comentarista comentarista = comentaristaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comentarista", id));
        comentaristaRepository.deleteById(id);
    }
}
