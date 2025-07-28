package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.service.ReaderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/leitores")
public class ReaderController {

    private final ReaderService readerService;

    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    @PostMapping
    public ResponseEntity<ReaderResponseDTO> createReader(@Valid @RequestBody ReaderRequestDTO readerRequestDTO){
        ReaderResponseDTO readerResponseDTO = readerService.createReader(readerRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(readerResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(readerResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<ReaderResponseDTO>> findAllReaders(){
        List<ReaderResponseDTO> leitoresResponseDTO = readerService.findAllReaders();
        return ResponseEntity.ok().body(leitoresResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReaderResponseDTO> findReaderById(@PathVariable Long id){
        ReaderResponseDTO readerResponseDTO = readerService.findReaderById(id);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReaderResponseDTO> updateReader(@PathVariable Long id, @Valid @RequestBody ReaderRequestDTO readerRequestDTO){
        ReaderResponseDTO readerResponseDTO = readerService.updateReader(id, readerRequestDTO);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReaderById(@PathVariable Long id){
        readerService.deleteReaderById(id);
        return ResponseEntity.noContent().build();
    }

}
