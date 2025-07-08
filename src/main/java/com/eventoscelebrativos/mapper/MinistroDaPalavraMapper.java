package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.MinistroDaPalavraRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDaPalavraResponseDTO;
import com.eventoscelebrativos.model.MinistroDaPalavra;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MinistroDaPalavraMapper {

    @Mapping(target = "id", ignore = true)
    MinistroDaPalavra toEntity(MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO);

    MinistroDaPalavraResponseDTO toDto(MinistroDaPalavra ministroDaPalavra);

    List<MinistroDaPalavraResponseDTO> toDtoList(List<MinistroDaPalavra> ministrosDaPalavra);

    @Mapping(target = "id", ignore = true)
    void atualizarMinistroDaPalavraFromDto(MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO, @MappingTarget MinistroDaPalavra ministroDaPalavra);
}
