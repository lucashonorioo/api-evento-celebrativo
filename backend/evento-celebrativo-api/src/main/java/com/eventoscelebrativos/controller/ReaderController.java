package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.service.ReaderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/leitores")
public class ReaderController {

    private final ReaderService readerService;

    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
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

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<ReaderResponseDTO> findReaderById(@PathVariable Long id){
        ReaderResponseDTO readerResponseDTO = readerService.findReaderById(id);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<ReaderResponseDTO> updateReader(@PathVariable Long id, @Valid @RequestBody ReaderRequestDTO readerRequestDTO){
        ReaderResponseDTO readerResponseDTO = readerService.updateReader(id, readerRequestDTO);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteReaderById(@PathVariable Long id){
        readerService.deleteReaderById(id);
        return ResponseEntity.noContent().build();
    }

}
