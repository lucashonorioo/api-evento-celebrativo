package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.model.Reader;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReaderMapper {

    @Mapping(target = "id", ignore = true)
    Reader toEntity(ReaderRequestDTO readerRequestDTO);

    ReaderResponseDTO toDto(Reader reader);

    List<ReaderResponseDTO> toDtoList(List<Reader> readers);

    @Mapping(target = "id", ignore = true)
    void updateReaderFromDto(ReaderRequestDTO readerRequestDTO, @MappingTarget Reader reader);
}
