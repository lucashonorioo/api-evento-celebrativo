package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PadreRequestDTO;
import com.eventoscelebrativos.dto.response.PadreResponseDTO;
import com.eventoscelebrativos.service.PadreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/padres")
public class PadreController {

    private final PadreService padreService;

    public PadreController(PadreService padreService) {
        this.padreService = padreService;
    }

    @PostMapping
    public ResponseEntity<PadreResponseDTO> criarPadre(@Valid @RequestBody PadreRequestDTO padreRequestDTO){
        PadreResponseDTO padreResponseDTO = padreService.criarPadre(padreRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(padreResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(padreResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<PadreResponseDTO>> listarTodosPadres(){
        List<PadreResponseDTO> padresResponseDTO = padreService.listarTodosPadre();
        return ResponseEntity.ok().body(padresResponseDTO);
    }

    @PostMapping("/{id}")
    public ResponseEntity<PadreResponseDTO> buscarPadrePorId(@PathVariable Long id){
        PadreResponseDTO padreResponseDTO = padreService.buscarPadrePorId(id);
        return ResponseEntity.ok().body(padreResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PadreResponseDTO> atualizarPadre(@PathVariable Long id, @Valid @RequestBody PadreRequestDTO padreRequestDTO){
        PadreResponseDTO padreResponseDTO = padreService.atualizarPadre(id, padreRequestDTO);
        return ResponseEntity.ok().body(padreResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPadre(@PathVariable Long id){
        padreService.deletarPadre(id);
        return ResponseEntity.noContent().build();
    }

}
