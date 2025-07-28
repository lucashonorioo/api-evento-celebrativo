package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.service.EucharisticMinisterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/ministrosDeEucaristia")
public class EucharisticMinisterController {

    private final EucharisticMinisterService eucharisticMinisterService;

    public EucharisticMinisterController(EucharisticMinisterService eucharisticMinisterService) {
        this.eucharisticMinisterService = eucharisticMinisterService;
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> findEucharisticMinistersById(@PathVariable Long id){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = eucharisticMinisterService.findEucharisticMinistersById(id);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> updateEucharisticMinisters(@PathVariable Long id, @Valid @RequestBody EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = eucharisticMinisterService.updateEucharisticMinisters(id, eucharisticMinisterRequestDTO);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> deleteEucharisticMinisterById(@PathVariable Long id){
        eucharisticMinisterService.deleteEucharisticMinisterById(id);
        return ResponseEntity.noContent().build();
    }

}
