package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/ministrosDaPalavra")
public class MinisterOfTheWordController {

    private final MinisterOfTheWordService ministerOfTheWordService;

    public MinisterOfTheWordController(MinisterOfTheWordService ministerOfTheWordService) {
        this.ministerOfTheWordService = ministerOfTheWordService;
    }

    @PostMapping
    public ResponseEntity<MinisterOfTheWordResponseDTO> createMinisterOfTheWord(@Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.createMinisterOfTheWord(ministerOfTheWordRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ministerOfTheWordResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(ministerOfTheWordResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<MinisterOfTheWordResponseDTO>> findAllMinistersOfTheWord(){
        List<MinisterOfTheWordResponseDTO> ministrosDaPalavraResponseDTO = ministerOfTheWordService.findAllMinistersOfTheWord();
        return ResponseEntity.ok().body(ministrosDaPalavraResponseDTO);
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> findMinisterOfTheWordById(@PathVariable Long id){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.findMinisterOfTheWordById(id);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> updateMinisterOfTheWord(@PathVariable Long id, @Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.updateMinisterOfTheWord(id, ministerOfTheWordRequestDTO);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteMinisterOfTheWord(@PathVariable Long id){
        ministerOfTheWordService.deleteMinisterOfTheWord(id);
        return ResponseEntity.noContent().build();
    }


}
