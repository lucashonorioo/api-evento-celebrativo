package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.LocalRequestDTO;
import com.eventoscelebrativos.dto.response.LocalResponseDTO;
import com.eventoscelebrativos.model.Local;

import java.util.List;
import java.util.Optional;

public interface LocalService {

    LocalResponseDTO criarLocal(LocalRequestDTO localRequestDTO);
    List<LocalResponseDTO> listarTodosLocais();
    LocalResponseDTO buscarLocalPorId(Long id);
    LocalResponseDTO atualizarLocal(Long id, LocalRequestDTO localRequestDTO);
    void deletarLocal(Long id);


}
