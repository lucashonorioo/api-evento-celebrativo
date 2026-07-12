package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinisterOfTheWordRequestDTO;
import com.eventoscelebrativos.dto.response.MinisterOfTheWordResponseDTO;
import com.eventoscelebrativos.service.MinisterOfTheWordService;
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
@RequestMapping(value = "/ministrosDaPalavra")
@Tag(name = "Ministros da Palavra", description = "Gerenciamento de ministros da Palavra")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MinisterOfTheWordController {

    private final MinisterOfTheWordService ministerOfTheWordService;

    public MinisterOfTheWordController(MinisterOfTheWordService ministerOfTheWordService) {
        this.ministerOfTheWordService = ministerOfTheWordService;
    }

    @Operation(summary = "Cria um ministro da Palavra")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<MinisterOfTheWordResponseDTO> createMinisterOfTheWord(@Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.createMinisterOfTheWord(ministerOfTheWordRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ministerOfTheWordResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(ministerOfTheWordResponseDTO);
    }

    @Operation(summary = "Lista ministros da Palavra")
    @GetMapping
    public ResponseEntity<List<MinisterOfTheWordResponseDTO>> findAllMinistersOfTheWord(){
        List<MinisterOfTheWordResponseDTO> ministrosDaPalavraResponseDTO = ministerOfTheWordService.findAllMinistersOfTheWord();
        return ResponseEntity.ok().body(ministrosDaPalavraResponseDTO);
    }

    @Operation(summary = "Busca um ministro da Palavra por ID")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> findMinisterOfTheWordById(@PathVariable Long id){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.findMinisterOfTheWordById(id);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @Operation(summary = "Atualiza um ministro da Palavra")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<MinisterOfTheWordResponseDTO> updateMinisterOfTheWord(@PathVariable Long id, @Valid @RequestBody MinisterOfTheWordRequestDTO ministerOfTheWordRequestDTO){
        MinisterOfTheWordResponseDTO ministerOfTheWordResponseDTO = ministerOfTheWordService.updateMinisterOfTheWord(id, ministerOfTheWordRequestDTO);
        return ResponseEntity.ok().body(ministerOfTheWordResponseDTO);
    }

    @Operation(summary = "Remove um ministro da Palavra")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteMinisterOfTheWord(@PathVariable Long id){
        ministerOfTheWordService.deleteMinisterOfTheWord(id);
        return ResponseEntity.noContent().build();
    }


}
