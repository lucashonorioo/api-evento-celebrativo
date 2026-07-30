package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.service.EventAssignmentGroup;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CelebrationEventScaleDetailMapper {

    default CelebrationEventScaleDetailResponseDTO toDto(
            CelebrationEvent event,
            Location location,
            EventAssignmentGroup assignments
    ) {
        CelebrationEventScaleDetailResponseDTO dto = new CelebrationEventScaleDetailResponseDTO();
        dto.setEventId(event.getId());
        dto.setEventName(event.getNameMassOrEvent());
        dto.setStartAt(event.getStartAt());
        dto.setEndAt(event.getEndAt());
        dto.setMassOrCelebration(event.getMassOrCelebration());
        dto.setLocation(toLocationDto(location));
        dto.setPriest(toPersonDto(assignments.priest()));
        dto.setReaders(toAssignmentPersonDtos(assignments.readers()));
        dto.setCommentators(toAssignmentPersonDtos(assignments.commentators()));
        dto.setMinistersOfTheWord(toAssignmentPersonDtos(assignments.ministersOfTheWord()));
        dto.setEucharisticMinisters(toAssignmentPersonDtos(assignments.eucharisticMinisters()));
        return dto;
    }

    default CelebrationEventScaleLocationResponseDTO toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new CelebrationEventScaleLocationResponseDTO(location.getId(), location.getChurchName());
    }

    default CelebrationEventScalePersonResponseDTO toPersonDto(EventAssignmentSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new CelebrationEventScalePersonResponseDTO(snapshot.personId(), snapshot.personName());
    }

    default List<CelebrationEventScalePersonResponseDTO> toAssignmentPersonDtos(
            List<EventAssignmentSnapshot> assignments
    ) {
        return assignments.stream()
                .map(this::toPersonDto)
                .toList();
    }
}
