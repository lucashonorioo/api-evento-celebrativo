package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.model.EventScheduleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface CelebrationEventService {

    CelebrationEventResponseDTO createEvent(CelebrationEventRequestDTO celebrationEventRequestDTO);
    List<CelebrationEventResponseDTO> findAllEvents();
    Page<EucharistScaleEventResponseDTO> findEucharistScale(Pageable pageable, LocalDate startDate, LocalDate endDate);
    Page<EventScheduleQueryResponseDTO> findEventSchedules(
            LocalDate startDate,
            LocalDate endDate,
            EventScheduleType type,
            int page,
            int size,
            boolean includeUnassigned
    );
    CelebrationEventResponseDTO findEventById(Long id);
    CelebrationEventResponseDTO updateEvent(Long id, CelebrationEventRequestDTO celebrationEventRequestDTO);
    CelebrationEventScaleResponseDTO updateEventScale(Long id, CelebrationEventScaleRequestDTO celebrationEventScaleRequestDTO);
    CelebrationEventScaleResponseDTO createEventWithScale(CelebrationEventWithScaleRequestDTO celebrationEventWithScaleRequestDTO);
    void deleteEventById(Long id);

}
