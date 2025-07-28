package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.ReaderRequestDTO;
import com.eventoscelebrativos.dto.response.ReaderResponseDTO;
import com.eventoscelebrativos.service.LeitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/leitores")
public class LeitorController {

    private final LeitorService leitorService;

    public LeitorController(LeitorService leitorService) {
        this.leitorService = leitorService;
    }

    @PostMapping
    public ResponseEntity<ReaderResponseDTO> criarLeitor(@Valid @RequestBody ReaderRequestDTO readerRequestDTO){
        ReaderResponseDTO readerResponseDTO = leitorService.criarLeitor(readerRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(readerResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(readerResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<ReaderResponseDTO>> listarTodosLeitores(){
        List<ReaderResponseDTO> leitoresResponseDTO = leitorService.listarTodosLeitor();
        return ResponseEntity.ok().body(leitoresResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReaderResponseDTO> buscarLeitorPorId(@PathVariable Long id){
        ReaderResponseDTO readerResponseDTO = leitorService.buscarLeitorPorId(id);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReaderResponseDTO> atualizarLeitor(@PathVariable Long id, @Valid @RequestBody ReaderRequestDTO readerRequestDTO){
        ReaderResponseDTO readerResponseDTO = leitorService.atualizarLeitor(id, readerRequestDTO);
        return ResponseEntity.ok().body(readerResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarLeitor(@PathVariable Long id){
        leitorService.deletarLeitor(id);
        return ResponseEntity.noContent().build();
    }

}
