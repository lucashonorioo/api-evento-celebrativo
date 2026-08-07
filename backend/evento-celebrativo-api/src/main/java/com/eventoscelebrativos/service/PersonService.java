package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonAdminUpdateRequestDTO;
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
            Boolean personActive,
            Boolean accountExists,
            Boolean accountEnabled,
            int page,
            int size
    );

    PersonAdminResponseDTO findPersonById(Long id);

    PersonAdminResponseDTO updatePersonAdmin(Long id, PersonAdminUpdateRequestDTO requestDTO);

    PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO);

    PersonMinistriesResponseDTO findPersonMinistries(Long id);

    PersonMinistriesResponseDTO updatePersonMinistries(Long id, PersonMinistriesUpdateRequestDTO requestDTO);

    CurrentUserProfileResponseDTO getCurrentUserProfile(Long personId);

    CurrentUserProfileResponseDTO updateCurrentUserProfile(Long personId, CurrentUserProfileUpdateRequestDTO requestDTO);

    Page<CurrentUserScheduleResponseDTO> findCurrentUserSchedules(
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );
}
