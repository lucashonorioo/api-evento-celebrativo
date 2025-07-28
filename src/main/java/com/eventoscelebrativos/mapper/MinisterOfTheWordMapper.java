package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MinisterOfTheWordMapper {

    @Mapping(target = "id", ignore = true)
    MinisterOfTheWord toEntity(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);

    MinisterOfTheWordResponseDTO toDto(MinisterOfTheWord ministerOfTheWord);

    List<MinisterOfTheWordResponseDTO> toDtoList(List<MinisterOfTheWord> ministerOfTheWord);

    @Mapping(target = "id", ignore = true)
    void updateMinisterOfTheWordFromDto(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO, @MappingTarget MinisterOfTheWord ministerOfTheWord);
}
