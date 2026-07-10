package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;

public interface PersonService {

    PersonRoleUpdateResponseDTO updatePersonRole(Long id, PersonRoleUpdateRequestDTO requestDTO);
}
