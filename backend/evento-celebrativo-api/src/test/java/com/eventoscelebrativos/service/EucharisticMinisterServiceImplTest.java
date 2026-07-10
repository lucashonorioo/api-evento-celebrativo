package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.repository.RoleRepository;
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
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createEucharisticMinister(request));

        ArgumentCaptor<EucharisticMinister> captor = ArgumentCaptor.forClass(EucharisticMinister.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
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

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updateEucharisticMinisters(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        when(repository.existsById(1L)).thenReturn(true);
        service.deleteEucharisticMinisterById(1L);
        verify(repository).deleteById(1L);
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
