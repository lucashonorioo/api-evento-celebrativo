package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PadreRequestDTO;
import com.eventoscelebrativos.dto.response.PadreResponseDTO;
import com.eventoscelebrativos.model.Padre;

import java.util.List;
import java.util.Optional;

public interface PadreService {

    PadreResponseDTO criarPadre(PadreRequestDTO Padre);
    List<PadreResponseDTO> listarTodosPadre();
    PadreResponseDTO buscarPadrePorId(Long id);
    PadreResponseDTO atualizarPadre(Long id, PadreRequestDTO padreRequestDTO);
    void deletarPadre(Long id);

}
