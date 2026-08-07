package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.CommentatorUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.CommentatorService;
import com.eventoscelebrativos.service.PersonAccountCoordinator;
import com.eventoscelebrativos.service.PersonCadastralUpdateService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentatorServiceImpl implements CommentatorService {

    private static final String ENTITY_LABEL = "Comentarista";

    private final CommentatorMapper commentatorMapper;
    private final PersonAccountCoordinator personAccountCoordinator;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;
    private final PersonCadastralUpdateService personCadastralUpdateService;

    public CommentatorServiceImpl(
            CommentatorMapper commentatorMapper,
            PersonAccountCoordinator personAccountCoordinator,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService,
            PersonCadastralUpdateService personCadastralUpdateService
    ) {
        this.commentatorMapper = commentatorMapper;
        this.personAccountCoordinator = personAccountCoordinator;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
        this.personCadastralUpdateService = personCadastralUpdateService;
    }

    @Override
    @Transactional
    public CommentatorResponseDTO createCommentator(CommentatorRequestDTO commentatorRequestDTO) {
        Person commentator = commentatorMapper.toEntity(commentatorRequestDTO);
        Person saved = personMinistryCommandService.create(commentator, MinistryType.COMMENTATOR);
        personAccountCoordinator.provisionAccess(saved, commentatorRequestDTO);
        return commentatorMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentatorResponseDTO> findAllCommentators() {
        List<Person> people = personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.COMMENTATOR);
        return commentatorMapper.toDtoPersonList(people);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentatorResponseDTO findCommentatorById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.COMMENTATOR, ENTITY_LABEL);
        return commentatorMapper.toDtoFromPerson(person);
    }

    @Override
    @Transactional
    public CommentatorResponseDTO updateCommentator(Long id, CommentatorUpdateRequestDTO commentatorUpdateRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        commentatorUpdateRequestDTO.rejectAccountFields();
        Person person = personMinistryCommandService.requireActiveMinistryPersonForUpdate(id, MinistryType.COMMENTATOR, ENTITY_LABEL);
        commentatorMapper.updateCommentatorFromDto(commentatorUpdateRequestDTO, person);

        Person saved = personCadastralUpdateService.updateCadastral(person);
        return commentatorMapper.toDtoFromPerson(saved);
    }

    @Override
    @Transactional
    public void deleteCommentatorById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        personMinistryCommandService.removeMinistry(id, MinistryType.COMMENTATOR, ENTITY_LABEL);
    }
}
