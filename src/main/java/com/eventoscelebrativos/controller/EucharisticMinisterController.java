package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(value = "/ministrosDeEucaristia")
public class EucharisticMinisterController {

    private final EucharisticMinisterService eucharisticMinisterService;

    public EucharisticMinisterController(EucharisticMinisterService eucharisticMinisterService) {
        this.eucharisticMinisterService = eucharisticMinisterService;
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<EucharisticMinisterResponseDTO> createEucharisticMinister(@Valid @RequestBody EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = eucharisticMinisterService.createEucharisticMinister(eucharisticMinisterRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(eucharisticMinisterResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(eucharisticMinisterResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<EucharisticMinisterResponseDTO>> findAllEucharisticMinisters(){
        List<EucharisticMinisterResponseDTO> ministrosDeEucaristiaResponseDTO = eucharisticMinisterService.findAllEucharisticMinisters();
        return ResponseEntity.ok().body(ministrosDeEucaristiaResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> findEucharisticMinistersById(@PathVariable Long id){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = eucharisticMinisterService.findEucharisticMinistersById(id);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> updateEucharisticMinisters(@PathVariable Long id, @Valid @RequestBody EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = eucharisticMinisterService.updateEucharisticMinisters(id, eucharisticMinisterRequestDTO);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> deleteEucharisticMinisterById(@PathVariable Long id){
        eucharisticMinisterService.deleteEucharisticMinisterById(id);
        return ResponseEntity.noContent().build();
    }

}
