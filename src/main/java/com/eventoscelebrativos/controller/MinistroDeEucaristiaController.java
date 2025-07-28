package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.EucharisticMinisterRequestDTO;
import com.eventoscelebrativos.dto.response.EucharisticMinisterResponseDTO;
import com.eventoscelebrativos.service.MinistroDeEucaristiaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/ministrosDeEucaristia")
public class MinistroDeEucaristiaController {

    private final MinistroDeEucaristiaService ministroDeEucaristiaService;

    public MinistroDeEucaristiaController(MinistroDeEucaristiaService ministroDeEucaristiaService) {
        this.ministroDeEucaristiaService = ministroDeEucaristiaService;
    }

    @PostMapping
    public ResponseEntity<EucharisticMinisterResponseDTO> criarMinistroDeEucaristia(@Valid @RequestBody EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = ministroDeEucaristiaService.criarMinistroDeEucaristia(eucharisticMinisterRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(eucharisticMinisterResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(eucharisticMinisterResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<EucharisticMinisterResponseDTO>> listarTodosMinistrosDeEucaristia(){
        List<EucharisticMinisterResponseDTO> ministrosDeEucaristiaResponseDTO = ministroDeEucaristiaService.listarTodosMinistroDeEucaristia();
        return ResponseEntity.ok().body(ministrosDeEucaristiaResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> buscarMinistroDeEucaristiaPorId(@PathVariable Long id){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = ministroDeEucaristiaService.buscarMinistroDeEucaristiaPorId(id);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> atualizarMinistroDeEucaristia(@PathVariable Long id, @Valid @RequestBody EucharisticMinisterRequestDTO eucharisticMinisterRequestDTO){
        EucharisticMinisterResponseDTO eucharisticMinisterResponseDTO = ministroDeEucaristiaService.atualizarMinistroDeEucaristia(id, eucharisticMinisterRequestDTO);
        return ResponseEntity.ok().body(eucharisticMinisterResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EucharisticMinisterResponseDTO> deletarMinistroDeEucaristia(@PathVariable Long id){
        ministroDeEucaristiaService.deletarMinistroDeEucaristia(id);
        return ResponseEntity.noContent().build();
    }

}
