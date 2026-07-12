package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.service.PriestService;
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
@RequestMapping(value = "/padres")
@Tag(name = "Padres", description = "Gerenciamento de padres")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PriestController {

    private final PriestService priestService;

    public PriestController(PriestService priestService) {
        this.priestService = priestService;
    }

    @Operation(summary = "Cria um padre")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<PriestResponseDTO> createPriest(@Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = priestService.createPriest(priestRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(priestResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(priestResponseDTO);
    }

    @Operation(summary = "Lista padres")
    @GetMapping
    public ResponseEntity<List<PriestResponseDTO>> findAllPriests(){
        List<PriestResponseDTO> padresResponseDTO = priestService.findAllPriests();
        return ResponseEntity.ok().body(padresResponseDTO);
    }

    @Operation(summary = "Busca um padre por ID")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<PriestResponseDTO> findPriestById(@PathVariable Long id){
        PriestResponseDTO priestResponseDTO = priestService.findPriestById(id);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @Operation(summary = "Atualiza um padre")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<PriestResponseDTO> updatePriest(@PathVariable Long id, @Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = priestService.updatePriest(id, priestRequestDTO);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @Operation(summary = "Remove um padre")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deletePriestById(@PathVariable Long id){
        priestService.deletePriestById(id);
        return ResponseEntity.noContent().build();
    }

}
