package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
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
    public ResponseEntity<PriestResponseDTO> criarPadre(@Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = padreService.criarPadre(priestRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(priestResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(priestResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<PriestResponseDTO>> listarTodosPadres(){
        List<PriestResponseDTO> padresResponseDTO = padreService.listarTodosPadre();
        return ResponseEntity.ok().body(padresResponseDTO);
    }

    @PostMapping("/{id}")
    public ResponseEntity<PriestResponseDTO> buscarPadrePorId(@PathVariable Long id){
        PriestResponseDTO priestResponseDTO = padreService.buscarPadrePorId(id);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PriestResponseDTO> atualizarPadre(@PathVariable Long id, @Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = padreService.atualizarPadre(id, priestRequestDTO);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPadre(@PathVariable Long id){
        padreService.deletarPadre(id);
        return ResponseEntity.noContent().build();
    }

}
