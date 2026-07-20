package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MinisterOfTheWordMapper {

    @Mapping(target = "id", ignore = true)
    MinisterOfTheWord toEntity(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);

    MinisterOfTheWordResponseDTO toDto(MinisterOfTheWord ministerOfTheWord);

    List<MinisterOfTheWordResponseDTO> toDtoList(List<MinisterOfTheWord> ministerOfTheWord);

    default MinisterOfTheWordResponseDTO toDtoFromPerson(Person person) {
        return new MinisterOfTheWordResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<MinisterOfTheWordResponseDTO> toDtoPersonList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDtoFromPerson)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    void updateMinisterOfTheWordFromDto(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO, @MappingTarget MinisterOfTheWord ministerOfTheWord);
}
