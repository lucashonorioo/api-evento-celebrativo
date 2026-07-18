package com.eventoscelebrativos.service;

import com.eventoscelebrativos.config.PersonMinistryShadowReadProperties;
import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.MinistryTypeResolver;
import com.eventoscelebrativos.service.PersonMinistryCompatibilityService;
import com.eventoscelebrativos.service.impl.EucharisticMinisterServiceImpl;
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
class EucharisticMinisterServiceImplTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1992, 3, 12);

    @Mock
    private EucharisticMinisterRepository repository;

    @Mock
    private EucharisticMinisterMapper mapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonMinistryCompatibilityService personMinistryCompatibilityService;

    @Mock
    private MinistryTypeResolver ministryTypeResolver;

    @Mock
    private PersonMinistryShadowReadExecutor personMinistryShadowReadExecutor;

    @Mock
    private PersonMinistryShadowReadProperties shadowReadProperties;

    @InjectMocks
    private EucharisticMinisterServiceImpl service;

    @Test
    void shouldCreateEucharisticMinisterWithEncryptedPasswordAndOperatorRole() {
        EucharisticMinisterRequestDTO request = request();
        EucharisticMinister entity = minister(null, "raw-password");
        Role operatorRole = new Role(1L, "ROLE_OPERATOR");
        EucharisticMinister saved = minister(1L, "encoded-password");
        EucharisticMinisterResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.of(operatorRole));
        when(repository.save(any(EucharisticMinister.class))).thenReturn(saved);
        when(ministryTypeResolver.resolve(saved)).thenReturn(MinistryType.EUCHARISTIC_MINISTER);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createEucharisticMinister(request));

        ArgumentCaptor<EucharisticMinister> captor = ArgumentCaptor.forClass(EucharisticMinister.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
        verify(personMinistryCompatibilityService).ensureMinistry(saved, MinistryType.EUCHARISTIC_MINISTER);
    }

    @Test
    void shouldThrowResourceNotFoundWhenOperatorRoleDoesNotExist() {
        EucharisticMinisterRequestDTO request = request();
        when(mapper.toEntity(request)).thenReturn(minister(null, "raw-password"));
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(roleRepository.findByAuthority("ROLE_OPERATOR")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createEucharisticMinister(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindEucharisticMinisterByIdWhenExists() {
        EucharisticMinister entity = minister(1L, "encoded-password");
        EucharisticMinisterResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEucharisticMinistersById(1L));
    }

    @Test
    void shouldThrowWhenEucharisticMinisterIdIsInvalidOrMissing() {
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(null));
        assertThrows(BusinessException.class, () -> service.findEucharisticMinistersById(0L));
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findEucharisticMinistersById(99L));
    }

    @Test
    void shouldListUpdateAndDeleteEucharisticMinister() {
        EucharisticMinister entity = minister(1L, "old-password");
        EucharisticMinisterResponseDTO response = response(1L);
        List<EucharisticMinister> entities = List.of(entity);
        List<EucharisticMinisterResponseDTO> responses = List.of(response);

        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);
        assertSame(responses, service.findAllEucharisticMinisters());
        verify(personMinistryShadowReadExecutor).execute(
                false,
                MinistryType.EUCHARISTIC_MINISTER,
                entities,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(ministryTypeResolver.resolve(entity)).thenReturn(MinistryType.EUCHARISTIC_MINISTER);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updateEucharisticMinisters(1L, request()));
        assertEquals("encoded-password", entity.getPassword());
        verify(personMinistryCompatibilityService).ensureMinistry(entity, MinistryType.EUCHARISTIC_MINISTER);

        when(repository.existsById(1L)).thenReturn(true);
        service.deleteEucharisticMinisterById(1L);
        var inOrder = inOrder(personMinistryCompatibilityService, repository);
        inOrder.verify(personMinistryCompatibilityService).deleteAllForPerson(1L);
        inOrder.verify(repository).deleteById(1L);
    }

    @Test
    void shouldRunEucharisticMinisterShadowReadWhenEnabledAndKeepLegacyResponse() {
        EucharisticMinister entity = minister(1L, "encoded-password");
        EucharisticMinisterResponseDTO response = response(1L);
        List<EucharisticMinister> entities = List.of(entity);
        List<EucharisticMinisterResponseDTO> responses = List.of(response);

        when(shadowReadProperties.isEucharisticMinisterEnabled()).thenReturn(true);
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllEucharisticMinisters());
        verify(personMinistryShadowReadExecutor).execute(
                true,
                MinistryType.EUCHARISTIC_MINISTER,
                entities,
                PersonMinistryShadowReadComparisonOptions.unorderedList()
        );
    }

    @Test
    void shouldPropagateLegacyEucharisticMinisterListFailureWithoutUsingShadowReadAsFallback() {
        RuntimeException legacyFailure = new IllegalStateException("legacy read failed");

        when(repository.findAll()).thenThrow(legacyFailure);

        assertSame(legacyFailure, assertThrows(RuntimeException.class, () -> service.findAllEucharisticMinisters()));
        verifyNoInteractions(personMinistryShadowReadExecutor, mapper);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingEucharisticMinister() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> service.updateEucharisticMinisters(99L, request()));

        when(repository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteEucharisticMinisterById(99L));
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEucharisticMinister() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteEucharisticMinisterById(1L));
    }

    private EucharisticMinisterRequestDTO request() {
        return new EucharisticMinisterRequestDTO("Minister", "34999999993", BIRTHDAY, "raw-password");
    }

    private EucharisticMinister minister(Long id, String password) {
        EucharisticMinister minister = new EucharisticMinister();
        minister.setId(id);
        minister.setName("Minister");
        minister.setPhoneNumber("34999999993");
        minister.setBirthdayDate(BIRTHDAY);
        minister.setPassword(password);
        return minister;
    }

    private EucharisticMinisterResponseDTO response(Long id) {
        return new EucharisticMinisterResponseDTO(id, "Minister", "34999999993", BIRTHDAY);
    }
}
