package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PriestMapper {

    @Mapping(target = "id", ignore = true)
    Priest toEntity(PriestRequestDTO priestRequestDTO);

    PriestResponseDTO toDto(Priest priest);

    List<PriestResponseDTO> toDtoList(List<Priest> priests);

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
    void updatePriestFromDto(PriestRequestDTO priestRequestDTO, @MappingTarget Priest priest);
}
