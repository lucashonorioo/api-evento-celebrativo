package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistroDeEucaristia;

import java.util.List;
import java.util.Optional;

public interface MinistroDeEucaristiaService {

    MinistroDeEucaristia criarMinistroDeEucaristia(MinistroDeEucaristia ministroDeEucaristia);
    List<MinistroDeEucaristia> listarTodosMinistroDeEucaristia();
    Optional<MinistroDeEucaristia> buscarMinistroDeEucaristiaPorId(Long id);
    MinistroDeEucaristia atualizarMinistroDeEucaristia(Long id, MinistroDeEucaristia ministroDeEucaristiaAtualizado);
    void deletarMinistroDeEucaristia(Long id);

}
