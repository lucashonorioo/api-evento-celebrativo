package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.service.EventoCelebrativoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/eventos")
public class EventoCelebrativoController {

    private final EventoCelebrativoService eventoCelebrativoService;

    public EventoCelebrativoController(EventoCelebrativoService eventoCelebrativoService) {
        this.eventoCelebrativoService = eventoCelebrativoService;
    }

    @PostMapping
    public ResponseEntity<CelebrationEventResponseDTO> criarEvento(@Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = eventoCelebrativoService.criarEvento(celebrationEventRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(celebrationEventResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(celebrationEventResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<CelebrationEventResponseDTO>> listarEventos(){
        List<CelebrationEventResponseDTO> eventosCelebrativosResponseDTO = eventoCelebrativoService.listarTodosEventos();
        return ResponseEntity.ok().body(eventosCelebrativosResponseDTO);
    }
    @GetMapping("/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> buscarEventoPorId(@PathVariable Long id){
        CelebrationEventResponseDTO celebrationEventResponseDTO = eventoCelebrativoService.buscarEventoPorId(id);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);

    }

    @GetMapping("/escala/eucaristia")
    public ResponseEntity<Page<EucharistScaleEventResponseDTO>> listarEscalaMinistrosEucaristia(
            @RequestParam("dataInicial") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam("dataFinal") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            Pageable pageable
    ) {
        Page<EucharistScaleEventResponseDTO>  eventoEscalaMinistrosResponseDTOS =
                eventoCelebrativoService.listarEscalaMinsEucaristia(pageable, dataInicial, dataFinal);

        return ResponseEntity.ok(eventoEscalaMinistrosResponseDTOS);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> atualizarEvento(@PathVariable Long id, @Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = eventoCelebrativoService.atualizarEvento(id, celebrationEventRequestDTO);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEvento(@PathVariable Long id){
        eventoCelebrativoService.deletarEvento(id);
        return ResponseEntity.noContent().build();
    }

}
