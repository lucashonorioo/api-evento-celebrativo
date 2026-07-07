package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.CelebrationEventMapper;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.projection.EucharistScaleEventProjection;
import com.eventoscelebrativos.repository.CelebrationEventRepository;
import com.eventoscelebrativos.service.impl.CelebrationEventServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CelebrationEventServiceImplTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalTime EVENT_TIME = LocalTime.of(19, 30);

    @Mock
    private CelebrationEventRepository repository;

    @Mock
    private CelebrationEventMapper mapper;

    @InjectMocks
    private CelebrationEventServiceImpl service;

    @Test
    void shouldCreateEvent() {
        CelebrationEventRequestDTO request = request();
        CelebrationEvent entity = event(null);
        CelebrationEvent saved = event(1L);
        CelebrationEventResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createEvent(request));
    }

    @Test
    void shouldFindEventByIdWhenExists() {
        CelebrationEvent entity = event(1L);
        CelebrationEventResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findEventById(1L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenEventIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findEventById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findEventById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findEventById(-1L))
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenEventDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findEventById(99L));
    }

    @Test
    void shouldListEvents() {
        List<CelebrationEvent> entities = List.of(event(1L));
        List<CelebrationEventResponseDTO> responses = List.of(response(1L));
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllEvents());
    }

    @Test
    void shouldMapEucharistScaleProjectionToResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        EucharistScaleEventProjection projection = projection("Missa", EVENT_DATE, EVENT_TIME, "Igreja Matriz", "Ana, Bruno");
        when(repository.findEucharistScale(pageable, startDate, endDate))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));

        Page<EucharistScaleEventResponseDTO> result = service.findEucharistScale(pageable, startDate, endDate);

        assertEquals(1, result.getTotalElements());
        EucharistScaleEventResponseDTO dto = result.getContent().get(0);
        assertEquals("Missa", dto.getNameMassOrEvent());
        assertEquals(EVENT_DATE, dto.getEventDate());
        assertEquals(EVENT_TIME, dto.getEventTime());
        assertEquals("Igreja Matriz", dto.getChurchName());
        assertEquals(List.of("Ana", "Bruno"), dto.getNameMinisters());
    }

    @Test
    void shouldThrowBusinessExceptionWhenEucharistScalePeriodIsInvalid() {
        PageRequest pageable = PageRequest.of(0, 10);

        assertAll(
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, null, EVENT_DATE)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, EVENT_DATE, null)),
                () -> assertThrows(BusinessException.class,
                        () -> service.findEucharistScale(pageable, EVENT_DATE.plusDays(1), EVENT_DATE))
        );
    }

    @Test
    void shouldUpdateEventWhenExists() {
        CelebrationEventRequestDTO request = request();
        CelebrationEvent entity = event(1L);
        CelebrationEvent saved = event(1L);
        CelebrationEventResponseDTO response = response(1L);

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.updateEvent(1L, request));
        verify(mapper).updateCelebrationEventMapperFromDto(request, entity);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingEvent() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());

        assertThrows(ResourceNotFoundException.class, () -> service.updateEvent(99L, request()));
    }

    @Test
    void shouldDeleteEventWhenExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteEventById(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingEvent() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteEventById(99L));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedEvent() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).deleteById(1L);

        assertThrows(DatabaseException.class, () -> service.deleteEventById(1L));
    }

    private CelebrationEventRequestDTO request() {
        return new CelebrationEventRequestDTO("Missa", EVENT_DATE, EVENT_TIME, true);
    }

    private CelebrationEvent event(Long id) {
        CelebrationEvent event = new CelebrationEvent();
        event.setId(id);
        event.setNameMassOrEvent("Missa");
        event.setEventDate(EVENT_DATE);
        event.setEventTime(EVENT_TIME);
        event.setMassOrCelebration(true);
        return event;
    }

    private CelebrationEventResponseDTO response(Long id) {
        return new CelebrationEventResponseDTO(id, "Missa", EVENT_DATE, EVENT_TIME, true);
    }

    private EucharistScaleEventProjection projection(
            String nameMassOrEvent,
            LocalDate eventDate,
            LocalTime eventTime,
            String churchName,
            String ministerNames
    ) {
        return new EucharistScaleEventProjection() {
            @Override
            public String getNameMassOrEvent() {
                return nameMassOrEvent;
            }

            @Override
            public LocalDate getEventDate() {
                return eventDate;
            }

            @Override
            public LocalTime getEventTime() {
                return eventTime;
            }

            @Override
            public String getChurchName() {
                return churchName;
            }

            @Override
            public String getMinisterNames() {
                return ministerNames;
            }
        };
    }
}
