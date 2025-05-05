package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Padre;

import java.util.List;
import java.util.Optional;

public interface PadreService {

    Padre criarPadre(Padre Padre);
    List<Padre> listarTodosPadre();
    Optional<Padre> buscarPadrePorId(Long id);
    Padre atualizarPadre(Long id, Padre padreAtualizado);
    void deletarPadre(Long id);

}
