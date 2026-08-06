package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersonAdminMapper {

    @Mapping(target = "ministries", ignore = true)
    @Mapping(target = "roles", ignore = true)
    PersonAdminResponseDTO toDto(Person person);
}
