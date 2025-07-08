package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.PadreRequestDTO;
import com.eventoscelebrativos.dto.response.PadreResponseDTO;
import com.eventoscelebrativos.model.Padre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PadreMapper {

    @Mapping(target = "id", ignore = true)
    Padre toEntity(PadreRequestDTO padreRequestDTO);

    PadreResponseDTO toDto(Padre padre);

    List<PadreResponseDTO> toDtoList(List<Padre> padres);

    @Mapping(target = "id", ignore = true)
    void atualizarPadreFromDto(PadreRequestDTO padreRequestDTO, @MappingTarget Padre padre);
}
