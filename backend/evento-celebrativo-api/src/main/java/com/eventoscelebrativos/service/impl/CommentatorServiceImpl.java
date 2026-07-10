package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.CommentatorService;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentatorServiceImpl implements CommentatorService {

    private final CommentatorRepository commentatorRepository;
    private final CommentatorMapper commentatorMapper;

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public CommentatorServiceImpl(CommentatorRepository commentatorRepository, CommentatorMapper commentatorMapper, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.commentatorRepository = commentatorRepository;
        this.commentatorMapper = commentatorMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public CommentatorResponseDTO createCommentator(CommentatorRequestDTO commentatorRequestDTO) {
        Commentator commentator = commentatorMapper.toEntity(commentatorRequestDTO);

        commentator.setPassword(passwordEncoder.encode(commentatorRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        commentator.addRole(operatorRole);

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
        try {
            Commentator commentator = commentatorRepository.getReferenceById(id);
            commentatorMapper.updateCommentatorFromDto(commentatorRequestDTO, commentator);
            commentator.setPassword(passwordEncoder.encode(commentatorRequestDTO.getPassword()));

            Commentator commentatorSalvo = commentatorRepository.save(commentator);

            return commentatorMapper.toDto(commentatorSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Comentarista", id);
        }
    }

    @Override
    @Transactional
    public void deleteCommentatorById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        if(!commentatorRepository.existsById(id)){
            throw new ResourceNotFoundException("Comentarista", id);
        }
        try {
            commentatorRepository.deleteById(id);
            commentatorRepository.flush();
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros.");
        }
    }

}
