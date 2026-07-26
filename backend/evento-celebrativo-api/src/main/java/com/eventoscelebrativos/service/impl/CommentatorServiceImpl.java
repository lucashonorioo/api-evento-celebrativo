package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.CommentatorService;
import com.eventoscelebrativos.service.PersonMinistryCommandService;
import com.eventoscelebrativos.service.PersonMinistryReadService;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentatorServiceImpl implements CommentatorService {

    private static final String ENTITY_LABEL = "Comentarista";

    private final CommentatorMapper commentatorMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonMinistryCommandService personMinistryCommandService;
    private final PersonMinistryReadService personMinistryReadService;

    public CommentatorServiceImpl(
            CommentatorMapper commentatorMapper,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PersonMinistryCommandService personMinistryCommandService,
            PersonMinistryReadService personMinistryReadService
    ) {
        this.commentatorMapper = commentatorMapper;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.personMinistryCommandService = personMinistryCommandService;
        this.personMinistryReadService = personMinistryReadService;
    }

    @Override
    @Transactional
    public CommentatorResponseDTO createCommentator(CommentatorRequestDTO commentatorRequestDTO) {
        Commentator commentator = commentatorMapper.toEntity(commentatorRequestDTO);

        commentator.setPassword(passwordEncoder.encode(commentatorRequestDTO.getPassword()));

        Role operatorRole = roleRepository.findByAuthority("ROLE_OPERATOR")
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"));

        commentator.addRole(operatorRole);

        Person saved = personMinistryCommandService.create(commentator, MinistryType.COMMENTATOR);
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
    public CommentatorResponseDTO updateCommentator(Long id, CommentatorRequestDTO commentatorRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O id deve ser positivo e não nulo");
        }
        Person person = personMinistryCommandService.requireActiveMinistryPerson(id, MinistryType.COMMENTATOR, ENTITY_LABEL);
        commentatorMapper.updateCommentatorFromDto(commentatorRequestDTO, person);
        person.setPassword(passwordEncoder.encode(commentatorRequestDTO.getPassword()));

        Person saved = personMinistryCommandService.save(person);
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
