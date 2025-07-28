package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.model.EucharisticMinister;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EucharisticMinisterMapper {

    @Mapping(target = "id", ignore = true)
    EucharisticMinister toEntity(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);

    EucharisticMinisterResponseDTO toDto(EucharisticMinister eucharisticMinister);

    List<EucharisticMinisterResponseDTO> toDtoList(List<EucharisticMinister> eucharisticMinisters);

    @Mapping(target = "id", ignore = true)
    void updateEucharisticMinisterFromDto(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO, @MappingTarget EucharisticMinister eucharisticMinister);
}
