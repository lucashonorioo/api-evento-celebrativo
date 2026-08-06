package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersonRoleUpdateMapper {

    @Mapping(target = "ministries", ignore = true)
    @Mapping(target = "roles", ignore = true)
    PersonRoleUpdateResponseDTO toDto(Person person);
}
