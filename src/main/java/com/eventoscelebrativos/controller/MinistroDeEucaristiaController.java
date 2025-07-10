package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.MinistroDeEucaristiaRequestDTO;
import com.eventoscelebrativos.dto.response.MinistroDeEucaristiaResponseDTO;
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
    public ResponseEntity<MinistroDeEucaristiaResponseDTO> criarMinistroDeEucaristia(@Valid @RequestBody MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO){
        MinistroDeEucaristiaResponseDTO ministroDeEucaristiaResponseDTO = ministroDeEucaristiaService.criarMinistroDeEucaristia(ministroDeEucaristiaRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(ministroDeEucaristiaResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(ministroDeEucaristiaResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<MinistroDeEucaristiaResponseDTO>> listarTodosMinistrosDeEucaristia(){
        List<MinistroDeEucaristiaResponseDTO> ministrosDeEucaristiaResponseDTO = ministroDeEucaristiaService.listarTodosMinistroDeEucaristia();
        return ResponseEntity.ok().body(ministrosDeEucaristiaResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinistroDeEucaristiaResponseDTO> buscarMinistroDeEucaristiaPorId(@PathVariable Long id){
        MinistroDeEucaristiaResponseDTO ministroDeEucaristiaResponseDTO = ministroDeEucaristiaService.buscarMinistroDeEucaristiaPorId(id);
        return ResponseEntity.ok().body(ministroDeEucaristiaResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinistroDeEucaristiaResponseDTO> atualizarMinistroDeEucaristia(@PathVariable Long id, @Valid @RequestBody MinistroDeEucaristiaRequestDTO ministroDeEucaristiaRequestDTO){
        MinistroDeEucaristiaResponseDTO ministroDeEucaristiaResponseDTO = ministroDeEucaristiaService.atualizarMinistroDeEucaristia(id, ministroDeEucaristiaRequestDTO);
        return ResponseEntity.ok().body(ministroDeEucaristiaResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MinistroDeEucaristiaResponseDTO> deletarMinistroDeEucaristia(@PathVariable Long id){
        ministroDeEucaristiaService.deletarMinistroDeEucaristia(id);
        return ResponseEntity.noContent().build();
    }

}
