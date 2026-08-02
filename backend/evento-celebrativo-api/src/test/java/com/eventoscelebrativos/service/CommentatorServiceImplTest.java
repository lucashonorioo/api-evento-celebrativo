package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.CommentatorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentatorServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1991, 2, 11);
    private static final String ENTITY_LABEL = "Comentarista";

    @Mock
    private CommentatorMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private UserAccountLifecycleService userAccountLifecycleService;

    @InjectMocks
    private CommentatorServiceImpl service;

    @Test
    void shouldCreateCommentatorWithEncryptedPasswordAndOperatorRole() {
        CommentatorRequestDTO request = request();
        Person entity = commentator(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Person saved = commentator(1L, "encoded-password");
        CommentatorResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        doAnswer(invocation -> {
            entity.setPassword("encoded-password");
            entity.addRole(operatorRole);
            return null;
        }).when(userAccountLifecycleService).applyCreationAccess(entity, request);
        when(personMinistryCommandService.create(any(Person.class), eq(MinistryType.COMMENTATOR))).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createCommentator(request));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMinistryCommandService).create(captor.capture(), eq(MinistryType.COMMENTATOR));
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        CommentatorRequestDTO request = request();
        Person entity = commentator(null, "raw-password");
        when(mapper.toEntity(request)).thenReturn(entity);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(userAccountLifecycleService).applyCreationAccess(entity, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createCommentator(request));
        verify(personMinistryCommandService, never()).create(any(), any());
    }

    @Test
    void shouldFindCommentatorByIdWhenExists() {
        Person entity = commentator(1L, "encoded-password");
        CommentatorResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.COMMENTATOR, ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findCommentatorById(1L));
    }

    @Test
    void shouldThrowWhenCommentatorIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findCommentatorById(null));
        assertThrows(BusinessException.class, () -> service.findCommentatorById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.COMMENTATOR, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findCommentatorById(99L));
    }

    @Test
    void shouldUpdateAndDeleteCommentator() {
        Person entity = commentator(1L, "old-password");
        CommentatorResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.COMMENTATOR, ENTITY_LABEL)).thenReturn(entity);
        when(personMinistryCommandService.save(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        CommentatorRequestDTO request = request();
        doAnswer(invocation -> {
            entity.setPassword("encoded-password");
            return null;
        }).when(userAccountLifecycleService).applyMinisterialUpdateAccess(entity, request);
        assertSame(response, service.updateCommentator(1L, request));
        assertEquals("encoded-password", entity.getPassword());

        service.deleteCommentatorById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.COMMENTATOR, ENTITY_LABEL);
    }

    @Test
    void shouldListCommentatorsUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person commentator = commentator(1L, "encoded-password");
        Person readerWithCommentatorMinistry = reader(2L, "encoded-password");
        List<Person> people = List.of(commentator, readerWithCommentatorMinistry);
        List<CommentatorResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.COMMENTATOR)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllCommentators());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.COMMENTATOR);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveCommentatorOrderReturnedByPersonMinistryReadService() {
        Person readerWithCommentatorMinistry = reader(2L, "encoded-password");
        Person commentator = commentator(1L, "encoded-password");
        List<Person> people = List.of(readerWithCommentatorMinistry, commentator);
        List<CommentatorResponseDTO> responses = List.of(response(2L), response(1L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.COMMENTATOR)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllCommentators());

        verify(mapper).toDtoPersonList(people);
        assertEquals(List.of(2L, 1L), people.stream().map(Person::getId).toList());
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.COMMENTATOR))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllCommentators()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingCommentator() {
        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(99L, MinistryType.COMMENTATOR, ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateCommentator(99L, request()));

        doThrow(new ResourceNotFoundException(ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.COMMENTATOR, ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteCommentatorById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedCommentator() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.COMMENTATOR, ENTITY_LABEL);

        assertThrows(DatabaseException.class, () -> service.deleteCommentatorById(1L));
    }

    private CommentatorRequestDTO request() {
        return new CommentatorRequestDTO("Commentator", "34999999992", BIRTHDAY, "raw-password");
    }

    private Person commentator(Long id, String password) {
        Person commentator = new Person();
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

    private Person reader(Long id, String password) {
        Person reader = new Person();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }
}
