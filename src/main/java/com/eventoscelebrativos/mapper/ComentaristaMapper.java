package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.ComentaristaRequestDTO;
import com.eventoscelebrativos.dto.response.ComentaristaResponseDTO;
import com.eventoscelebrativos.model.Comentarista;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ComentaristaMapper {

    @Mapping(target = "id", ignore = true)
    Comentarista toEntity(ComentaristaRequestDTO comentaristaRequestDTO);

    ComentaristaResponseDTO toDto(Comentarista comentarista);

    List<ComentaristaResponseDTO> toDtoList(List<Comentarista> comentaristas);

    @Mapping(target = "id", ignore = true)
    void atualizarComentaristaFromDto(ComentaristaRequestDTO comentaristaRequestDTO, @MappingTarget Comentarista comentarista);
}
