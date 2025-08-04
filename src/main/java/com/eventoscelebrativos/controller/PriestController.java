package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.PriestRequestDTO;
import com.eventoscelebrativos.dto.response.PriestResponseDTO;
import com.eventoscelebrativos.service.PriestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/padres")
public class PriestController {

    private final PriestService priestService;

    public PriestController(PriestService priestService) {
        this.priestService = priestService;
    }

    @PostMapping
    public ResponseEntity<PriestResponseDTO> createPriest(@Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = priestService.createPriest(priestRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(priestResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(priestResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<PriestResponseDTO>> findAllPriests(){
        List<PriestResponseDTO> padresResponseDTO = priestService.findAllPriests();
        return ResponseEntity.ok().body(padresResponseDTO);
    }

    @PostMapping(value = "/{id}")
    public ResponseEntity<PriestResponseDTO> findPriestById(@PathVariable Long id){
        PriestResponseDTO priestResponseDTO = priestService.findPriestById(id);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<PriestResponseDTO> updatePriest(@PathVariable Long id, @Valid @RequestBody PriestRequestDTO priestRequestDTO){
        PriestResponseDTO priestResponseDTO = priestService.updatePriest(id, priestRequestDTO);
        return ResponseEntity.ok().body(priestResponseDTO);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deletePriestById(@PathVariable Long id){
        priestService.deletePriestById(id);
        return ResponseEntity.noContent().build();
    }

}
