package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

public interface PersonUnavailabilityService {

    Page<PersonUnavailabilityResponseDTO> findMine(
            Long personId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int page,
            int size
    );

    PersonUnavailabilityResponseDTO create(Long personId, PersonUnavailabilityRequestDTO requestDTO);

    PersonUnavailabilityResponseDTO update(Long personId, Long id, PersonUnavailabilityRequestDTO requestDTO);

    void delete(Long personId, Long id);

    AdminUnavailabilityResponseDTO findByDate(LocalDateTime startAt, LocalDateTime endAt);
}
