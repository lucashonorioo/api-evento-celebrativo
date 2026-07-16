package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import org.springframework.data.domain.Page;

public interface PersonService {

    Page<PersonAdminResponseDTO> findPeople(
            String name,
            String phoneNumber,
            String personType,
            String role,
            int page,
            int size
    );

    PersonAdminResponseDTO findPersonById(Long id);

    PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO);
}
