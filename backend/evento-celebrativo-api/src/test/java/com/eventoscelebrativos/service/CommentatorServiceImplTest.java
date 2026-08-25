package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.request.CommentatorUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CommentatorMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.impl.CommentatorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentatorServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1991, 2, 11);
    private static final String ENTITY_LABEL = "Comentarista";

    @Mock
    private CommentatorMapper mapper;

    @Mock
    private PersonAccountCoordinator personAccountCoordinator;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonCadastralUpdateService personCadastralUpdateService;

    @InjectMocks
    private CommentatorServiceImpl service;

    @Test
    void shouldCreateCommentatorAndProvisionAccessAfterPersisting() {
        CommentatorRequestDTO request = request();
        Person entity = commentator(null);
        Person saved = commentator(1L);
        CommentatorResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.COMMENTATOR)).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createCommentator(request));

        verify(personMinistryCommandService).create(entity, MinistryType.COMMENTATOR);
        verify(personAccountCoordinator).provisionAccess(saved, request);
    }

    @Test
    void shouldPropagateProvisioningFailureAfterPersonAlreadyCreated() {
        CommentatorRequestDTO request = request();
        Person entity = commentator(null);
        Person saved = commentator(1L);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(personMinistryCommandService.create(entity, MinistryType.COMMENTATOR)).thenReturn(saved);
        doThrow(new ResourceNotFoundException("Perfil de acesso", "ROLE_OPERATOR"))
                .when(personAccountCoordinator).provisionAccess(saved, request);

        assertThrows(ResourceNotFoundException.class, () -> service.createCommentator(request));
        verify(personMinistryCommandService).create(entity, MinistryType.COMMENTATOR);
    }

    @Test
    void shouldFindCommentatorByIdWhenExists() {
        Person entity = commentator(1L);
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
        Person entity = commentator(1L);
        CommentatorResponseDTO response = response(1L);
        CommentatorUpdateRequestDTO request = updateRequest();

        when(personMinistryCommandService.requireActiveMinistryPersonForUpdate(1L, MinistryType.COMMENTATOR, ENTITY_LABEL)).thenReturn(entity);
        when(personCadastralUpdateService.updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        )).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updateCommentator(1L, request));
        verify(personCadastralUpdateService).updateCadastral(
                entity,
                request.getName(),
                request.getPhoneNumber(),
                request.getBirthdayDate()
        );
        verifyNoInteractions(personAccountCoordinator);

        service.deleteCommentatorById(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.COMMENTATOR, ENTITY_LABEL);
    }

    @Test
    void shouldListCommentatorsUsingPersonMinistryWithoutCallingLegacyRepository() {
        Person commentator = commentator(1L);
        Person readerWithCommentatorMinistry = reader(2L);
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
        Person readerWithCommentatorMinistry = reader(2L);
        Person commentator = commentator(1L);
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
        assertThrows(ResourceNotFoundException.class, () -> service.updateCommentator(99L, updateRequest()));

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

    private CommentatorUpdateRequestDTO updateRequest() {
        return new CommentatorUpdateRequestDTO("Commentator Updated", "34888888882", LocalDate.of(1992, 3, 12));
    }

    private Person commentator(Long id) {
        Person commentator = new Person("Commentator", "34999999992", BIRTHDAY);
        ReflectionTestUtils.setField(commentator, "id", id);
        return commentator;
    }

    private CommentatorResponseDTO response(Long id) {
        return new CommentatorResponseDTO(id, "Commentator", "34999999992", BIRTHDAY);
    }

    private Person reader(Long id) {
        Person reader = new Person("Reader", "34999999991", BIRTHDAY);
        ReflectionTestUtils.setField(reader, "id", id);
        return reader;
    }
}
