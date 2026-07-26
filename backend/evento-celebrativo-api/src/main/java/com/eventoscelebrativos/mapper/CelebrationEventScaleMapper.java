package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScalePersonResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.service.EventScaleAssignmentPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = EventAssignmentType.class)
public interface CelebrationEventScaleMapper {

    @Mapping(target = "eventId", source = "celebrationEvent.id")
    @Mapping(target = "location", expression = "java(mapLocation(firstLocation(celebrationEvent)))")
    @Mapping(target = "priest", expression = "java(mapFirstPersonByType(plan, EventAssignmentType.PRIEST))")
    @Mapping(target = "readers", expression = "java(mapPeopleByType(plan, EventAssignmentType.READER))")
    @Mapping(target = "commentators", expression = "java(mapPeopleByType(plan, EventAssignmentType.COMMENTATOR))")
    @Mapping(target = "ministersOfTheWord", expression = "java(mapPeopleByType(plan, EventAssignmentType.MINISTER_OF_THE_WORD))")
    @Mapping(target = "eucharisticMinisters", expression = "java(mapPeopleByType(plan, EventAssignmentType.EUCHARISTIC_MINISTER))")
    CelebrationEventScaleResponseDTO toDto(CelebrationEvent celebrationEvent, EventScaleAssignmentPlan plan);

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

    default CelebrationEventScalePersonResponseDTO mapFirstPersonByType(
            EventScaleAssignmentPlan plan,
            EventAssignmentType assignmentType
    ) {
        return plan.entries().stream()
                .filter(entry -> entry.assignmentType() == assignmentType)
                .findFirst()
                .map(entry -> mapPerson(entry.person()))
                .orElse(null);
    }

    default List<CelebrationEventScalePersonResponseDTO> mapPeopleByType(
            EventScaleAssignmentPlan plan,
            EventAssignmentType assignmentType
    ) {
        return plan.entries().stream()
                .filter(entry -> entry.assignmentType() == assignmentType)
                .map(entry -> mapPerson(entry.person()))
                .toList();
    }

    default CelebrationEventScalePersonResponseDTO mapPerson(Person person) {
        return new CelebrationEventScalePersonResponseDTO(person.getId(), person.getName());
    }
}
