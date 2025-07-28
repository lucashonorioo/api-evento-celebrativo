package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;
import com.eventoscelebrativos.service.CommentatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/comentaristas")
public class CommentatorController {

    private final CommentatorService commentatorService;

    public CommentatorController(CommentatorService commentatorService) {
        this.commentatorService = commentatorService;
    }

    @PostMapping
    public ResponseEntity<CommentatorResponseDTO> createCommentator(@Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.createCommentator(commentatorRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(commentatorResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(commentatorResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<CommentatorResponseDTO>> findAllCommentators(){
        List<CommentatorResponseDTO> commentatorResponseDTO = commentatorService.findAllCommentators();
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentatorResponseDTO> findCommentatorById(@PathVariable Long id){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.findCommentatorById(id);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentatorResponseDTO> updateCommentator(@PathVariable Long id, @Valid @RequestBody CommentatorRequestDTO commentatorRequestDTO){
        CommentatorResponseDTO commentatorResponseDTO = commentatorService.updateCommentator(id, commentatorRequestDTO);
        return ResponseEntity.ok().body(commentatorResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCommentatorById(@PathVariable Long id){
        commentatorService.deleteCommentatorById(id);
        return ResponseEntity.noContent().build();
    }

}
