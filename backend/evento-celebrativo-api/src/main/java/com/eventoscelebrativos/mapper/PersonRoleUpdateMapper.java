package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface PersonRoleUpdateMapper {

    @Mapping(target = "ministries", ignore = true)
    PersonRoleUpdateResponseDTO toDto(Person person);

    default List<String> map(Set<Role> roles) {
        return roles.stream()
                .map(Role::getAuthority)
                .sorted()
                .toList();
    }
}
