package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.MinisterOfTheWordServiceImpl;
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
class MinisterOfTheWordServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1993, 4, 13);
    private static final String FIND_ENTITY_LABEL = "Ministro Da Palavra";
    private static final String MUTATION_ENTITY_LABEL = "Ministro da Palavra";

    @Mock
    private MinisterOfTheWordMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCommandService personMinistryCommandService;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

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
        when(personMinistryCommandService.create(any(Person.class), eq(MinistryType.MINISTER_OF_THE_WORD))).thenReturn(saved);
        when(mapper.toDtoFromPerson(saved)).thenReturn(response);

        assertSame(response, service.createMinisterOfTheWord(request));

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMinistryCommandService).create(captor.capture(), eq(MinistryType.MINISTER_OF_THE_WORD));
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        MinisterOfTheWordRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(minister(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createMinisterOfTheWord(request));
        verify(personMinistryCommandService, never()).create(any(), any());
    }

    @Test
    void shouldFindMinisterOfTheWordByIdWhenExists() {
        MinisterOfTheWord entity = minister(1L, "encoded-password");
        MinisterOfTheWordResponseDTO response = response(1L);
        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.MINISTER_OF_THE_WORD, FIND_ENTITY_LABEL)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);

        assertSame(response, service.findMinisterOfTheWordById(1L));
    }

    @Test
    void shouldThrowWhenMinisterOfTheWordIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(null));
        assertThrows(BusinessException.class, () -> service.findMinisterOfTheWordById(0L));
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.MINISTER_OF_THE_WORD, FIND_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(FIND_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.findMinisterOfTheWordById(99L));
    }

    @Test
    void shouldUpdateAndDeleteMinisterOfTheWord() {
        MinisterOfTheWord entity = minister(1L, "old-password");
        MinisterOfTheWordResponseDTO response = response(1L);

        when(personMinistryCommandService.requireActiveMinistryPerson(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(personMinistryCommandService.save(entity)).thenReturn(entity);
        when(mapper.toDtoFromPerson(entity)).thenReturn(response);
        assertSame(response, service.updateMinisterOfTheWord(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        service.deleteMinisterOfTheWord(1L);
        verify(personMinistryCommandService).removeMinistry(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);
    }

    @Test
    void shouldListMinistersOfTheWordUsingPersonMinistryWithoutCallingLegacyRepository() {
        Reader readerWithMinisterOfTheWordMinistry = reader(2L, "encoded-password");
        MinisterOfTheWordResponseDTO response = new MinisterOfTheWordResponseDTO(
                2L,
                "Minister",
                "34999999994",
                BIRTHDAY
        );
        List<Person> people = List.of(readerWithMinisterOfTheWordMinistry);
        List<MinisterOfTheWordResponseDTO> responses = List.of(response);

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllMinistersOfTheWord());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPreserveMinisterOfTheWordOrderReturnedByPersonMinistryReadService() {
        MinisterOfTheWord first = minister(1L, "encoded-password");
        Reader second = reader(2L, "encoded-password");
        List<Person> people = List.of(first, second);
        List<MinisterOfTheWordResponseDTO> responses = List.of(response(1L), response(2L));

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllMinistersOfTheWord());

        verifyNoInteractions(personMinistryCommandService);
    }

    @Test
    void shouldPropagateOfficialReadFailureWithoutFallback() {
        RuntimeException officialFailure = new IllegalStateException("official read failed");

        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.MINISTER_OF_THE_WORD))
                .thenThrow(officialFailure);

        assertSame(officialFailure, assertThrows(RuntimeException.class, () -> service.findAllMinistersOfTheWord()));
        verifyNoInteractions(personMinistryCommandService, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingMinisterOfTheWord() {
        when(personMinistryCommandService.requireActiveMinistryPerson(99L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL))
                .thenThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateMinisterOfTheWord(99L, request()));

        doThrow(new ResourceNotFoundException(MUTATION_ENTITY_LABEL, 99L))
                .when(personMinistryCommandService).removeMinistry(99L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteMinisterOfTheWord(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedMinisterOfTheWord() {
        doThrow(new DatabaseException("Não é possível excluir este registro, pois ele possui vínculos com outros cadastros."))
                .when(personMinistryCommandService).removeMinistry(1L, MinistryType.MINISTER_OF_THE_WORD, MUTATION_ENTITY_LABEL);

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

    private Reader reader(Long id, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Minister");
        reader.setPhoneNumber("34999999994");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }

    private MinisterOfTheWordResponseDTO response(Long id) {
        return new MinisterOfTheWordResponseDTO(id, "Minister", "34999999994", BIRTHDAY);
    }
}
