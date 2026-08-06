package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.request.ReaderUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;

import java.util.List;

public interface ReaderService {

    ReaderResponseDTO createReader(ReaderRequestDTO readerRequestDTO);
    List<ReaderResponseDTO> findAllReaders();
    ReaderResponseDTO findReaderById(Long id);
    ReaderResponseDTO updateReader(Long id, ReaderUpdateRequestDTO readerUpdateRequestDTO);
    void deleteReaderById(Long id);

}
