package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.model.EventoCelebrativo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventoCelebrativoMapper {

    @Mapping(target = "id", ignore = true)
    EventoCelebrativo toEntity(EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO);

    EventoCelebrativoResponseDTO toDto(EventoCelebrativo eventoCelebrativo);

    List<EventoCelebrativoResponseDTO> toDtoList(List<EventoCelebrativo> eventoCelebrativos);

    @Mapping(target = "id", ignore = true)
    void atualizarEventoCelebrativoMapperFromDto(EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO, @MappingTarget EventoCelebrativo eventoCelebrativo);
}
