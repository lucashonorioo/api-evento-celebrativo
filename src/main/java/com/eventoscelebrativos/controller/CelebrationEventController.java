package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.service.CelebrationEventService;
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
@RequestMapping(value = "/eventos")
public class CelebrationEventController {

    private final CelebrationEventService celebrationEventService;

    public CelebrationEventController(CelebrationEventService celebrationEventService) {
        this.celebrationEventService = celebrationEventService;
    }

    @PostMapping
    public ResponseEntity<CelebrationEventResponseDTO> createEvent(@Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.createEvent(celebrationEventRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(celebrationEventResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(celebrationEventResponseDTO);
    }

    @GetMapping
    public ResponseEntity<List<CelebrationEventResponseDTO>> findAllEvents(){
        List<CelebrationEventResponseDTO> eventosCelebrativosResponseDTO = celebrationEventService.findAllEvents();
        return ResponseEntity.ok().body(eventosCelebrativosResponseDTO);
    }
    @GetMapping(value = "/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> findEventById(@PathVariable Long id){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.findEventById(id);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);

    }

    @GetMapping(value = "/escala/eucaristia")
    public ResponseEntity<Page<EucharistScaleEventResponseDTO>> findEucharistScale(
            @RequestParam("dataInicial") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam("dataFinal") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            Pageable pageable
    ) {
        Page<EucharistScaleEventResponseDTO>  eventoEscalaMinistrosResponseDTOS =
                celebrationEventService.findEucharistScale(pageable, dataInicial, dataFinal);

        return ResponseEntity.ok(eventoEscalaMinistrosResponseDTOS);
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.updateEvent(id, celebrationEventRequestDTO);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteEventById(@PathVariable Long id){
        celebrationEventService.deleteEventById(id);
        return ResponseEntity.noContent().build();
    }

}
