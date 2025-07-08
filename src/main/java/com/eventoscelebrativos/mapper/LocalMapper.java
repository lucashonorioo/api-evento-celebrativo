package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.LocalRequestDTO;
import com.eventoscelebrativos.dto.response.LocalResponseDTO;
import com.eventoscelebrativos.model.Local;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LocalMapper {

    @Mapping(target = "id", ignore = true)
    Local toEntity(LocalRequestDTO localRequestDTO);

    LocalResponseDTO toDto(Local local);

    List<LocalResponseDTO> toDtoList(List<Local> locais);

    @Mapping(target = "id", ignore = true)
    void atualizarLocalFromDto(LocalRequestDTO localRequestDTO, @MappingTarget Local local);
}
