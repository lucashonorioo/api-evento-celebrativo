package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonUnavailabilityRequestDTO;
import com.eventoscelebrativos.dto.response.AdminUnavailabilityResponseDTO;
import com.eventoscelebrativos.dto.response.PersonUnavailabilityResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface PersonUnavailabilityService {

    Page<PersonUnavailabilityResponseDTO> findMine(
            String phoneNumber,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    PersonUnavailabilityResponseDTO create(String phoneNumber, PersonUnavailabilityRequestDTO requestDTO);

    PersonUnavailabilityResponseDTO update(String phoneNumber, Long id, PersonUnavailabilityRequestDTO requestDTO);

    void delete(String phoneNumber, Long id);

    AdminUnavailabilityResponseDTO findByDate(LocalDate date);
}
