package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinistroDaPalavraRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDaPalavraResponseDTO;
import com.eventoscelebrativos.service.MinistroDaPalavraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/ministrosDaPalavra")
public class MinistroDaPalavraController {

    private final MinistroDaPalavraService ministroDaPalavraService;

    public MinistroDaPalavraController(MinistroDaPalavraService ministroDaPalavraService) {
        this.ministroDaPalavraService = ministroDaPalavraService;
    }

    @PostMapping
    public ResponseEntity<MinistroDaPalavraResponseDTO> criarMinistroDaPalavra(@Valid @RequestBody MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO){
        MinistroDaPalavraResponseDTO ministroDaPalavraResponseDTO = ministroDaPalavraService.criarMinistroDaPalavra(ministroDaPalavraRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ministroDaPalavraResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(ministroDaPalavraResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<MinistroDaPalavraResponseDTO>> listarTodosMinistrosDaPalavra(){
        List<MinistroDaPalavraResponseDTO> ministrosDaPalavraResponseDTO = ministroDaPalavraService.listarTodosMinistroDaPalavra();
        return ResponseEntity.ok().body(ministrosDaPalavraResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinistroDaPalavraResponseDTO> buscarMinistroDaPalavraPorId(@PathVariable Long id){
        MinistroDaPalavraResponseDTO ministroDaPalavraResponseDTO = ministroDaPalavraService.buscarMinistroDaPalavraPorId(id);
        return ResponseEntity.ok().body(ministroDaPalavraResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinistroDaPalavraResponseDTO> atualizarMinistroDaPalavra(@PathVariable Long id, @Valid @RequestBody MinistroDaPalavraRequestDTO ministroDaPalavraRequestDTO){
        MinistroDaPalavraResponseDTO ministroDaPalavraResponseDTO = ministroDaPalavraService.atualizarMinistroDaPalavra(id, ministroDaPalavraRequestDTO);
        return ResponseEntity.ok().body(ministroDaPalavraResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarMinistroDaPalavra(@PathVariable Long id){
        ministroDaPalavraService.deletarMinistroDaPalavra(id);
        return ResponseEntity.noContent().build();
    }


}
