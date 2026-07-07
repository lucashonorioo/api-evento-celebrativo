package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Role;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.repository.RoleRepository;
import com.eventoscelebrativos.service.impl.PriestServiceImpl;
import jakarta.persistence.EntityNotFoundException;
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
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createPriest(request));

        ArgumentCaptor<Priest> captor = ArgumentCaptor.forClass(Priest.class);
        verify(repository).save(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertNotEquals("raw-password", captor.getValue().getPassword());
        assertTrue(captor.getValue().hasRole("ROLE_OPERATOR"));
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

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(response);
        assertSame(response, service.updatePriest(1L, request()));
        assertEquals("encoded-password", entity.getPassword());

        when(repository.existsById(1L)).thenReturn(true);
        service.deletePriestById(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowWhenUpdatingOrDeletingMissingPriest() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> service.updatePriest(99L, request()));

        when(repository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deletePriestById(99L));
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
}
