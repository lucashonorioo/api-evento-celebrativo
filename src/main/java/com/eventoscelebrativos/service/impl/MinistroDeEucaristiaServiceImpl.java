package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.model.MinistroDeEucaristia;
import com.eventoscelebrativos.repository.MinistroDeEucaristiaRepository;
import com.eventoscelebrativos.service.MinistroDeEucaristiaService;
import com.eventoscelebrativos.exception.exception.BusinessRuleViolationException;
import com.eventoscelebrativos.exception.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MinistroDeEucaristiaServiceImpl implements MinistroDeEucaristiaService {

    private final MinistroDeEucaristiaRepository ministroDeEucaristiaRepository;

    public MinistroDeEucaristiaServiceImpl(MinistroDeEucaristiaRepository ministroDeEucaristiaRepository) {
        this.ministroDeEucaristiaRepository = ministroDeEucaristiaRepository;
    }


    @Override
    @Transactional
    public MinistroDeEucaristia criarMinistroDeEucaristia(MinistroDeEucaristia ministroDeEucaristia) {
        if(ministroDeEucaristia.getNome() == null){
            throw new BusinessRuleViolationException("O nome não pode ser vazio");
        }
        if(ministroDeEucaristia.getDataAniversario() == null){
            throw new BusinessRuleViolationException("A data de aniversario não pode ser vazia");
        }
        return ministroDeEucaristiaRepository.save(ministroDeEucaristia);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistroDeEucaristia> listarTodosMinistroDeEucaristia() {
        return ministroDeEucaristiaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MinistroDeEucaristia> buscarMinistroDeEucaristiaPorId(Long id) {
        return ministroDeEucaristiaRepository.findById(id);
    }

    @Override
    @Transactional
    public MinistroDeEucaristia atualizarMinistroDeEucaristia(Long id, MinistroDeEucaristia ministroDeEucaristiaAtualizado) {
        Optional<MinistroDeEucaristia> ministroDeEucaristiaOptional = ministroDeEucaristiaRepository.findById(id);
        if(ministroDeEucaristiaOptional.isEmpty()){
            throw new ResourceNotFoundException("O ministro de eucaristia não foi encontrado com id: " + id);
        }
        if(ministroDeEucaristiaAtualizado.getNome() == null){
            throw new BusinessRuleViolationException("O nome não pode ser vazio");
        }
        if(ministroDeEucaristiaAtualizado.getDataAniversario() == null){
            throw new BusinessRuleViolationException("A data de aniversario não pode ser vazia");
        }
        if(ministroDeEucaristiaAtualizado.getDataAtuacao() == null){
            throw new BusinessRuleViolationException("A data de atuação não pode ser vazia");
        }
        MinistroDeEucaristia ministroDeEucaristiaExistente = ministroDeEucaristiaOptional.get();
        ministroDeEucaristiaExistente.setNome(ministroDeEucaristiaAtualizado.getNome());
        ministroDeEucaristiaExistente.setDataAniversario(ministroDeEucaristiaAtualizado.getDataAniversario());
        ministroDeEucaristiaExistente.setDataAtuacao(ministroDeEucaristiaAtualizado.getDataAtuacao());
        return ministroDeEucaristiaExistente;
    }

    @Override
    @Transactional
    public void deletarMinistroDeEucaristia(Long id) {
        Optional<MinistroDeEucaristia> MinistroDeEucaristiaOptional = ministroDeEucaristiaRepository.findById(id);
        if (MinistroDeEucaristiaOptional.isEmpty()){
            throw new ResourceNotFoundException("O ministro de eucaristia não foi encontrado com id: " + id);
        }
        ministroDeEucaristiaRepository.deleteById(id);
    }
}
