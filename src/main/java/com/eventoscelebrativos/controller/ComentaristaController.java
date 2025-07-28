package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
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
    public ResponseEntity<CommentatorResponseDTO> criarComentarista(@Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = comentaristaService.criarComentarista(commentatorRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(commentatorResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(commentatorResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<CommentatorResponseDTO>> listarTodosComentaristas(){
        List<CommentatorResponseDTO> commentatorResponseDTO = comentaristaService.listarTodosComentaristas();
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentatorResponseDTO> buscarComentaristaPorId(@PathVariable Long id){
        CommentatorResponseDTO commentatorResponseDTO = comentaristaService.buscarComentaristaPorId(id);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentatorResponseDTO> atualizarComentarista(@PathVariable Long id, @Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = comentaristaService.atualizarComentarista(id, commentatorRequestDTO);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarComentarista(@PathVariable Long id){
        comentaristaService.deletarComentarista(id);
        return ResponseEntity.noContent().build();
    }

}
