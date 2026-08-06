package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CurrentUserProfileMapper {

    @Mapping(target = "ministries", ignore = true)
    @Mapping(target = "roles", ignore = true)
    CurrentUserProfileResponseDTO toDto(Person person);
}
