package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.Person;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CelebrationEventScaleDetailMapper {

    default CelebrationEventScaleDetailResponseDTO toDto(
            CelebrationEvent event,
            Location location,
            Person priest,
            List<? extends Person> readers,
            List<? extends Person> commentators,
            List<? extends Person> ministersOfTheWord,
            List<? extends Person> eucharisticMinisters
    ) {
        CelebrationEventScaleDetailResponseDTO dto = new CelebrationEventScaleDetailResponseDTO();
        dto.setEventId(event.getId());
        dto.setEventName(event.getNameMassOrEvent());
        dto.setEventDate(event.getEventDate());
        dto.setEventTime(event.getEventTime());
        dto.setMassOrCelebration(event.getMassOrCelebration());
        dto.setLocation(toLocationDto(location));
        dto.setPriest(toPersonDto(priest));
        dto.setReaders(toPersonDtos(readers));
        dto.setCommentators(toPersonDtos(commentators));
        dto.setMinistersOfTheWord(toPersonDtos(ministersOfTheWord));
        dto.setEucharisticMinisters(toPersonDtos(eucharisticMinisters));
        return dto;
    }

    default CelebrationEventScaleLocationResponseDTO toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new CelebrationEventScaleLocationResponseDTO(location.getId(), location.getChurchName());
    }

    default CelebrationEventScalePersonResponseDTO toPersonDto(Person person) {
        if (person == null) {
            return null;
        }
        return new CelebrationEventScalePersonResponseDTO(person.getId(), person.getName());
    }

    default List<CelebrationEventScalePersonResponseDTO> toPersonDtos(List<? extends Person> people) {
        return people.stream()
                .map(this::toPersonDto)
                .toList();
    }
}
