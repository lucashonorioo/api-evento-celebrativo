package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.model.Comentarista;
import com.eventoscelebrativos.repository.ComentaristaRepository;
import com.eventoscelebrativos.service.ComentaristaService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ComentaristaServiceImpl implements ComentaristaService {

    private final ComentaristaRepository comentaristaRepository;

    public ComentaristaServiceImpl(ComentaristaRepository comentaristaRepository) {
        this.comentaristaRepository = comentaristaRepository;
    }

    @Override
    @Transactional
    public Comentarista criarComentarista(Comentarista comentarista) {
        if(comentarista.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(comentarista.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }
        return comentaristaRepository.save(comentarista);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comentarista> listarTodosComentaristas() {
        return comentaristaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Comentarista> buscarComentaristaPorId(Long id) {
        return comentaristaRepository.findById(id);
    }

    @Override
    @Transactional
    public Comentarista atualizarComentarista(Long id, Comentarista comentaristaAtualizado) {
        Optional<Comentarista> comentaristaOptional = comentaristaRepository.findById(id);
        if(comentaristaOptional.isEmpty()){
            throw new ResourceNotFoundException("O comentarista não foi encontrado com id: " + id);
        }
        if(comentaristaAtualizado.getNome() == null){
            throw new BusinessException("O nome não pode ser vazio");
        }
        if(comentaristaAtualizado.getDataAniversario() == null){
            throw new BusinessException("A data de aniversario não pode ser vazia");
        }

        Comentarista comentaristaExistente = comentaristaOptional.get();
        comentaristaExistente.setNome(comentaristaAtualizado.getNome());
        comentaristaExistente.setDataAniversario(comentaristaAtualizado.getDataAniversario());
        return comentaristaExistente;
    }

    @Override
    @Transactional
    public void deletarComentarista(Long id) {
        Optional<Comentarista> comentaristaOptional = comentaristaRepository.findById(id);
        if (comentaristaOptional.isEmpty()){
            throw new ResourceNotFoundException("O comentarista não foi encontrado com id: " + id);
        }
        comentaristaRepository.deleteById(id);
    }
}
