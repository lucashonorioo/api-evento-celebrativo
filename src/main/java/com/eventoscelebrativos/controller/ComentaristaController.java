package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.ComentaristaRequestDTO;
import com.eventoscelebrativos.dto.response.ComentaristaResponseDTO;
import com.eventoscelebrativos.service.ComentaristaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/comentaristas")
public class ComentaristaController {

    private final ComentaristaService comentaristaService;

    public ComentaristaController(ComentaristaService comentaristaService) {
        this.comentaristaService = comentaristaService;
    }

    @PostMapping
    public ResponseEntity<ComentaristaResponseDTO> criarComentarista(@Valid @RequestBody ComentaristaRequestDTO comentaristaRequestDTO){
        ComentaristaResponseDTO comentaristaResponseDTO = comentaristaService.criarComentarista(comentaristaRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(comentaristaResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(comentaristaResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<ComentaristaResponseDTO>> listarTodosComentaristas(){
        List<ComentaristaResponseDTO> comentaristaResponseDTO = comentaristaService.listarTodosComentaristas();
        return ResponseEntity.ok().body(comentaristaResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComentaristaResponseDTO> buscarComentaristaPorId(@PathVariable Long id){
        ComentaristaResponseDTO comentaristaResponseDTO = comentaristaService.buscarComentaristaPorId(id);
        return ResponseEntity.ok().body(comentaristaResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComentaristaResponseDTO> atualizarComentarista(@PathVariable Long id,@Valid @RequestBody ComentaristaRequestDTO comentaristaRequestDTO){
        ComentaristaResponseDTO comentaristaResponseDTO = comentaristaService.atualizarComentarista(id, comentaristaRequestDTO);
        return ResponseEntity.ok().body(comentaristaResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarComentarista(@PathVariable Long id){
        comentaristaService.deletarComentarista(id);
        return ResponseEntity.noContent().build();
    }

}
