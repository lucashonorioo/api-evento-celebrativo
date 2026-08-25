package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.MinistryPersonCreateRequestDTO;
import com.eventoscelebrativos.dto.response.MinistryPersonResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MinistryPersonMapper {

    default Person toEntity(MinistryPersonCreateRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        return new Person(
                requestDTO.getName(),
                requestDTO.getPhoneNumber(),
                requestDTO.getBirthdayDate()
        );
    }

    default MinistryPersonResponseDTO toDto(Person person) {
        return new MinistryPersonResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<MinistryPersonResponseDTO> toDtoList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDto)
                .toList();
    }
}
