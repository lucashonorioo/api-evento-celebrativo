package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.mapper.MinisterOfTheWordMapper;
import com.eventoscelebrativos.model.MinisterOfTheWord;
import com.eventoscelebrativos.repository.MinisterOfTheWordRepository;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinisterOfTheWordServiceImpl implements MinisterOfTheWordService {

    private final MinisterOfTheWordRepository ministerOfTheWordRepository;
    private final MinisterOfTheWordMapper ministerOfTheWordMapper;

    public MinisterOfTheWordServiceImpl(MinisterOfTheWordRepository ministerOfTheWordRepository, MinisterOfTheWordMapper ministerOfTheWordMapper) {
        this.ministerOfTheWordRepository = ministerOfTheWordRepository;
        this.ministerOfTheWordMapper = ministerOfTheWordMapper;
    }


    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordMapper.toEntity(ministerOfTheWordRequestDTO);
        ministerOfTheWord = ministerOfTheWordRepository.save(ministerOfTheWord);

        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinisterOfTheWordResponseDTO> findAllMinistersOfTheWord() {
        List<MinisterOfTheWord> ministrosDaPalavra = ministerOfTheWordRepository.findAll();
        return ministerOfTheWordMapper.toDtoList(ministrosDaPalavra);
    }

    @Override
    @Transactional(readOnly = true)
    public MinisterOfTheWordResponseDTO findMinisterOfTheWordById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ministro Da Palavra", id));
        return ministerOfTheWordMapper.toDto(ministerOfTheWord);
    }

    @Override
    @Transactional
    public MinisterOfTheWordResponseDTO updateMinisterOfTheWord(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            MinisterOfTheWord ministerOfTheWord = ministerOfTheWordRepository.getReferenceById(id);
            ministerOfTheWordMapper.updateMinisterOfTheWordFromDto(ministerOfTheWordRequestDTO, ministerOfTheWord);
            MinisterOfTheWord ministerOfTheWordSalvo = ministerOfTheWordRepository.save(ministerOfTheWord);
            return ministerOfTheWordMapper.toDto(ministerOfTheWordSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Ministro da Palavra", id);
        }
    }

    @Override
    @Transactional
    public void deleteMinisterOfTheWord(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!ministerOfTheWordRepository.existsById(id)){
            throw new ResourceNotFoundException("Ministro da Palavra", id);
        }
        ministerOfTheWordRepository.deleteById(id);
    }
}
