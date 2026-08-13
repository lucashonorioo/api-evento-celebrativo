package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;

public interface ParishProfileService {

    ParishProfileResponseDTO findPublicProfile();

    ParishProfileResponseDTO update(ParishProfileUpdateRequestDTO requestDTO);
}
