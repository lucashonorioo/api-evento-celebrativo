package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.LocationRequestDTO;
import com.eventoscelebrativos.dto.response.LocationResponseDTO;
import com.eventoscelebrativos.service.LocalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/locais")
public class LocalController {

    private final LocalService localService;

    public LocalController(LocalService localService) {
        this.localService = localService;
    }

    @PostMapping
    public ResponseEntity<LocationResponseDTO> criarLocal(@Valid @RequestBody LocationRequestDTO locationRequestDTO){
        LocationResponseDTO locationResponseDTO = localService.criarLocal(locationRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(locationResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(locationResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<LocationResponseDTO>> listarTodosLocais(){
        List<LocationResponseDTO> locaisResponseDTO = localService.listarTodosLocais();
        return ResponseEntity.ok().body(locaisResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponseDTO> buscarLocalPorId(@PathVariable Long id){
        LocationResponseDTO locationResponseDTO = localService.buscarLocalPorId(id);
        return ResponseEntity.ok().body(locationResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationResponseDTO> atualizarLocal(@PathVariable Long id, @Valid @RequestBody LocationRequestDTO locationRequestDTO){
        LocationResponseDTO locationResponseDTO = localService.atualizarLocal(id, locationRequestDTO);
        return ResponseEntity.ok().body(locationResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarLocal(@PathVariable Long id){
        localService.deletarLocal(id);
        return ResponseEntity.noContent().build();
    }

}
