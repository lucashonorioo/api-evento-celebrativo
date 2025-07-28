package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;

import java.util.List;

public interface LocalService {

    LocationResponseDTO criarLocal(LocationRequestDTO locationRequestDTO);
    List<LocationResponseDTO> listarTodosLocais();
    LocationResponseDTO buscarLocalPorId(Long id);
    LocationResponseDTO atualizarLocal(Long id, LocationRequestDTO locationRequestDTO);
    void deletarLocal(Long id);


}
