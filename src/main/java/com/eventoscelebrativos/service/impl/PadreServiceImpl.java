package com.eventoscelebrativos.service.impl;





import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.mapper.PriestMapper;
import com.eventoscelebrativos.model.Priest;
import com.eventoscelebrativos.repository.PriestRepository;
import com.eventoscelebrativos.service.PadreService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PadreServiceImpl implements PadreService {

    private final PriestRepository priestRepository;
    private final PriestMapper priestMapper;

    public PadreServiceImpl(PriestRepository priestRepository, PriestMapper priestMapper) {
        this.priestRepository = priestRepository;
        this.priestMapper = priestMapper;
    }


    @Override
    @Transactional
    public PriestResponseDTO criarPadre(PriestRequestDTO priestRequestDTO) {
        Priest priest = priestMapper.toEntity(priestRequestDTO);
        priest = priestRepository.save(priest);
        return priestMapper.toDto(priest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriestResponseDTO> listarTodosPadre() {
        List<Priest> priests = priestRepository.findAll();
        return priestMapper.toDtoList(priests);
    }

    @Override
    @Transactional(readOnly = true)
    public PriestResponseDTO buscarPadrePorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Priest priest = priestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        return priestMapper.toDto(priest);
    }

    @Override
    @Transactional
    public PriestResponseDTO atualizarPadre(Long id, PriestRequestDTO priestRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Priest priest = priestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        priestMapper.atualizarPadreFromDto(priestRequestDTO, priest);
        Priest priestSalvo = priestRepository.save(priest);
        return priestMapper.toDto(priestSalvo);
    }

    @Override
    @Transactional
    public void deletarPadre(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Priest priest = priestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        priestRepository.deleteById(id);
    }
}
