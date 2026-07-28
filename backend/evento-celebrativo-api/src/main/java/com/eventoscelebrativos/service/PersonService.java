package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface PersonService {

    Page<PersonAdminResponseDTO> findPeople(
            String name,
            String phoneNumber,
            String ministry,
            String role,
            int page,
            int size
    );

    PersonAdminResponseDTO findPersonById(Long id);

    PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO);

    PersonMinistriesResponseDTO findPersonMinistries(Long id);

    PersonMinistriesResponseDTO updatePersonMinistries(Long id, PersonMinistriesUpdateRequestDTO requestDTO);

    CurrentUserProfileResponseDTO getCurrentUserProfile(String phoneNumber);

    CurrentUserProfileResponseDTO updateCurrentUserProfile(String phoneNumber, CurrentUserProfileUpdateRequestDTO requestDTO);

    Page<CurrentUserScheduleResponseDTO> findCurrentUserSchedules(
            String phoneNumber,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );
}
