package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.config.OpenApiConfig;
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
}
