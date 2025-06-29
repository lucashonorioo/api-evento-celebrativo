package com.eventoscelebrativos.service.impl;





import com.eventoscelebrativos.model.Padre;
import com.eventoscelebrativos.repository.PadreRepository;
import com.eventoscelebrativos.service.PadreService;
import com.eventoscelebrativos.exception.exception.BusinessRuleViolationException;
import com.eventoscelebrativos.exception.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PadreServiceImpl implements PadreService {

    private final PadreRepository padreRepository;

    public PadreServiceImpl(PadreRepository padreRepository) {
        this.padreRepository = padreRepository;
    }


    @Override
    @Transactional
    public Padre criarPadre(Padre padre) {
        if(padre.getNome() == null){
            throw new BusinessRuleViolationException("O nome não pode ser vazio");
        }
        if(padre.getDataAniversario() == null){
            throw new BusinessRuleViolationException("A data de aniversario não pode ser vazia");
        }
        return padreRepository.save(padre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Padre> listarTodosPadre() {
        return padreRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Padre> buscarPadrePorId(Long id) {
        return padreRepository.findById(id);
    }

    @Override
    @Transactional
    public Padre atualizarPadre(Long id, Padre padreAtualizado) {
        Optional<Padre> padreOptional = padreRepository.findById(id);
        if(padreOptional.isEmpty()){
            throw new ResourceNotFoundException("O padre não foi encontrado com id: " + id);
        }
        if(padreAtualizado.getNome() == null){
            throw new BusinessRuleViolationException("O nome não pode ser vazio");
        }
        if(padreAtualizado.getDataAniversario() == null){
            throw new BusinessRuleViolationException("A data de aniversario não pode ser vazia");
        }
        if(padreAtualizado.getDataAtuacao() == null){
            throw new BusinessRuleViolationException("A data de atuação não pode ser vazia");
        }
        Padre padreExistente = padreOptional.get();
        padreExistente.setNome(padreAtualizado.getNome());
        padreExistente.setDataAniversario(padreAtualizado.getDataAniversario());
        padreExistente.setDataAtuacao(padreAtualizado.getDataAtuacao());
        return padreExistente;
    }

    @Override
    @Transactional
    public void deletarPadre(Long id) {
        Optional<Padre> padreOptional = padreRepository.findById(id);
        if (padreOptional.isEmpty()){
            throw new ResourceNotFoundException("O padre não foi encontrado com id: " + id);
        }
        padreRepository.deleteById(id);
    }
}
