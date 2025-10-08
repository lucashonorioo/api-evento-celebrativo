package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CelebrationEventMapper {

    @Mapping(target = "id", ignore = true)
    CelebrationEvent toEntity(CelebrationEventRequestDTO celebrationEventRequestDTO);

    CelebrationEventResponseDTO toDto(CelebrationEvent celebrationEvent);

    List<CelebrationEventResponseDTO> toDtoList(List<CelebrationEvent> celebrationEvents);

    @Mapping(target = "id", ignore = true)
    void updateCelebrationEventMapperFromDto(CelebrationEventRequestDTO celebrationEventRequestDTO, @MappingTarget CelebrationEvent celebrationEvent);
}
