package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;

import java.util.List;

public interface PadreService {

    PriestResponseDTO criarPadre(PriestRequestDTO Padre);
    List<PriestResponseDTO> listarTodosPadre();
    PriestResponseDTO buscarPadrePorId(Long id);
    PriestResponseDTO atualizarPadre(Long id, PriestRequestDTO priestRequestDTO);
    void deletarPadre(Long id);

}
