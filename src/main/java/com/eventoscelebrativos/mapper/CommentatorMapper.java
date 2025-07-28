package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.model.Commentator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentatorMapper {

    @Mapping(target = "id", ignore = true)
    Commentator toEntity(CommentatorRequestDTO commentatorRequestDTO);

    CommentatorResponseDTO toDto(Commentator commentator);

    List<CommentatorResponseDTO> toDtoList(List<Commentator> commentators);

    @Mapping(target = "id", ignore = true)
    void updateCommentatorFromDto(CommentatorRequestDTO commentatorRequestDTO, @MappingTarget Commentator commentator);
}
