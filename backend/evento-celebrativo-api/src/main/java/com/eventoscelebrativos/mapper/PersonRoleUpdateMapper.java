package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface PersonRoleUpdateMapper {

    PersonRoleUpdateResponseDTO toDto(Person person);

    default List<String> map(Set<Role> roles) {
        return roles.stream()
                .map(Role::getAuthority)
                .toList();
    }
}
