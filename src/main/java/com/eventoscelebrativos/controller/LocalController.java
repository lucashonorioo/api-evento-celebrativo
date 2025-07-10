package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.LocalRequestDTO;
import com.eventoscelebrativos.dto.response.LocalResponseDTO;
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
    public ResponseEntity<LocalResponseDTO> criarLocal(@Valid @RequestBody LocalRequestDTO localRequestDTO){
        LocalResponseDTO localResponseDTO = localService.criarLocal(localRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(localResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(localResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<LocalResponseDTO>> listarTodosLocais(){
        List<LocalResponseDTO> locaisResponseDTO = localService.listarTodosLocais();
        return ResponseEntity.ok().body(locaisResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocalResponseDTO> buscarLocalPorId(@PathVariable Long id){
        LocalResponseDTO localResponseDTO = localService.buscarLocalPorId(id);
        return ResponseEntity.ok().body(localResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocalResponseDTO> atualizarLocal(@PathVariable Long id,@Valid @RequestBody LocalRequestDTO localRequestDTO){
        LocalResponseDTO localResponseDTO = localService.atualizarLocal(id, localRequestDTO);
        return ResponseEntity.ok().body(localResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarLocal(@PathVariable Long id){
        localService.deletarLocal(id);
        return ResponseEntity.noContent().build();
    }

}
