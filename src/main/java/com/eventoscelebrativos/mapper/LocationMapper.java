package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;
import com.eventoscelebrativos.model.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    @Mapping(target = "id", ignore = true)
    Location toEntity(LocationRequestDTO locationRequestDTO);

    LocationResponseDTO toDto(Location location);

    List<LocationResponseDTO> toDtoList(List<Location> locations);

    @Mapping(target = "id", ignore = true)
    void updateLocationFromDto(LocationRequestDTO locationRequestDTO, @MappingTarget Location location);
}
