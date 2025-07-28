package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;

import java.util.List;

public interface MinistroDaPalavraService {

    MinisterOfTheWordResponseDTO criarMinistroDaPalavra(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);
    List<MinisterOfTheWordResponseDTO> listarTodosMinistroDaPalavra();
    MinisterOfTheWordResponseDTO buscarMinistroDaPalavraPorId(Long id);
    MinisterOfTheWordResponseDTO atualizarMinistroDaPalavra(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);
    void deletarMinistroDaPalavra(Long id);

}
