package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CelebrationEventRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventScaleRequestDTO;
import com.eventoscelebrativos.dto.request.CelebrationEventWithScaleRequestDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleParticipationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.CelebrationEventScaleResponseDTO;
import com.eventoscelebrativos.dto.response.EventScheduleQueryResponseDTO;
import com.eventoscelebrativos.dto.response.EucharistScaleEventResponseDTO;
import com.eventoscelebrativos.dto.response.ScheduleUnavailabilityConflictResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.EventScheduleType;
import com.eventoscelebrativos.service.CelebrationEventService;
import com.eventoscelebrativos.service.ScheduleUnavailabilityConflictService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

@RestController
@RequestMapping(value = "/eventos")
@Tag(name = "Eventos", description = "Gerenciamento de eventos celebrativos e escalas")
public class CelebrationEventController {

    private static final DateTimeFormatter CANONICAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    private final CelebrationEventService celebrationEventService;
    private final ScheduleUnavailabilityConflictService scheduleUnavailabilityConflictService;

    public CelebrationEventController(
            CelebrationEventService celebrationEventService,
            ScheduleUnavailabilityConflictService scheduleUnavailabilityConflictService
    ) {
        this.celebrationEventService = celebrationEventService;
        this.scheduleUnavailabilityConflictService = scheduleUnavailabilityConflictService;
    }

    @Operation(summary = "Cria um evento celebrativo sem escala. Permitido para ROLE_ADMIN ou ROLE_OPERATOR com "
            + "responsabilidade de secretaria paroquial ativa.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("@parishAuthorizationService.canPerformSecretaryOperations()")
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

    @Operation(summary = "Consulta a escala completa de um evento celebrativo. Nao inclui o motivo de recusa de participacao; "
            + "veja GET /eventos/{id}/escala/participacoes, exclusivo para administradores.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_OPERATOR')")
    @GetMapping(value = "/{id}/escala")
    public ResponseEntity<CelebrationEventScaleDetailResponseDTO> findEventScaleById(@PathVariable Long id) {
        CelebrationEventScaleDetailResponseDTO celebrationEventScaleDetailResponseDTO =
                celebrationEventService.findScaleByEventId(id);
        return ResponseEntity.ok(celebrationEventScaleDetailResponseDTO);
    }

    @Operation(summary = "Consulta a escala completa de um evento celebrativo com o estado de participacao de cada pessoa. "
            + "Exclusivo para administradores, pois inclui o motivo de recusa.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}/escala/participacoes")
    public ResponseEntity<CelebrationEventScaleParticipationDetailResponseDTO> findEventScaleParticipationById(@PathVariable Long id) {
        CelebrationEventScaleParticipationDetailResponseDTO celebrationEventScaleParticipationDetailResponseDTO =
                celebrationEventService.findScaleParticipationByEventId(id);
        return ResponseEntity.ok(celebrationEventScaleParticipationDetailResponseDTO);
    }

    @Operation(summary = "Atualiza os dados básicos de um evento celebrativo (não altera escala). Permitido para "
            + "ROLE_ADMIN ou ROLE_OPERATOR com responsabilidade de secretaria paroquial ativa.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("@parishAuthorizationService.canPerformSecretaryOperations()")
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

    @Operation(summary = "Consulta os conflitos derivados entre a escala de um evento e indisponibilidades pessoais. "
            + "Um conflito nao bloqueia nem altera o assignment; e apenas exibido ate deixar de existir naturalmente. "
            + "Evento encerrado retorna lista vazia.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{eventId}/escala/conflitos-indisponibilidade")
    public ResponseEntity<List<ScheduleUnavailabilityConflictResponseDTO>> findScheduleUnavailabilityConflictsByEvent(
            @PathVariable Long eventId
    ) {
        List<ScheduleUnavailabilityConflictResponseDTO> conflicts =
                scheduleUnavailabilityConflictService.findByEventId(eventId);
        return ResponseEntity.ok(conflicts);
    }

    @Operation(summary = "Consulta paginada dos conflitos derivados entre escalas e indisponibilidades pessoais em um "
            + "intervalo [startAt, endAt). Considera somente eventos ainda ativos (endAt no presente ou futuro).")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/escala/conflitos-indisponibilidade")
    public ResponseEntity<Page<ScheduleUnavailabilityConflictResponseDTO>> findScheduleUnavailabilityConflictsByRange(
            @Parameter(description = "Inicio do intervalo, inclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("startAt") String startAt,
            @Parameter(description = "Fim do intervalo, exclusive. Formato ISO yyyy-MM-ddTHH:mm:ss")
            @RequestParam("endAt") String endAt,
            @Parameter(description = "Número da página, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por página. Máximo: 100")
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ScheduleUnavailabilityConflictResponseDTO> conflicts = scheduleUnavailabilityConflictService.findByRange(
                parseCanonicalDateTime(startAt),
                parseCanonicalDateTime(endAt),
                page,
                size
        );
        return ResponseEntity.ok(conflicts);
    }

    private LocalDateTime parseCanonicalDateTime(String value) {
        try {
            return LocalDateTime.parse(value, CANONICAL_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(
                    "Data e hora inválidas. Use o formato yyyy-MM-dd'T'HH:mm:ss sem offset ou frações."
            );
        }
    }

}
