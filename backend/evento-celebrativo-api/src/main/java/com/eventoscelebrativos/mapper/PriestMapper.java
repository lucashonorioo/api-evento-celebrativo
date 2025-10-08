package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.model.Priest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriestMapper {

    @Mapping(target = "id", ignore = true)
    Priest toEntity(PriestRequestDTO priestRequestDTO);

    PriestResponseDTO toDto(Priest priest);

    List<PriestResponseDTO> toDtoList(List<Priest> priests);

    @Mapping(target = "id", ignore = true)
    void updatePriestFromDto(PriestRequestDTO priestRequestDTO, @MappingTarget Priest priest);
}
