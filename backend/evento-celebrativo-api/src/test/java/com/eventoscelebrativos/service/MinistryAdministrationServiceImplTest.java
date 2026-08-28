package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistryRequestDTO;
import com.eventoscelebrativos.dto.request.MinistryStatusUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.exception.exceptions.LifecycleConflictException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.model.Ministry;
import com.eventoscelebrativos.repository.MinistryRepository;
import com.eventoscelebrativos.repository.PersonMinistryRepository;
import com.eventoscelebrativos.service.impl.MinistryAdministrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryAdministrationServiceImplTest {

    @Mock
    private MinistryRepository ministryRepository;

    @Mock
    private PersonMinistryRepository personMinistryRepository;

    private MinistryAdministrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MinistryAdministrationServiceImpl(ministryRepository, personMinistryRepository);
    }

    @Test
    void shouldFindAllOrderedFromRepository() {
        Ministry acolytes = ministry(10L, "Acolitos");
        Ministry readers = ministry(2L, "Leitores");
        when(ministryRepository.findAllByOrderByNameAscIdAsc()).thenReturn(List.of(acolytes, readers));

        List<MinistryResponseDTO> response = service.findAll();

        assertEquals(List.of(10L, 2L), response.stream().map(MinistryResponseDTO::getId).toList());
        assertEquals(List.of("Acolitos", "Leitores"), response.stream().map(MinistryResponseDTO::getName).toList());
        assertEquals(List.of(true, true), response.stream().map(MinistryResponseDTO::isActive).toList());
    }

    @Test
    void shouldFindById() {
        Ministry ministry = ministry(10L, "Acolitos");
        when(ministryRepository.findById(10L)).thenReturn(Optional.of(ministry));

        MinistryResponseDTO response = service.findById(10L);

        assertEquals(10L, response.getId());
        assertEquals("Acolitos", response.getName());
        assertTrue(response.isActive());
    }

    @Test
    void shouldReturnNotFoundWhenFindByIdMissing() {
        when(ministryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));
    }

    @Test
    void shouldRejectInvalidId() {
        assertThrows(BadRequestException.class, () -> service.findById(null));
        assertThrows(BadRequestException.class, () -> service.findById(0L));
        assertThrows(BadRequestException.class, () -> service.rename(-1L, new MinistryRequestDTO("Nome")));
        assertThrows(BadRequestException.class, () -> service.updateStatus(-1L, new MinistryStatusUpdateRequestDTO(true)));

        verifyNoInteractions(ministryRepository, personMinistryRepository);
    }

    @Test
    void shouldCreateActiveMinistryWithNormalizedName() {
        when(ministryRepository.saveAndFlush(any(Ministry.class))).thenAnswer(invocation -> {
            Ministry ministry = invocation.getArgument(0);
            ReflectionTestUtils.setField(ministry, "id", 11L);
            return ministry;
        });

        MinistryResponseDTO response = service.create(new MinistryRequestDTO("  Acólitos   e   Coroinhas  "));

        ArgumentCaptor<Ministry> captor = ArgumentCaptor.forClass(Ministry.class);
        verify(ministryRepository).saveAndFlush(captor.capture());
        assertEquals("Acólitos e Coroinhas", captor.getValue().getName());
        assertEquals("ACOLITOS E COROINHAS", captor.getValue().getNormalizedName());
        assertTrue(captor.getValue().isActive());
        assertEquals(11L, response.getId());
        assertEquals("Acólitos e Coroinhas", response.getName());
        assertTrue(response.isActive());
    }

    @Test
    void shouldTranslateDuplicateCreateToConflict() {
        when(ministryRepository.saveAndFlush(any(Ministry.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> service.create(new MinistryRequestDTO("Leitores")));

        assertEquals("MINISTRY_NAME_ALREADY_EXISTS", exception.getErrorCode());
    }

    @Test
    void shouldRenameWithoutChangingIdOrStatus() {
        Ministry ministry = ministry(2L, "Leitores");
        when(ministryRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(ministry));
        when(ministryRepository.saveAndFlush(ministry)).thenReturn(ministry);

        MinistryResponseDTO response = service.rename(2L, new MinistryRequestDTO("Leitores e Salmistas"));

        assertEquals(2L, response.getId());
        assertEquals("Leitores e Salmistas", response.getName());
        assertEquals("LEITORES E SALMISTAS", ministry.getNormalizedName());
        assertTrue(response.isActive());
    }

    @Test
    void shouldTranslateDuplicateRenameToConflict() {
        Ministry ministry = ministry(2L, "Leitores");
        when(ministryRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(ministry));
        when(ministryRepository.saveAndFlush(ministry)).thenThrow(new DataIntegrityViolationException("duplicate"));

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> service.rename(2L, new MinistryRequestDTO("Comentaristas")));

        assertEquals("MINISTRY_NAME_ALREADY_EXISTS", exception.getErrorCode());
    }

    @Test
    void shouldRejectInvalidName() {
        assertThrows(BadRequestException.class, () -> service.create(new MinistryRequestDTO(" ")));
        assertThrows(BadRequestException.class, () -> service.create(null));
    }

    @Test
    void shouldDeactivateWhenNoActiveMembershipExists() {
        Ministry ministry = ministry(10L, "Acolitos");
        when(ministryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ministry));
        when(personMinistryRepository.existsByMinistryIdAndActiveTrue(10L)).thenReturn(false);
        when(ministryRepository.saveAndFlush(ministry)).thenReturn(ministry);

        MinistryResponseDTO response = service.updateStatus(10L, new MinistryStatusUpdateRequestDTO(false));

        assertFalse(response.isActive());
        verify(personMinistryRepository).existsByMinistryIdAndActiveTrue(10L);
        verify(ministryRepository).saveAndFlush(ministry);
    }

    @Test
    void shouldReturnInactiveMinistryWithoutWriteWhenDeactivationIsIdempotent() {
        Ministry ministry = ministry(10L, "Acolitos");
        ministry.deactivate();
        when(ministryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ministry));

        MinistryResponseDTO response = service.updateStatus(10L, new MinistryStatusUpdateRequestDTO(false));

        assertFalse(response.isActive());
        verify(personMinistryRepository, never()).existsByMinistryIdAndActiveTrue(any());
        verify(ministryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldBlockDeactivationWhenActiveMembershipExists() {
        Ministry ministry = ministry(2L, "Leitores");
        when(ministryRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(ministry));
        when(personMinistryRepository.existsByMinistryIdAndActiveTrue(2L)).thenReturn(true);

        LifecycleConflictException exception = assertThrows(LifecycleConflictException.class,
                () -> service.updateStatus(2L, new MinistryStatusUpdateRequestDTO(false)));

        assertEquals("MINISTRY_HAS_ACTIVE_MEMBERSHIPS", exception.getErrorCode());
        assertTrue(ministry.isActive());
        verify(ministryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReactivateInactiveMinistry() {
        Ministry ministry = ministry(10L, "Acolitos");
        ministry.deactivate();
        when(ministryRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(ministry));
        when(ministryRepository.saveAndFlush(ministry)).thenReturn(ministry);

        MinistryResponseDTO response = service.updateStatus(10L, new MinistryStatusUpdateRequestDTO(true));

        assertTrue(response.isActive());
        verify(personMinistryRepository, never()).existsByMinistryIdAndActiveTrue(any());
        verify(ministryRepository).saveAndFlush(ministry);
    }

    @Test
    void shouldRejectNullStatusRequest() {
        assertThrows(BadRequestException.class, () -> service.updateStatus(10L, null));
        assertThrows(BadRequestException.class,
                () -> service.updateStatus(10L, new MinistryStatusUpdateRequestDTO(null)));
    }

    private Ministry ministry(Long id, String name) {
        Ministry ministry = new Ministry(name);
        ReflectionTestUtils.setField(ministry, "id", id);
        return ministry;
    }
}
