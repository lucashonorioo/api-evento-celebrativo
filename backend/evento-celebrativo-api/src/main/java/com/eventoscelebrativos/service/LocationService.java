package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;

import java.util.List;

public interface LocationService {

    LocationResponseDTO createLocation(LocationRequestDTO locationRequestDTO);
    List<LocationResponseDTO> findAllLocations();
    LocationResponseDTO findLocationById(Long id);
    LocationResponseDTO updateLocation(Long id, LocationRequestDTO locationRequestDTO);
    void deleteLocationById(Long id);


}
