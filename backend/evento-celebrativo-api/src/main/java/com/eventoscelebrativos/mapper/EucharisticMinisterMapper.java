package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EucharisticMinisterMapper {

    @Mapping(target = "id", ignore = true)
    EucharisticMinister toEntity(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);

    EucharisticMinisterResponseDTO toDto(EucharisticMinister eucharisticMinister);

    List<EucharisticMinisterResponseDTO> toDtoList(List<EucharisticMinister> eucharisticMinisters);

    default EucharisticMinisterResponseDTO toDtoFromPerson(Person person) {
        return new EucharisticMinisterResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<EucharisticMinisterResponseDTO> toDtoPersonList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDtoFromPerson)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    void updateEucharisticMinisterFromDto(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO, @MappingTarget EucharisticMinister eucharisticMinister);
}
