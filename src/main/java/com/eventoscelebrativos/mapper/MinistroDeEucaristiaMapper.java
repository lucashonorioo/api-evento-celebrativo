package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.MinistroDeEucaristiaRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDeEucaristiaResponseDTO;
import com.eventoscelebrativos.model.MinistroDeEucaristia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MinistroDeEucaristiaMapper {

    @Mapping(target = "id", ignore = true)
    MinistroDeEucaristia toEntity(MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO);

    MinistroDeEucaristiaResponseDTO toDto(MinistroDeEucaristia ministroDeEucaristia);

    List<MinistroDeEucaristiaResponseDTO> toDtoList(List<MinistroDeEucaristia> ministrosDeEucaristia);

    @Mapping(target = "id", ignore = true)
    void atualizarMinistroDeEucaristiaFromDto(MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO, @MappingTarget MinistroDeEucaristia ministroDeEucaristia);
}
