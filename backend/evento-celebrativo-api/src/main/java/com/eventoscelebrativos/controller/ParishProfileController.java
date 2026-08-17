package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
import com.eventoscelebrativos.dto.request.ParishProfileContactUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParishProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.ParishProfileResponseDTO;
import com.eventoscelebrativos.service.ParishProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/paroquia")
@Tag(name = "Paróquia", description = "Perfil institucional único da paróquia representada por esta instalação")
public class ParishProfileController {

    private final ParishProfileService parishProfileService;

    public ParishProfileController(ParishProfileService parishProfileService) {
        this.parishProfileService = parishProfileService;
    }

    @Operation(summary = "Consulta pública do perfil institucional da paróquia. Não exige autenticação. Enquanto o "
            + "perfil ainda não foi configurado pelo administrador, retorna 404 (PARISH_PROFILE_NOT_CONFIGURED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil institucional retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Perfil institucional ainda não configurado (PARISH_PROFILE_NOT_CONFIGURED)")
    })
    @GetMapping
    public ResponseEntity<ParishProfileResponseDTO> findPublicProfile() {
        return ResponseEntity.ok(parishProfileService.findPublicProfile());
    }

    @Operation(summary = "Configura ou atualiza o perfil institucional da paróquia (registro singleton, id=1). A "
            + "primeira chamada configura o perfil; chamadas seguintes atualizam o mesmo registro. Exclusivo ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil institucional configurado/atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao (exclusivo ROLE_ADMIN)")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping
    public ResponseEntity<ParishProfileResponseDTO> update(@Valid @RequestBody ParishProfileUpdateRequestDTO requestDTO) {
        return ResponseEntity.ok(parishProfileService.update(requestDTO));
    }

    @Operation(summary = "Atualiza somente os dados de contato/atendimento da paróquia (telefone institucional, "
            + "e-mail institucional, endereço da secretaria e horário de funcionamento). Não altera nome nem "
            + "diocese, que continuam exclusivos de PUT /paroquia. Exige que o perfil já tenha sido configurado "
            + "por ROLE_ADMIN; caso contrário retorna 404 (PARISH_PROFILE_NOT_CONFIGURED). Permitido para "
            + "ROLE_ADMIN ou ROLE_OPERATOR com responsabilidade de secretaria paroquial ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados de contato atualizados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campo não permitido no contrato"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao (exige ROLE_ADMIN ou secretaria paroquial ativa)"),
            @ApiResponse(responseCode = "404", description = "Perfil institucional ainda não configurado (PARISH_PROFILE_NOT_CONFIGURED)")
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PreAuthorize("@parishAuthorizationService.canPerformSecretaryOperations()")
    @PutMapping("/contato")
    public ResponseEntity<ParishProfileResponseDTO> updateContact(
            @Valid @RequestBody ParishProfileContactUpdateRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(parishProfileService.updateContact(requestDTO));
    }
}
