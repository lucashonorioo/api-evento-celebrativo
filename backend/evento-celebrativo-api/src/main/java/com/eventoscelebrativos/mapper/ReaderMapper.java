package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReaderMapper {

    @Mapping(target = "id", ignore = true)
    Person toEntity(ReaderRequestDTO readerRequestDTO);

    default ReaderResponseDTO toDtoFromPerson(Person person) {
        return new ReaderResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<ReaderResponseDTO> toDtoPersonList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDtoFromPerson)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    void updateReaderFromDto(ReaderUpdateRequestDTO readerUpdateRequestDTO, @MappingTarget Person person);
}
