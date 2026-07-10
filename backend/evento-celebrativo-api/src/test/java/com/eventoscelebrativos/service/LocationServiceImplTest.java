package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import com.eventoscelebrativos.mapper.LocationMapper;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.service.impl.LocationServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    @Mock
    private LocationRepository repository;

    @Mock
    private LocationMapper mapper;

    @InjectMocks
    private LocationServiceImpl service;

    @Test
    void shouldCreateLocation() {
        LocationRequestDTO request = request();
        Location entity = location(null);
        Location saved = location(1L);
        LocationResponseDTO response = response(1L);

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.createLocation(request));
    }

    @Test
    void shouldFindLocationByIdWhenExists() {
        Location entity = location(1L);
        LocationResponseDTO response = response(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(response);

        assertSame(response, service.findLocationById(1L));
    }

    @Test
    void shouldThrowBusinessExceptionWhenLocationIdIsInvalid() {
        assertAll(
                () -> assertThrows(BusinessException.class, () -> service.findLocationById(null)),
                () -> assertThrows(BusinessException.class, () -> service.findLocationById(0L)),
                () -> assertThrows(BusinessException.class, () -> service.findLocationById(-1L))
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenLocationDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findLocationById(99L));
    }

    @Test
    void shouldListLocations() {
        List<Location> entities = List.of(location(1L));
        List<LocationResponseDTO> responses = List.of(response(1L));
        when(repository.findAll()).thenReturn(entities);
        when(mapper.toDtoList(entities)).thenReturn(responses);

        assertSame(responses, service.findAllLocations());
    }

    @Test
    void shouldUpdateLocationWhenExists() {
        LocationRequestDTO request = request();
        Location entity = location(1L);
        Location saved = location(1L);
        LocationResponseDTO response = response(1L);

        when(repository.getReferenceById(1L)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        assertSame(response, service.updateLocation(1L, request));
        verify(mapper).updateLocationFromDto(request, entity);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingLocation() {
        when(repository.getReferenceById(99L)).thenThrow(new EntityNotFoundException());

        assertThrows(ResourceNotFoundException.class, () -> service.updateLocation(99L, request()));
    }

    @Test
    void shouldDeleteLocationWhenExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteLocationById(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void shouldThrowResourceNotFoundWhenDeletingMissingLocation() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteLocationById(99L));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void shouldThrowDatabaseExceptionWhenDeletingReferencedLocation() {
        when(repository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("constraint")).when(repository).flush();

        assertThrows(DatabaseException.class, () -> service.deleteLocationById(1L));
    }

    private LocationRequestDTO request() {
        return new LocationRequestDTO("Igreja Matriz", "Rua Central, 100");
    }

    private Location location(Long id) {
        Location location = new Location();
        location.setId(id);
        location.setChurchName("Igreja Matriz");
        location.setAddress("Rua Central, 100");
        return location;
    }

    private LocationResponseDTO response(Long id) {
        return new LocationResponseDTO(id, "Igreja Matriz", "Rua Central, 100");
    }
}
