package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.model.MinistroDaPalavra;
import com.eventoscelebrativos.repository.MinistroDaPalavraRepository;
import com.eventoscelebrativos.service.MinistroDaPalavraService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MinistroDaPalavraServiceImpl implements MinistroDaPalavraService {

    private final MinistroDaPalavraRepository ministroDaPalavraRepository;

    public MinistroDaPalavraServiceImpl(MinistroDaPalavraRepository ministroDaPalavraRepository) {
        this.ministroDaPalavraRepository = ministroDaPalavraRepository;
    }


    @Override
    @Transactional
    public MinistroDaPalavra criarMinistroDaPalavra(MinistroDaPalavra ministroDaPalavra) {
        if(ministroDaPalavra.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(ministroDaPalavra.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }
        return ministroDaPalavraRepository.save(ministroDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistroDaPalavra> listarTodosMinistroDaPalavra() {
        return ministroDaPalavraRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MinistroDaPalavra> buscarMinistroDaPalavraPorId(Long id) {
        return ministroDaPalavraRepository.findById(id);
    }

    @Override
    @Transactional
    public MinistroDaPalavra atualizarMinistroDaPalavra(Long id, MinistroDaPalavra ministroDaPalavraAtualizado) {
        Optional<MinistroDaPalavra> ministroDaPalavraOptional = ministroDaPalavraRepository.findById(id);
        if(ministroDaPalavraOptional.isEmpty()){
            throw new ResourceNotFoundException("O ministro da palavra não foi encontrado com id: " + id);
        }
        if(ministroDaPalavraAtualizado.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(ministroDaPalavraAtualizado.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }
        MinistroDaPalavra ministroDaPalavraExistente = ministroDaPalavraOptional.get();
        ministroDaPalavraExistente.setNome(ministroDaPalavraAtualizado.getNome());
        ministroDaPalavraExistente.setDataAniversario(ministroDaPalavraAtualizado.getDataAniversario());
        return ministroDaPalavraExistente;
    }

    @Override
    @Transactional
    public void deletarMinistroDaPalavra(Long id) {
        Optional<MinistroDaPalavra> ministroDaPalavraOptional = ministroDaPalavraRepository.findById(id);
        if (ministroDaPalavraOptional.isEmpty()){
            throw new ResourceNotFoundException("O ministro da palavra não foi encontrado com id: " + id);
        }
        ministroDaPalavraRepository.deleteById(id);
    }
}
