package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.response.EventAssignmentAuditResponseDTO;
import com.eventoscelebrativos.service.EventAssignmentOperationalAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/event-assignments")
@Tag(name = "Auditoria de atribuicoes de eventos", description = "Operacoes administrativas de auditoria somente leitura")
public class EventAssignmentOperationalAuditController {

    private final EventAssignmentOperationalAuditService eventAssignmentOperationalAuditService;

    public EventAssignmentOperationalAuditController(
            EventAssignmentOperationalAuditService eventAssignmentOperationalAuditService
    ) {
        this.eventAssignmentOperationalAuditService = eventAssignmentOperationalAuditService;
    }

    @Operation(summary = "Audita a consistencia entre escala legada e atribuicoes paralelas")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/consistency")
    public ResponseEntity<EventAssignmentAuditResponseDTO> auditConsistency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long eventId,
            @Parameter(description = "Numero da pagina, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de eventos por pagina. Maximo: 100")
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "true") boolean includeDetails
    ) {
        return ResponseEntity.ok(eventAssignmentOperationalAuditService.audit(
                startDate,
                endDate,
                eventId,
                page,
                size,
                includeDetails
        ));
    }
}
