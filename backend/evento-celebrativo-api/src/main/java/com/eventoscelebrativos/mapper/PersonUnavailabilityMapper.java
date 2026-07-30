package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import com.eventoscelebrativos.model.PersonUnavailability;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PersonUnavailabilityMapper {

    PersonUnavailabilityResponseDTO toDto(PersonUnavailability personUnavailability);
}
