package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.PersonMinistryReadSource;
import com.eventoscelebrativos.config.PersonMinistryReadSourceProperties;
import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.impl.PriestServiceImpl;
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
class PriestServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1980, 5, 14);

    @Mock
    private PriestRepository repository;

    @Mock
    private PriestMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Mock
    private MinistryTypeResolver ministryTypeResolver;

    @Mock
    private PersonMinistryReadService personMinistryReadService;

    @Mock
    private PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor;

    @Mock
    private PersonMinistryReadSourceProperties readSourceProperties;

    @Mock
    private PersonMinistryShadowReadProperties shadowReadProperties;

    @InjectMocks
    private PriestServiceImpl service;

    @Test
    void shouldCreatePriestWithEncryptedPasswordAndOperatorRole() {
        PriestRequestDTO request = request();
        Priest entity = priest(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        Priest saved = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(repository.save(any(Priest.class))).thenReturn(saved);
        when(ministryTypeResolver.resolve(saved)).thenReturn(MinistryType.PRIEST);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createPriest(request));

        ArgumentCaptor<Priest> captor = ArgumentCaptor.forClass(Priest.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
        verify(personMinistryCompatibilityService).ensureMinistry(saved, MinistryType.PRIEST);
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        PriestRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(priest(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createPriest(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindPriestByIdWhenExists() {
        Priest entity = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findPriestById(1L));
    }

    @Test
    void shouldThrowWhenPriestIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findPriestById(null));
        assertThrows(BusinessException.class, () -> service.findPriestById(0L));
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findPriestById(99L));
    }

    @Test
    void shouldListUpdateAndDeletePriest() {
        Priest entity = priest(1L, "old-password");
        PriestResponseDTO response = response(1L);
        List<Priest> entities = List.of(entity);
        List<PriestResponseDTO> responses = List.of(response);

        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);
        assertSame(responses, service.findAllPriests());
        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadExecutor);

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(ministryTypeResolver.resolve(entity)).thenReturn(MinistryType.PRIEST);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updatePriest(1L, request()));
        assertEquals("encoded-password", entity.getPassword());
        verify(personMinistryCompatibilityService).ensureMinistry(entity, MinistryType.PRIEST);

        when(repository.existsById(1L)).thenReturn(true);
        service.deletePriestById(1L);
        var inOrder = inOrder(personMinistryCompatibilityService, repository);
        inOrder.verify(personMinistryCompatibilityService).deleteAllForPerson(1L);
        inOrder.verify(repository).deleteById(1L);
    }

    @Test
    void shouldListPriestsWithShadowReadDisabledUsingLegacyRepository() {
        Priest entity = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);
        List<Priest> entities = List.of(entity);
        List<PriestResponseDTO> responses = List.of(response);

        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());

        verify(repository).findAll();
        verify(mapper).toDtoList(entities);
        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadExecutor);
    }

    @Test
    void shouldRunPriestShadowReadWhenEnabledAndKeepLegacyResponse() {
        Priest entity = priest(1L, "encoded-password");
        PriestResponseDTO response = response(1L);
        List<Priest> entities = List.of(entity);
        List<PriestResponseDTO> responses = List.of(response);

        when(shadowReadProperties.isPriestEnabled()).thenReturn(true);
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());
        verify(personMinistryShadowReadExecutor).execute(
                true,
                MinistryType.PRIEST,
                entities,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
        verifyNoInteractions(personMinistryReadService);
    }

    @Test
    void shouldUseParallelPriestReadSourceWithoutCallingLegacyRepository() {
        Priest priest = priest(1L, "encoded-password");
        Reader readerWithPriestMinistry = reader(2L, "encoded-password");
        List<Person> people = List.of(priest, readerWithPriestMinistry);
        List<PriestResponseDTO> responses = List.of(response(1L), response(2L));

        when(readSourceProperties.getPriest()).thenReturn(PersonMinistryReadSource.PARALLEL);
        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());

        verify(personMinistryReadService).findAllActivePeopleByMinistry(MinistryType.PRIEST);
        verify(mapper).toDtoPersonList(people);
        verifyNoInteractions(repository, personMinistryShadowReadExecutor);
    }

    @Test
    void shouldPreserveParallelPriestOrderReturnedByPersonMinistryReadService() {
        Reader readerWithPriestMinistry = reader(2L, "encoded-password");
        Priest priest = priest(1L, "encoded-password");
        List<Person> people = List.of(readerWithPriestMinistry, priest);
        List<PriestResponseDTO> responses = List.of(response(2L), response(1L));

        when(readSourceProperties.getPriest()).thenReturn(PersonMinistryReadSource.PARALLEL);
        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST)).thenReturn(people);
        when(mapper.toDtoPersonList(people)).thenReturn(responses);

        assertSame(responses, service.findAllPriests());

        verify(mapper).toDtoPersonList(people);
        assertEquals(List.of(2L, 1L), people.stream().map(Person::getId).toList());
    }

    @Test
    void shouldPropagateOfficialParallelFailureWithoutUsingLegacyFallback() {
        RuntimeException parallelFailure = new IllegalStateException("parallel read failed");

        when(readSourceProperties.getPriest()).thenReturn(PersonMinistryReadSource.PARALLEL);
        when(personMinistryReadService.findAllActivePeopleByMinistry(MinistryType.PRIEST))
                .thenThrow(parallelFailure);

        assertSame(parallelFailure, assertThrows(RuntimeException.class, () -> service.findAllPriests()));
        verifyNoInteractions(repository, personMinistryShadowReadExecutor, mapper);
    }

    @Test
    void shouldPropagateLegacyPriestListFailureWithoutUsingShadowReadAsFallback() {
        RuntimeException legacyFailure = new IllegalStateException("legacy read failed");

        when(repository.findAll()).thenThrow(legacyFailure);

        assertSame(legacyFailure, assertThrows(RuntimeException.class, () -> service.findAllPriests()));
        verifyNoInteractions(personMinistryReadService, personMinistryShadowReadExecutor, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingPriest() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> service.updatePriest(99L, request()));

        when(repository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deletePriestById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedPriest() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deletePriestById(1L));
    }

    private PriestRequestDTO request() {
        return new PriestRequestDTO("Priest", "34999999995", BIRTHDAY, "raw-password");
    }

    private Priest priest(Long id, String password) {
        Priest priest = new Priest();
        priest.setId(id);
        priest.setName("Priest");
        priest.setPhoneNumber("34999999995");
        priest.setBirthdayDate(BIRTHDAY);
        priest.setPassword(password);
        return priest;
    }

    private PriestResponseDTO response(Long id) {
        return new PriestResponseDTO(id, "Priest", "34999999995", BIRTHDAY);
    }

    private Reader reader(Long id, String password) {
        Reader reader = new Reader();
        reader.setId(id);
        reader.setName("Reader");
        reader.setPhoneNumber("34999999991");
        reader.setBirthdayDate(BIRTHDAY);
        reader.setPassword(password);
        return reader;
    }
}
