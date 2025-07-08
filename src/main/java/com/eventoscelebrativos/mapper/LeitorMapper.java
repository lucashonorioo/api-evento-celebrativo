package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.LeitorRequestDTO;
import com.eventoscelebrativos.dto.response.LeitorResponseDTO;
import com.eventoscelebrativos.model.Leitor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeitorMapper {

    @Mapping(target = "id", ignore = true)
    Leitor toEntity(LeitorRequestDTO leitorRequestDTO);

    LeitorResponseDTO toDto(Leitor leitor);

    List<LeitorResponseDTO> toDtoList(List<Leitor> leitors);

    @Mapping(target = "id", ignore = true)
    void atualizarLeitorFromDto(LeitorRequestDTO leitorRequestDTO, @MappingTarget Leitor leitor);
}
