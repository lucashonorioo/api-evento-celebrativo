package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinistroDaPalavraRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDaPalavraResponseDTO;
import com.eventoscelebrativos.model.MinistroDaPalavra;

import java.util.List;
import java.util.Optional;

public interface MinistroDaPalavraService {

    MinistroDaPalavraResponseDTO criarMinistroDaPalavra(MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO);
    List<MinistroDaPalavraResponseDTO> listarTodosMinistroDaPalavra();
    MinistroDaPalavraResponseDTO buscarMinistroDaPalavraPorId(Long id);
    MinistroDaPalavraResponseDTO atualizarMinistroDaPalavra(Long id, MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO);
    void deletarMinistroDaPalavra(Long id);

}
