package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.EventoCelebrativoRequestDTO;
import com.eventoscelebrativos.dto.response.EventoCelebrativoResponseDTO;
import com.eventoscelebrativos.dto.response.EventoEscalaMinistrosResponseDTO;
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
    public ResponseEntity<EventoCelebrativoResponseDTO> criarEvento(@Valid @RequestBody EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO){
        EventoCelebrativoResponseDTO eventoCelebrativoResponseDTO = eventoCelebrativoService.criarEvento(eventoCelebrativoRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(eventoCelebrativoResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(eventoCelebrativoResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<EventoCelebrativoResponseDTO>> listarEventos(){
        List<EventoCelebrativoResponseDTO> eventosCelebrativosResponseDTO = eventoCelebrativoService.listarTodosEventos();
        return ResponseEntity.ok().body(eventosCelebrativosResponseDTO);
    }
    @GetMapping("/{id}")
    public ResponseEntity<EventoCelebrativoResponseDTO> buscarEventoPorId(@PathVariable Long id){
        EventoCelebrativoResponseDTO eventoCelebrativoResponseDTO = eventoCelebrativoService.buscarEventoPorId(id);
        return ResponseEntity.ok().body(eventoCelebrativoResponseDTO);

    }

    @GetMapping("/escala-ministros")
    public ResponseEntity<Page<EventoEscalaMinistrosResponseDTO>> listarEscalaMinistrosEucaristia(
            @RequestParam("dataInicial") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam("dataFinal") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            Pageable pageable
    ) {
        Page<EventoEscalaMinistrosResponseDTO>  eventoEscalaMinistrosResponseDTOS =
                eventoCelebrativoService.listarEscalaMinsEucaristia(pageable, dataInicial, dataFinal);

        return ResponseEntity.ok(eventoEscalaMinistrosResponseDTOS);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventoCelebrativoResponseDTO> atualizarEvento(@PathVariable Long id, @Valid @RequestBody EventoCelebrativoRequestDTO eventoCelebrativoRequestDTO){
        EventoCelebrativoResponseDTO eventoCelebrativoResponseDTO = eventoCelebrativoService.atualizarEvento(id, eventoCelebrativoRequestDTO);
        return ResponseEntity.ok().body(eventoCelebrativoResponseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEvento(@PathVariable Long id){
        eventoCelebrativoService.deletarEvento(id);
        return ResponseEntity.noContent().build();
    }

}
