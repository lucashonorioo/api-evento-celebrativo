package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.service.ComentaristaService;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ComentaristaServiceImpl implements ComentaristaService {

    private final CommentatorRepository commentatorRepository;
    private final CommentatorMapper commentatorMapper;

    public ComentaristaServiceImpl(CommentatorRepository commentatorRepository, CommentatorMapper commentatorMapper) {
        this.commentatorRepository = commentatorRepository;
        this.commentatorMapper = commentatorMapper;
    }

    @Override
    @Transactional
    public CommentatorResponseDTO criarComentarista(CommentatorRequestDTO commentatorRequestDTO) {
        Commentator commentator = commentatorMapper.toEntity(commentatorRequestDTO);
        commentator = commentatorRepository.save(commentator);
        return commentatorMapper.toDto(commentator);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentatorResponseDTO> listarTodosComentaristas() {
        List<Commentator> commentators = commentatorRepository.findAll();
        return commentatorMapper.toDtoList(commentators);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentatorResponseDTO buscarComentaristaPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        return commentatorMapper.toDto(commentator);
    }

    @Override
    @Transactional
    public CommentatorResponseDTO atualizarComentarista(Long id, CommentatorRequestDTO commentatorRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        commentatorMapper.atualizarComentaristaFromDto(commentatorRequestDTO, commentator);
        Commentator commentatorSalvo = commentatorRepository.save(commentator);

        return commentatorMapper.toDto(commentatorSalvo);
    }

    @Override
    @Transactional
    public void deletarComentarista(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comentarista", id));
        commentatorRepository.deleteById(id);
    }

}
