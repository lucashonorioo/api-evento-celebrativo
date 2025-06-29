package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistroDaPalavra;

import java.util.List;
import java.util.Optional;

public interface MinistroDaPalavraService {

    MinistroDaPalavra criarMinistroDaPalavra(MinistroDaPalavra ministroDaPalavra);
    List<MinistroDaPalavra> listarTodosMinistroDaPalavra();
    Optional<MinistroDaPalavra> buscarMinistroDaPalavraPorId(Long id);
    MinistroDaPalavra atualizarMinistroDaPalavra(Long id, MinistroDaPalavra ministroDaPalavraAtualizado);
    void deletarMinistroDaPalavra(Long id);

}
