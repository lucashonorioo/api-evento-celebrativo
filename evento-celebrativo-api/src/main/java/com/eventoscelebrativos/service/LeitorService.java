package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.Leitor;

import java.util.List;
import java.util.Optional;

public interface LeitorService {

    Leitor criarLeitor(Leitor leitor);
    List<Leitor> listarTodosLeitor();
    Optional<Leitor> buscarLeitorPorId(Long id);
    Leitor atualizarLeitor(Long id, Leitor leitorAtualizado);
    void deletarLeitor(Long id);

}
