package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.service.CommentatorService;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentatorServiceImpl implements CommentatorService {

    private final CommentatorRepository commentatorRepository;
    private final CommentatorMapper commentatorMapper;

    public CommentatorServiceImpl(CommentatorRepository commentatorRepository, CommentatorMapper commentatorMapper) {
        this.commentatorRepository = commentatorRepository;
        this.commentatorMapper = commentatorMapper;
    }

    @Override
    @Transactional
    public CommentatorResponseDTO createCommentator(CommentatorRequestDTO commentatorRequestDTO) {
        Commentator commentator = commentatorMapper.toEntity(commentatorRequestDTO);
        commentator = commentatorRepository.save(commentator);
        return commentatorMapper.toDto(commentator);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentatorResponseDTO> findAllCommentators() {
        List<Commentator> commentators = commentatorRepository.findAll();
        return commentatorMapper.toDtoList(commentators);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentatorResponseDTO findCommentatorById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        return commentatorMapper.toDto(commentator);
    }

    @Override
    @Transactional
    public CommentatorResponseDTO updateCommentator(Long id, CommentatorRequestDTO commentatorRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow( () -> new ResourceNotFoundException("Comentarista", id));
        commentatorMapper.updateCommentatorFromDto(commentatorRequestDTO, commentator);
        Commentator commentatorSalvo = commentatorRepository.save(commentator);

        return commentatorMapper.toDto(commentatorSalvo);
    }

    @Override
    @Transactional
    public void deleteCommentatorById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Commentator commentator = commentatorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comentarista", id));
        commentatorRepository.deleteById(id);
    }

}
