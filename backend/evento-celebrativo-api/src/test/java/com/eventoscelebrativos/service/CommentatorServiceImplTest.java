package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.CommentatorRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.CommentatorServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentatorServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1991, 2, 11);

    @Mock
    private CommentatorRepository repository;

    @Mock
    private CommentatorMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CommentatorServiceImpl service;

    @Test
    void shouldCreateCommentatorWithEncryptedPasswordAndOperatorRole() {
        CommentatorRequestDTO request = request();
        Commentator entity = commentator(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Commentator saved = commentator(1L, "encoded-password");
        CommentatorResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(repository.save(any(Commentator.class))).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createCommentator(request));

        ArgumentCaptor<Commentator> captor = ArgumentCaptor.forClass(Commentator.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        CommentatorRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(commentator(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createCommentator(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindCommentatorByIdWhenExists() {
        Commentator entity = commentator(1L, "encoded-password");
        CommentatorResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findCommentatorById(1L));
    }

    @Test
    void shouldThrowWhenCommentatorIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findCommentatorById(null));
        assertThrows(BusinessException.class, () -> service.findCommentatorById(0L));
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findCommentatorById(99L));
    }

    @Test
    void shouldListUpdateAndDeleteCommentator() {
        Commentator entity = commentator(1L, "old-password");
        CommentatorResponseDTO response = response(1L);
        List<Commentator> entities = List.of(entity);
        List<CommentatorResponseDTO> responses = List.of(response);

        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);
        assertSame(responses, service.findAllCommentators());

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updateCommentator(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        when(repository.existsById(1L)).thenReturn(true);
        service.deleteCommentatorById(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingCommentator() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> service.updateCommentator(99L, request()));

        when(repository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteCommentatorById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedCommentator() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteCommentatorById(1L));
    }

    private CommentatorRequestDTO request() {
        return new CommentatorRequestDTO("Commentator", "34999999992", BIRTHDAY, "raw-password");
    }

    private Commentator commentator(Long id, String password) {
        Commentator commentator = new Commentator();
        commentator.setId(id);
        commentator.setName("Commentator");
        commentator.setPhoneNumber("34999999992");
        commentator.setBirthdayDate(BIRTHDAY);
        commentator.setPassword(password);
        return commentator;
    }

    private CommentatorResponseDTO response(Long id) {
        return new CommentatorResponseDTO(id, "Commentator", "34999999992", BIRTHDAY);
    }
}
