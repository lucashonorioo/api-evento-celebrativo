package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.response.AdminNotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationRecipientResponseDTO;
import com.eventoscelebrativos.dto.response.AdminNotificationSummaryResponseDTO;
import com.eventoscelebrativos.exception.exceptions.BadRequestException;
import com.eventoscelebrativos.model.NotificationAudience;
import com.eventoscelebrativos.model.NotificationOrigin;
import com.eventoscelebrativos.service.NotificationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/admin/notificacoes")
@Tag(name = "Notificações (administração)", description = "Histórico administrativo de notificações internas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;

    public NotificationAdminController(NotificationAdminService notificationAdminService) {
        this.notificationAdminService = notificationAdminService;
    }

    @Operation(summary = "Lista o histórico de notificações enviadas, com contadores de destinatários e leitura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico listado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtros ou paginação inválidos"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão (exclusivo ROLE_ADMIN)")
    })
    @GetMapping
    public ResponseEntity<Page<AdminNotificationSummaryResponseDTO>> findHistory(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String audience,
            @Parameter(description = "yyyy-MM-dd, inclusive") @RequestParam(required = false) String startDate,
            @Parameter(description = "yyyy-MM-dd, inclusive") @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long senderPersonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminNotificationSummaryResponseDTO> result = notificationAdminService.findHistory(
                parseOrigin(origin),
                parseAudience(audience),
                parseDate(startDate),
                parseDate(endDate),
                senderPersonId,
                page,
                size
        );
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Detalha uma notificação enviada, com conteúdo completo, remetente, referência, "
            + "source e contadores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificação encontrada"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão (exclusivo ROLE_ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente")
    })
    @GetMapping("/{notificationId}")
    public ResponseEntity<AdminNotificationDetailResponseDTO> findById(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationAdminService.findById(notificationId));
    }

    @Operation(summary = "Lista os destinatários de uma notificação, ordenados por nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Destinatários listados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtro ou paginação inválidos"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão (exclusivo ROLE_ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente")
    })
    @GetMapping("/{notificationId}/destinatarios")
    public ResponseEntity<Page<AdminNotificationRecipientResponseDTO>> findRecipients(
            @PathVariable Long notificationId,
            @Parameter(description = "ALL, UNREAD ou READ") @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationAdminService.findRecipients(notificationId, filter, page, size));
    }

    private NotificationOrigin parseOrigin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationOrigin.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("O campo origin deve ser ADMIN ou SYSTEM");
        }
    }

    private NotificationAudience parseAudience(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationAudience.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("O campo audience deve ser GLOBAL, ADMIN, MINISTRY ou DIRECT");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Datas devem estar no formato yyyy-MM-dd");
        }
    }
}
