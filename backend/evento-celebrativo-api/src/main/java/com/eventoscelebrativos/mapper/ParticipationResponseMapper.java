package com.eventoscelebrativos.mapper;

import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.model.ParticipationStatus;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface ParticipationResponseMapper {

    default ParticipationResponseResponseDTO toDto(
            Long eventId,
            ParticipationStatus status,
            String declineReason,
            LocalDateTime respondedAt
    ) {
        return new ParticipationResponseResponseDTO(eventId, status, declineReason, respondedAt);
    }
}
