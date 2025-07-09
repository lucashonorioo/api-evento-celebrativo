package com.eventoscelebrativos.service.impl;





import com.eventoscelebrativos.dto.request.PadreRequestDTO;
import com.eventoscelebrativos.dto.response.PadreResponseDTO;
import com.eventoscelebrativos.mapper.PadreMapper;
import com.eventoscelebrativos.model.Padre;
import com.eventoscelebrativos.repository.PadreRepository;
import com.eventoscelebrativos.service.PadreService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PadreServiceImpl implements PadreService {

    private final PadreRepository padreRepository;
    private final PadreMapper padreMapper;

    public PadreServiceImpl(PadreRepository padreRepository, PadreMapper padreMapper) {
        this.padreRepository = padreRepository;
        this.padreMapper = padreMapper;
    }


    @Override
    @Transactional
    public PadreResponseDTO criarPadre(PadreRequestDTO padreRequestDTO) {
        Padre padre = padreMapper.toEntity(padreRequestDTO);
        padre = padreRepository.save(padre);
        return padreMapper.toDto(padre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PadreResponseDTO> listarTodosPadre() {
        List<Padre> padres = padreRepository.findAll();
        return padreMapper.toDtoList(padres);
    }

    @Override
    @Transactional(readOnly = true)
    public PadreResponseDTO buscarPadrePorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Padre padre = padreRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        return padreMapper.toDto(padre);
    }

    @Override
    @Transactional
    public PadreResponseDTO atualizarPadre(Long id, PadreRequestDTO padreRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Padre padre = padreRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        padreMapper.atualizarPadreFromDto(padreRequestDTO, padre);
        Padre padreSalvo = padreRepository.save(padre);
        return padreMapper.toDto(padreSalvo);
    }

    @Override
    @Transactional
    public void deletarPadre(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positio e não nulo");
        }
        Padre padre = padreRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Padre", id));
        padreRepository.deleteById(id);
    }
}
