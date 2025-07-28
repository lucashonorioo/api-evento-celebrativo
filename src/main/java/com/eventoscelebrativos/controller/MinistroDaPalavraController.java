package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
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
    public ResponseEntity<MinisterOfTheWordResponseDTO> criarMinistroDaPalavra(@Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministroDaPalavraService.criarMinistroDaPalavra(ministerOfTheWordRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ministerOfTheWordResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(ministerOfTheWordResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<MinisterOfTheWordResponseDTO>> listarTodosMinistrosDaPalavra(){
        List<MinisterOfTheWordResponseDTO> ministrosDaPalavraResponseDTO = ministroDaPalavraService.listarTodosMinistroDaPalavra();
        return ResponseEntity.ok().body(ministrosDaPalavraResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> buscarMinistroDaPalavraPorId(@PathVariable Long id){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministroDaPalavraService.buscarMinistroDaPalavraPorId(id);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> atualizarMinistroDaPalavra(@PathVariable Long id, @Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministroDaPalavraService.atualizarMinistroDaPalavra(id, ministerOfTheWordRequestDTO);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarMinistroDaPalavra(@PathVariable Long id){
        ministroDaPalavraService.deletarMinistroDaPalavra(id);
        return ResponseEntity.noContent().build();
    }


}
