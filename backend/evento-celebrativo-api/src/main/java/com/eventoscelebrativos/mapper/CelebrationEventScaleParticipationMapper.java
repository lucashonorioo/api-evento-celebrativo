package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.CelebrationEventScaleLocationResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationPersonResponseDTO;
import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.model.ParticipationStatus;
import com.eventoscelebrativos.service.EventAssignmentGroup;
import com.eventoscelebrativos.service.EventAssignmentSnapshot;
import com.eventoscelebrativos.service.ParticipationResponseSnapshot;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface CelebrationEventScaleParticipationMapper {

    default CelebrationEventScaleParticipationDetailResponseDTO toDto(
            CelebrationEvent event,
            Location location,
            EventAssignmentGroup assignments,
            Map<Long, ParticipationResponseSnapshot> participationByPersonId
    ) {
        CelebrationEventScaleParticipationDetailResponseDTO dto = new CelebrationEventScaleParticipationDetailResponseDTO();
        dto.setEventId(event.getId());
        dto.setEventName(event.getNameMassOrEvent());
        dto.setEventDate(event.getEventDate());
        dto.setEventTime(event.getEventTime());
        dto.setMassOrCelebration(event.getMassOrCelebration());
        dto.setLocation(toLocationDto(location));
        dto.setPriest(toPersonDto(assignments.priest(), participationByPersonId));
        dto.setReaders(toAssignmentPersonDtos(assignments.readers(), participationByPersonId));
        dto.setCommentators(toAssignmentPersonDtos(assignments.commentators(), participationByPersonId));
        dto.setMinistersOfTheWord(toAssignmentPersonDtos(assignments.ministersOfTheWord(), participationByPersonId));
        dto.setEucharisticMinisters(toAssignmentPersonDtos(assignments.eucharisticMinisters(), participationByPersonId));
        return dto;
    }

    default CelebrationEventScaleLocationResponseDTO toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new CelebrationEventScaleLocationResponseDTO(location.getId(), location.getChurchName());
    }

    default CelebrationEventScaleParticipationPersonResponseDTO toPersonDto(
            EventAssignmentSnapshot snapshot,
            Map<Long, ParticipationResponseSnapshot> participationByPersonId
    ) {
        if (snapshot == null) {
            return null;
        }
        ParticipationResponseSnapshot participation = participationByPersonId.get(snapshot.personId());
        ParticipationStatus status = participation != null ? participation.status() : ParticipationStatus.PENDING;
        String declineReason = participation != null ? participation.declineReason() : null;
        return new CelebrationEventScaleParticipationPersonResponseDTO(
                snapshot.personId(),
                snapshot.personName(),
                status,
                declineReason,
                participation != null ? participation.respondedAt() : null
        );
    }

    default List<CelebrationEventScaleParticipationPersonResponseDTO> toAssignmentPersonDtos(
            List<EventAssignmentSnapshot> assignments,
            Map<Long, ParticipationResponseSnapshot> participationByPersonId
    ) {
        return assignments.stream()
                .map(snapshot -> toPersonDto(snapshot, participationByPersonId))
                .toList();
    }
}
