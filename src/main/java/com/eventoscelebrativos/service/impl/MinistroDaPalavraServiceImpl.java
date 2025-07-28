package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.service.MinistroDaPalavraService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinistroDaPalavraServiceImpl implements MinistroDaPalavraService {

    private final MinisterOfTheWordRepository ministerOfTheWordRepository;
    private final MinisterOfTheWordMapper ministerOfTheWordMapper;

    public MinistroDaPalavraServiceImpl(MinisterOfTheWordRepository ministerOfTheWordRepository, MinisterOfTheWordMapper ministerOfTheWordMapper) {
        this.ministerOfTheWordRepository = ministerOfTheWordRepository;
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO criarMinistroDaPalavra(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);
        ministerOfTheWord = ministerOfTheWordRepository.save(ministerOfTheWord);

        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinisterOfTheWordResponseDTO> listarTodosMinistroDaPalavra() {
        List<MinisterOfTheWord> ministrosDaPalavra = ministerOfTheWordRepository.findAll();
        return ministerOfTheWordMapper.toDtoList(ministrosDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public MinisterOfTheWordResponseDTO buscarMinistroDaPalavraPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO atualizarMinistroDaPalavra(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        ministerOfTheWordMapper.atualizarMinistroDaPalavraFromDto(ministerOfTheWordRequestDTO, ministerOfTheWord);
        MinisterOfTheWord ministerOfTheWordSalvo = ministerOfTheWordRepository.save(ministerOfTheWord);
        return ministerOfTheWordMapper.toDto(ministerOfTheWordSalvo);
    }

    @Override
    @Transactional
    public void deletarMinistroDaPalavra(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinisterOfTheWord ministerOfTheWordOptional = ministerOfTheWordRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        ministerOfTheWordRepository.deleteById(id);
    }
}
