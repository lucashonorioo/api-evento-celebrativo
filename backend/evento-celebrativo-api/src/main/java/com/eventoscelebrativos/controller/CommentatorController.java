package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.service.CommentatorService;
import com.eventoscelebrativos.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/comentaristas")
@Tag(name = "Comentaristas", description = "Gerenciamento de comentaristas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CommentatorController {

    private final CommentatorService commentatorService;

    public CommentatorController(CommentatorService commentatorService) {
        this.commentatorService = commentatorService;
    }

    @Operation(summary = "Cria um comentarista")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<CommentatorResponseDTO> createCommentator(@Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.createCommentator(commentatorRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(commentatorResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(commentatorResponseDTO);
    }

    @Operation(summary = "Lista comentaristas")
    @GetMapping
    public ResponseEntity<List<CommentatorResponseDTO>> findAllCommentators(){
        List<CommentatorResponseDTO> commentatorResponseDTO = commentatorService.findAllCommentators();
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @Operation(summary = "Busca um comentarista por ID")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<CommentatorResponseDTO> findCommentatorById(@PathVariable Long id){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.findCommentatorById(id);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @Operation(summary = "Atualiza um comentarista")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<CommentatorResponseDTO> updateCommentator(@PathVariable Long id, @Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.updateCommentator(id, commentatorRequestDTO);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @Operation(summary = "Remove um comentarista")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteCommentatorById(@PathVariable Long id){
        commentatorService.deleteCommentatorById(id);
        return ResponseEntity.noContent().build();
    }

}
