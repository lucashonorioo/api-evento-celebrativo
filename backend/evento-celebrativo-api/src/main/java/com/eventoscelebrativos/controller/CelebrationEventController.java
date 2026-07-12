package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.service.CelebrationEventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<CelebrationEventResponseDTO> createEvent(@Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.createEvent(celebrationEventRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(celebrationEventResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(celebrationEventResponseDTO);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/com-escala")
    public ResponseEntity<CelebrationEventScaleResponseDTO> createEventWithScale(
            @Valid @RequestBody CelebrationEventWithScaleRequestDTO celebrationEventWithScaleRequestDTO
    ){
        CelebrationEventScaleResponseDTO celebrationEventScaleResponseDTO =
                celebrationEventService.createEventWithScale(celebrationEventWithScaleRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/eventos/{id}")
                .buildAndExpand(celebrationEventScaleResponseDTO.getEventId())
                .toUri();
        return ResponseEntity.created(location).body(celebrationEventScaleResponseDTO);
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
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable
    ) {
        Page<EucharistScaleEventResponseDTO>  eventoEscalaMinistrosResponseDTOS =
                celebrationEventService.findEucharistScale(pageable, startDate, endDate);

        return ResponseEntity.ok(eventoEscalaMinistrosResponseDTOS);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.updateEvent(id, celebrationEventRequestDTO);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/escala")
    public ResponseEntity<CelebrationEventScaleResponseDTO> updateEventScale(
            @PathVariable Long id,
            @Valid @RequestBody CelebrationEventScaleRequestDTO celebrationEventScaleRequestDTO
    ){
        CelebrationEventScaleResponseDTO celebrationEventScaleResponseDTO =
                celebrationEventService.updateEventScale(id, celebrationEventScaleRequestDTO);
        return ResponseEntity.ok().body(celebrationEventScaleResponseDTO);
    }

    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteEventById(@PathVariable Long id){
        celebrationEventService.deleteEventById(id);
        return ResponseEntity.noContent().build();
    }

}
