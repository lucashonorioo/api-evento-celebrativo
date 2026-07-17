package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.impl.MinisterOfTheWordServiceImpl;
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
class MinisterOfTheWordServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1993, 4, 13);

    @Mock
    private MinisterOfTheWordRepository repository;

    @Mock
    private MinisterOfTheWordMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Mock
    private MinistryTypeResolver ministryTypeResolver;

    @InjectMocks
    private MinisterOfTheWordServiceImpl service;

    @Test
    void shouldCreateMinisterOfTheWordWithEncryptedPasswordAndOperatorRole() {
        MinisterOfTheWordRequestDTO request = request();
        MinisterOfTheWord entity = minister(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        MinisterOfTheWord saved = minister(1L, "encoded-password");
        MinisterOfTheWordResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(repository.save(any(MinisterOfTheWord.class))).thenReturn(saved);
        when(ministryTypeResolver.resolve(saved)).thenReturn(MinistryType.MINISTER_OF_THE_WORD);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createMinisterOfTheWord(request));

        ArgumentCaptor<MinisterOfTheWord> captor = ArgumentCaptor.forClass(MinisterOfTheWord.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
        verify(personMinistryCompatibilityService).ensureMinistry(saved, MinistryType.MINISTER_OF_THE_WORD);
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        MinisterOfTheWordRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(minister(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createMinisterOfTheWord(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindMinisterOfTheWordByIdWhenExists() {
        MinisterOfTheWord entity = minister(1L, "encoded-password");
        MinisterOfTheWordResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findMinisterOfTheWordById(1L));
    }

    @Test
    void shouldThrowWhenMinisterOfTheWordIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(null));
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(0L));
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findMinisterOfTheWordById(99L));
    }

    @Test
    void shouldListUpdateAndDeleteMinisterOfTheWord() {
        MinisterOfTheWord entity = minister(1L, "old-password");
        MinisterOfTheWordResponseDTO response = response(1L);
        List<MinisterOfTheWord> entities = List.of(entity);
        List<MinisterOfTheWordResponseDTO> responses = List.of(response);

        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);
        assertSame(responses, service.findAllMinistersOfTheWord());

        when(repository.getReferenceById(1L)).thenReturn(entity);
        doAnswer(invocation -> {
            MinisterOfTheWord target = invocation.getArgument(1);
            target.setPassword("raw-password");
            return null;
        }).when(mapper).updateMinisterOfTheWordFromDto(any(), eq(entity));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(ministryTypeResolver.resolve(entity)).thenReturn(MinistryType.MINISTER_OF_THE_WORD);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updateMinisterOfTheWord(1L, request()));
        assertEquals("encoded-password", entity.getPassword());
        verify(personMinistryCompatibilityService).ensureMinistry(entity, MinistryType.MINISTER_OF_THE_WORD);

        when(repository.existsById(1L)).thenReturn(true);
        service.deleteMinisterOfTheWord(1L);
        var inOrder = inOrder(personMinistryCompatibilityService, repository);
        inOrder.verify(personMinistryCompatibilityService).deleteAllForPerson(1L);
        inOrder.verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingMinisterOfTheWord() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> service.updateMinisterOfTheWord(99L, request()));

        when(repository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteMinisterOfTheWord(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedMinisterOfTheWord() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteMinisterOfTheWord(1L));
    }

    private MinisterOfTheWordRequestDTO request() {
        return new MinisterOfTheWordRequestDTO("Minister", "34999999994", BIRTHDAY, "raw-password");
    }

    private MinisterOfTheWord minister(Long id, String password) {
        MinisterOfTheWord minister = new MinisterOfTheWord();
        minister.setId(id);
        minister.setName("Minister");
        minister.setPhoneNumber("34999999994");
        minister.setBirthdayDate(BIRTHDAY);
        minister.setPassword(password);
        return minister;
    }

    private MinisterOfTheWordResponseDTO response(Long id) {
        return new MinisterOfTheWordResponseDTO(id, "Minister", "34999999994", BIRTHDAY);
    }
}
