package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;

import java.util.List;

public interface EucharisticMinisterService {

    EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);
    List<EucharisticMinisterResponseDTO> findAllEucharisticMinisters();
    EucharisticMinisterResponseDTO findEucharisticMinistersById(Long id);
    EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO);
    void deleteEucharisticMinisterById(Long id);

}
