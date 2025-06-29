package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Local;

import java.util.List;
import java.util.Optional;

public interface LocalService {

    Local criarLocal(Local local);
    List<Local> listarTodosLocais();
    Optional<Local> buscarLocalPorId(Long id);
    Local atualizarLocal(Long id, Local localAtualizado);
    void deletarLocal(Long id);


}
