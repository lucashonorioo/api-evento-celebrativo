package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.LeitorRequestDTO;
import com.eventoscelebrativos.dto.response.LeitorResponseDTO;
import com.eventoscelebrativos.service.LeitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/leitores")
public class LeitorController {

    private final LeitorService leitorService;

    public LeitorController(LeitorService leitorService) {
        this.leitorService = leitorService;
    }

    @PostMapping
    public ResponseEntity<LeitorResponseDTO> criarLeitor(@Valid @RequestBody LeitorRequestDTO leitorRequestDTO){
        LeitorResponseDTO leitorResponseDTO = leitorService.criarLeitor(leitorRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(leitorResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(leitorResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<LeitorResponseDTO>> listarTodosLeitores(){
        List<LeitorResponseDTO> leitoresResponseDTO = leitorService.listarTodosLeitor();
        return ResponseEntity.ok().body(leitoresResponseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeitorResponseDTO> buscarLeitorPorId(@PathVariable Long id){
        LeitorResponseDTO leitorResponseDTO = leitorService.buscarLeitorPorId(id);
        return ResponseEntity.ok().body(leitorResponseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeitorResponseDTO> atualizarLeitor(@PathVariable Long id,@Valid @RequestBody LeitorRequestDTO leitorRequestDTO){
        LeitorResponseDTO leitorResponseDTO = leitorService.atualizarLeitor(id, leitorRequestDTO);
        return ResponseEntity.ok().body(leitorResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarLeitor(@PathVariable Long id){
        leitorService.deletarLeitor(id);
        return ResponseEntity.noContent().build();
    }

}
