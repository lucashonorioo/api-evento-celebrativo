package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.dto.request.MinistroDeEucaristiaRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDeEucaristiaResponseDTO;
import com.eventoscelebrativos.mapper.MinistroDeEucaristiaMapper;
import com.eventoscelebrativos.model.MinistroDeEucaristia;
import com.eventoscelebrativos.repository.MinistroDeEucaristiaRepository;
import com.eventoscelebrativos.service.MinistroDeEucaristiaService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MinistroDeEucaristiaServiceImpl implements MinistroDeEucaristiaService {

    private final MinistroDeEucaristiaRepository ministroDeEucaristiaRepository;
    private final MinistroDeEucaristiaMapper ministroDeEucaristiaMapper;

    public MinistroDeEucaristiaServiceImpl(MinistroDeEucaristiaRepository ministroDeEucaristiaRepository, MinistroDeEucaristiaMapper ministroDeEucaristiaMapper) {
        this.ministroDeEucaristiaRepository = ministroDeEucaristiaRepository;
        this.ministroDeEucaristiaMapper = ministroDeEucaristiaMapper;
    }


    @Override
    @Transactional
    public MinistroDeEucaristiaResponseDTO criarMinistroDeEucaristia(MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO) {
        MinistroDeEucaristia ministroDeEucaristia = ministroDeEucaristiaMapper.toEntity(ministroDeEucaristiaRequestDTO);
        ministroDeEucaristia = ministroDeEucaristiaRepository.save(ministroDeEucaristia);
        return ministroDeEucaristiaMapper.toDto(ministroDeEucaristia);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinistroDeEucaristiaResponseDTO> listarTodosMinistroDeEucaristia() {
        List<MinistroDeEucaristia> ministrosDeEucaristia = ministroDeEucaristiaRepository.findAll();
        return ministroDeEucaristiaMapper.toDtoList(ministrosDeEucaristia);
    }

    @Override
    @Transactional(readOnly = true)
    public MinistroDeEucaristiaResponseDTO buscarMinistroDeEucaristiaPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDeEucaristia ministroDeEucaristia = ministroDeEucaristiaRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Ministro De Eucaristia", id));

        return ministroDeEucaristiaMapper.toDto(ministroDeEucaristia);
    }

    @Override
    @Transactional
    public MinistroDeEucaristiaResponseDTO atualizarMinistroDeEucaristia(Long id, MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDeEucaristia ministroDeEucaristia = ministroDeEucaristiaRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Ministro De Eucaristia", id));
        ministroDeEucaristiaMapper.atualizarMinistroDeEucaristiaFromDto(ministroDeEucaristiaRequestDTO, ministroDeEucaristia);
        MinistroDeEucaristia ministroDeEucaristiaSalvo = ministroDeEucaristiaRepository.save(ministroDeEucaristia);

        return ministroDeEucaristiaMapper.toDto(ministroDeEucaristiaSalvo);
    }

    @Override
    @Transactional
    public void deletarMinistroDeEucaristia(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        MinistroDeEucaristia ministroDeEucaristia = ministroDeEucaristiaRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Ministro De Eucaristia", id));
        ministroDeEucaristiaRepository.deleteById(id);
    }
}
