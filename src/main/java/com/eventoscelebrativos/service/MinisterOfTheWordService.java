package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;

import java.util.List;

public interface MinisterOfTheWordService {

    MinisterOfTheWordResponseDTO createMinisterOfTheWord(MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);
    List<MinisterOfTheWordResponseDTO> findAllMinistersOfTheWord();
    MinisterOfTheWordResponseDTO findMinisterOfTheWordById(Long id);
    MinisterOfTheWordResponseDTO updateMinisterOfTheWord(Long id, MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO);
    void deleteMinisterOfTheWord(Long id);

}
