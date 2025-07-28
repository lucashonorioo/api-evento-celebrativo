package com.eventoscelebrativos.service.impl;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;
import com.eventoscelebrativos.exception.exceptions.DatabaseException;
import com.eventoscelebrativos.mapper.LocationMapper;
import com.eventoscelebrativos.model.Location;
import com.eventoscelebrativos.repository.LocationRepository;
import com.eventoscelebrativos.service.LocalService;
import com.eventoscelebrativos.exception.exceptions.BusinessException;
import com.eventoscelebrativos.exception.exceptions.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocalServiceImpl implements LocalService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    public LocalServiceImpl(LocationRepository locationRepository, LocationMapper locationMapper) {
        this.locationRepository = locationRepository;
        this.locationMapper = locationMapper;
    }

    @Override
    @Transactional
    public LocationResponseDTO criarLocal(LocationRequestDTO locationRequestDTO) {
        Location location = locationMapper.toEntity(locationRequestDTO);
        location = locationRepository.save(location);
        return locationMapper.toDto(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponseDTO> listarTodosLocais() {
        List<Location> locais = locationRepository.findAll();
        return locationMapper.toDtoList(locais);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponseDTO buscarLocalPorId(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Location location = locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Local", id));
        return locationMapper.toDto(location);
    }

    @Override
    @Transactional
    public LocationResponseDTO atualizarLocal(Long id, LocationRequestDTO locationRequestDTO) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        Location location = locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Local", id));
        locationMapper.atualizarLocalFromDto(locationRequestDTO, location);
        Location locationSalvo = locationRepository.save(location);

        return locationMapper.toDto(locationSalvo);
    }

    @Override
    @Transactional
    public void deletarLocal(Long id) {
        if(id == null || id <= 0){
            throw new BusinessException("O Id deve ser positivo e não nulo");
        }
        if(!locationRepository.existsById(id)){
            throw new ResourceNotFoundException("Local", id);
        }
        try {
            locationRepository.deleteById(id);
        }
        catch (DataIntegrityViolationException e){
            throw new DatabaseException("Não foi possivel deletar o local, possui outras referencias no sistema");
        }

    }
}
