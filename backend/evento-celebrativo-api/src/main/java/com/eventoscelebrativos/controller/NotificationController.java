package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.request.NotificationCreateRequestDTO;
import com.eventoscelebrativos.dto.response.NotificationCreateResponseDTO;
import com.eventoscelebrativos.dto.response.NotificationDetailResponseDTO;
import com.eventoscelebrativos.dto.response.NotificationSummaryResponseDTO;
import com.eventoscelebrativos.dto.response.UnreadNotificationCountResponseDTO;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.NotificationDeliveryService;
import com.eventoscelebrativos.service.NotificationInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/notificacoes")
@Tag(name = "Notificações", description = "Envio administrativo e caixa pessoal de notificações internas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationInboxService notificationInboxService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public NotificationController(
            NotificationDeliveryService notificationDeliveryService,
            NotificationInboxService notificationInboxService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.notificationDeliveryService = notificationDeliveryService;
        this.notificationInboxService = notificationInboxService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Operation(summary = "Envia uma notificação administrativa imediata (origin=ADMIN, category=GENERAL) "
            + "para o público selecionado. O remetente é sempre a conta autenticada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notificação enviada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou combinação de audience/ministryTypes/personIds incompatível"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão (exclusivo ROLE_ADMIN)"),
            @ApiResponse(responseCode = "409", description = "Nenhum destinatário elegível ou destinatário inválido")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<NotificationCreateResponseDTO> create(
            @Valid @RequestBody NotificationCreateRequestDTO request
    ) {
        NotificationCreateResponseDTO response = notificationDeliveryService.sendAdministrativeNotification(
                authenticatedUserResolver.requireCurrentAccountId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getNotificationId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Lista as notificações da conta autenticada, mais recentes primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificações listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtro ou paginação inválidos"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping
    public ResponseEntity<Page<NotificationSummaryResponseDTO>> findMine(
            @Parameter(description = "ALL, UNREAD ou READ") @RequestParam(defaultValue = "ALL") String filter,
            @Parameter(description = "ALL, ACTIVE ou RESOLVED (conflitos de escala)") @RequestParam(defaultValue = "ALL") String resolutionFilter,
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade por página. Máximo: 100") @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationInboxService.findMine(
                authenticatedUserResolver.requireCurrentAccountId(), filter, resolutionFilter, page, size));
    }

    @Operation(summary = "Detalha uma notificação da conta autenticada. Não marca como lida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificação encontrada"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente ou pertencente a outra conta")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDetailResponseDTO> findMineById(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationInboxService.findMineById(
                authenticatedUserResolver.requireCurrentAccountId(), notificationId));
    }

    @Operation(summary = "Marca uma notificação da conta autenticada como lida (update condicional, "
            + "preserva o primeiro readAt sob concorrência). Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marcada como lida (ou já estava lida)"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente ou pertencente a outra conta")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping("/{notificationId}/leitura")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationInboxService.markAsRead(authenticatedUserResolver.requireCurrentAccountId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marca todas as notificações não lidas da conta autenticada como lidas, "
            + "com um único instante para todas. Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marcadas como lidas"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping("/leitura/todas")
    public ResponseEntity<Void> markAllAsRead() {
        notificationInboxService.markAllAsRead(authenticatedUserResolver.requireCurrentAccountId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna a quantidade de notificações não lidas da conta autenticada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contagem calculada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping("/nao-lidas/contagem")
    public ResponseEntity<UnreadNotificationCountResponseDTO> countUnread() {
        return ResponseEntity.ok(notificationInboxService.countUnread(authenticatedUserResolver.requireCurrentAccountId()));
    }
}
