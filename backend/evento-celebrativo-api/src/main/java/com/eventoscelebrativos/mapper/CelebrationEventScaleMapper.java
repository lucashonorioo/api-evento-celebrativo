package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Commentator;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.model.Reader;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CelebrationEventScaleMapper {

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "location", expression = "java(mapLocation(firstLocation(celebrationEvent)))")
    @Mapping(target = "priest", expression = "java(mapPriest(celebrationEvent))")
    @Mapping(target = "readers", expression = "java(mapReaders(celebrationEvent))")
    @Mapping(target = "commentators", expression = "java(mapCommentators(celebrationEvent))")
    @Mapping(target = "ministersOfTheWord", expression = "java(mapMinistersOfTheWord(celebrationEvent))")
    @Mapping(target = "eucharisticMinisters", expression = "java(mapEucharisticMinisters(celebrationEvent))")
    CelebrationEventScaleResponseDTO toDto(CelebrationEvent celebrationEvent);

    default Location firstLocation(CelebrationEvent celebrationEvent) {
        if (celebrationEvent.getLocations().isEmpty()) {
            return null;
        }
        return celebrationEvent.getLocations().get(0);
    }

    default CelebrationEventScaleLocationResponseDTO mapLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new CelebrationEventScaleLocationResponseDTO(location.getId(), location.getChurchName());
    }

    default CelebrationEventScalePersonResponseDTO mapPriest(CelebrationEvent celebrationEvent) {
        return mapFirstPersonByType(celebrationEvent, Priest.class);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapReaders(CelebrationEvent celebrationEvent) {
        return mapPeopleByType(celebrationEvent, Reader.class);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapCommentators(CelebrationEvent celebrationEvent) {
        return mapPeopleByType(celebrationEvent, Commentator.class);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapMinistersOfTheWord(CelebrationEvent celebrationEvent) {
        return mapPeopleByType(celebrationEvent, MinisterOfTheWord.class);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapEucharisticMinisters(CelebrationEvent celebrationEvent) {
        return mapPeopleByType(celebrationEvent, EucharisticMinister.class);
    }

    default CelebrationEventScalePersonResponseDTO mapFirstPersonByType(
            CelebrationEvent celebrationEvent,
            Class<? extends Person> type
    ) {
        return celebrationEvent.getPeople().stream()
                .filter(type::isInstance)
                .findFirst()
                .map(this::mapPerson)
                .orElse(null);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapPeopleByType(
            CelebrationEvent celebrationEvent,
            Class<? extends Person> type
    ) {
        return celebrationEvent.getPeople().stream()
                .filter(type::isInstance)
                .map(this::mapPerson)
                .toList();
    }

    default CelebrationEventScalePersonResponseDTO mapPerson(Person person) {
        return new CelebrationEventScalePersonResponseDTO(person.getId(), person.getName());
    }
}
