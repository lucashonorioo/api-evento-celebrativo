package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;
import com.eventoscelebrativos.model.ParishProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ParishProfileMapper {

    ParishProfileResponseDTO toDto(ParishProfile parishProfile);
}
