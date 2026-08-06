package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.request.PriestUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriestMapper {

    @Mapping(target = "id", ignore = true)
    Person toEntity(PriestRequestDTO priestRequestDTO);

    default PriestResponseDTO toDtoFromPerson(Person person) {
        return new PriestResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<PriestResponseDTO> toDtoPersonList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDtoFromPerson)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    void updatePriestFromDto(PriestUpdateRequestDTO priestUpdateRequestDTO, @MappingTarget Person person);
}
