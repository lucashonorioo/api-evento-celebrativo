package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;

import java.util.List;

public interface ReaderService {

    ReaderResponseDTO createReader(ReaderRequestDTO readerRequestDTO);
    List<ReaderResponseDTO> findAllReaders();
    ReaderResponseDTO findReaderById(Long id);
    ReaderResponseDTO updateReader(Long id, ReaderRequestDTO readerRequestDTO);
    void deleteReaderById(Long id);

}
