package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@Tag(name = "Eventos", description = "Gerenciamento de eventos celebrativos e escalas")
public class CelebrationEventController {

    private final CelebrationEventService celebrationEventService;

    public CelebrationEventController(CelebrationEventService celebrationEventService) {
        this.celebrationEventService = celebrationEventService;
    }

    @Operation(summary = "Cria um evento celebrativo")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<CelebrationEventResponseDTO> createEvent(@Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.createEvent(celebrationEventRequestDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(celebrationEventResponseDTO.getId()).toUri();
        return ResponseEntity.created(location).body(celebrationEventResponseDTO);
    }

    @Operation(summary = "Cria um evento celebrativo com escala")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
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

    @Operation(summary = "Lista eventos celebrativos")
    @GetMapping
    public ResponseEntity<List<CelebrationEventResponseDTO>> findAllEvents(){
        List<CelebrationEventResponseDTO> eventosCelebrativosResponseDTO = celebrationEventService.findAllEvents();
        return ResponseEntity.ok().body(eventosCelebrativosResponseDTO);
    }

    @Operation(summary = "Busca um evento celebrativo por ID")
    @GetMapping(value = "/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> findEventById(@PathVariable Long id){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.findEventById(id);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);

    }

    @Operation(summary = "Consulta a escala de ministros da Eucaristia por período")
    @GetMapping(value = "/escala/eucaristia")
    public ResponseEntity<Page<EucharistScaleEventResponseDTO>> findEucharistScale(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "NÃºmero da pÃ¡gina, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por pÃ¡gina")
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<EucharistScaleEventResponseDTO>  eventoEscalaMinistrosResponseDTOS =
                celebrationEventService.findEucharistScale(PageRequest.of(page, size), startDate, endDate);

        return ResponseEntity.ok(eventoEscalaMinistrosResponseDTOS);
    }

    @Operation(summary = "Consulta escalas por período e tipo de função")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/escalas")
    public ResponseEntity<Page<EventScheduleQueryResponseDTO>> findEventSchedules(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Tipo da função: PRIEST, READER, COMMENTATOR, MINISTER_OF_THE_WORD, EUCHARISTIC_MINISTER")
            @RequestParam("type") EventScheduleType type,
            @Parameter(description = "Número da página, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por página. Máximo: 100")
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean includeUnassigned
    ) {
        Page<EventScheduleQueryResponseDTO> eventSchedules = celebrationEventService.findEventSchedules(
                startDate,
                endDate,
                type,
                page,
                size,
                includeUnassigned
        );

        return ResponseEntity.ok(eventSchedules);
    }

    @Operation(summary = "Consulta a escala completa de um evento celebrativo")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}/escala")
    public ResponseEntity<CelebrationEventScaleDetailResponseDTO> findEventScaleById(@PathVariable Long id) {
        CelebrationEventScaleDetailResponseDTO celebrationEventScaleDetailResponseDTO =
                celebrationEventService.findScaleByEventId(id);
        return ResponseEntity.ok(celebrationEventScaleDetailResponseDTO);
    }

    @Operation(summary = "Atualiza um evento celebrativo")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<CelebrationEventResponseDTO> updateEvent(@PathVariable Long id, @Valid @RequestBody CelebrationEventRequestDTO celebrationEventRequestDTO){
        CelebrationEventResponseDTO celebrationEventResponseDTO = celebrationEventService.updateEvent(id, celebrationEventRequestDTO);
        return ResponseEntity.ok().body(celebrationEventResponseDTO);
    }

    @Operation(summary = "Atualiza a escala de um evento celebrativo")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
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

    @Operation(summary = "Remove um evento celebrativo")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteEventById(@PathVariable Long id){
        celebrationEventService.deleteEventById(id);
        return ResponseEntity.noContent().build();
    }

}
