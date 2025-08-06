package com.eventoscelebrativos.service.impl;




import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.mapper.EucharisticMinisterMapper;
import com.eventoscelebrativos.model.EucharisticMinister;
import com.eventoscelebrativos.repository.EucharisticMinisterRepository;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EucharisticMinisterServiceImpl implements EucharisticMinisterService {

    private final EucharisticMinisterRepository eucharisticMinisterRepository;
    private final EucharisticMinisterMapper eucharisticMinisterMapper;

    public EucharisticMinisterServiceImpl(EucharisticMinisterRepository eucharisticMinisterRepository, EucharisticMinisterMapper eucharisticMinisterMapper) {
        this.eucharisticMinisterRepository = eucharisticMinisterRepository;
        this.eucharisticMinisterMapper = eucharisticMinisterMapper;
    }


    @Override
    @Transactional
    public EucharisticMinisterResponseDTO createEucharisticMinister(EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        EucharisticMinister eucharisticMinister = eucharisticMinisterMapper.toEntity(eucharisticMinisterRequestDTO);
        eucharisticMinister = eucharisticMinisterRepository.save(eucharisticMinister);
        return eucharisticMinisterMapper.toDto(eucharisticMinister);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EucharisticMinisterResponseDTO> findAllEucharisticMinisters() {
        List<EucharisticMinister> ministrosDeEucaristia = eucharisticMinisterRepository.findAll();
        return eucharisticMinisterMapper.toDtoList(ministrosDeEucaristia);
    }

    @Override
    @Transactional(readOnly = true)
    public EucharisticMinisterResponseDTO findEucharisticMinistersById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        EucharisticMinister eucharisticMinister = eucharisticMinisterRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Ministro De Eucaristia", id));

        return eucharisticMinisterMapper.toDto(eucharisticMinister);
    }

    @Override
    @Transactional
    public EucharisticMinisterResponseDTO updateEucharisticMinisters(Long id, EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            EucharisticMinister eucharisticMinister = eucharisticMinisterRepository.getReferenceById(id);
            eucharisticMinisterMapper.updateEucharisticMinisterFromDto(eucharisticMinisterRequestDTO, eucharisticMinister);
            EucharisticMinister eucharisticMinisterSalvo = eucharisticMinisterRepository.save(eucharisticMinister);

            return eucharisticMinisterMapper.toDto(eucharisticMinisterSalvo);
        }catch (EntityNotFoundException e){
            throw new ResourceNotFoundException("Ministro de Eucaristia", id);
        }
    }

    @Override
    @Transactional
    public void deleteEucharisticMinisterById(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        try {
            eucharisticMinisterRepository.deleteById(id);
        }catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Ministro de Eucaristia", id);
        }
    }
}
