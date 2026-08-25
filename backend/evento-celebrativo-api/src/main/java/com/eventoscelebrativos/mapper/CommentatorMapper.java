package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentatorMapper {

    @Mapping(target = "id", ignore = true)
    Person toEntity(CommentatorRequestDTO commentatorRequestDTO);

    default CommentatorResponseDTO toDtoFromPerson(Person person) {
        return new CommentatorResponseDTO(
                person.getId(),
                person.getName(),
                person.getPhoneNumber(),
                person.getBirthdayDate()
        );
    }

    default List<CommentatorResponseDTO> toDtoPersonList(List<? extends Person> people) {
        return people.stream()
                .map(this::toDtoFromPerson)
                .toList();
    }
}
