package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/pessoas")
@Tag(name = "Pessoas", description = "Gerenciamento de permissões de pessoas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PersonController {

    private final PersonService personService;
    private final EventParticipationResponseService eventParticipationResponseService;

    public PersonController(PersonService personService, EventParticipationResponseService eventParticipationResponseService) {
        this.personService = personService;
        this.eventParticipationResponseService = eventParticipationResponseService;
    }

    @Operation(summary = "Consulta o perfil da pessoa autenticada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil consultado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping(value = "/me")
    public ResponseEntity<CurrentUserProfileResponseDTO> findCurrentUserProfile(Authentication authentication) {
        CurrentUserProfileResponseDTO responseDTO = personService.getCurrentUserProfile(authentication.getName());
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Atualiza nome e data de nascimento da pessoa autenticada. Nao permite alterar telefone, senha, roles ou ministerios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping(value = "/me")
    public ResponseEntity<CurrentUserProfileResponseDTO> updateCurrentUserProfile(
            Authentication authentication,
            @Valid @RequestBody CurrentUserProfileUpdateRequestDTO requestDTO
    ) {
        CurrentUserProfileResponseDTO responseDTO = personService.updateCurrentUserProfile(authentication.getName(), requestDTO);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Lista as escalas da pessoa autenticada em um período. A pessoa e determinada pelo token, nao por parametro. "
            + "Cada evento inclui participationStatus (PENDING quando nao ha resposta registrada, ou CONFIRMED/DECLINED), "
            + "declineReason e respondedAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Escalas listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Periodo ou paginacao invalidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping(value = "/me/escalas")
    public ResponseEntity<Page<CurrentUserScheduleResponseDTO>> findCurrentUserSchedules(
            Authentication authentication,
            @Parameter(description = "Data inicial do periodo, inclusive. Formato ISO yyyy-MM-dd")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Data final do periodo, inclusive. Formato ISO yyyy-MM-dd")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Numero da pagina, iniciando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de registros por pagina. Maximo: 100")
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<CurrentUserScheduleResponseDTO> schedules = personService.findCurrentUserSchedules(
                authentication.getName(),
                startDate,
                endDate,
                page,
                size
        );
        return ResponseEntity.ok(schedules);
    }

    @Operation(summary = "Confirma ou recusa a participacao da pessoa autenticada em uma escala. "
            + "A pessoa e determinada exclusivamente pelo token (Bearer JWT), nao por parametro. Vale para todas as funcoes "
            + "da pessoa no evento. Aceita apenas os status CONFIRMED e DECLINED; PENDING e o estado derivado da ausencia de "
            + "resposta e nao pode ser enviado. O motivo da recusa (declineReason) e opcional, limitado a 500 caracteres apos "
            + "trim, e e descartado quando o status e CONFIRMED. Nao e permitido responder apos o inicio do evento "
            + "(eventDate + eventTime). A operacao e idempotente: reenviar a mesma resposta normalizada nao altera respondedAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participacao registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status ausente/invalido, PENDING informado ou motivo acima de 500 caracteres"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa ou evento nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Pessoa sem atribuicao no evento ou evento ja iniciado")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping(value = "/me/escalas/{eventId}/participacao")
    public ResponseEntity<ParticipationResponseResponseDTO> respondToEventParticipation(
            Authentication authentication,
            @PathVariable Long eventId,
            @RequestBody ParticipationResponseRequestDTO requestDTO
    ) {
        ParticipationResponseResponseDTO responseDTO = eventParticipationResponseService.respond(
                authentication.getName(),
                eventId,
                requestDTO
        );
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Lista pessoas para administracao de usuarios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoas listadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Filtros ou paginacao invalidos"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<Page<PersonAdminResponseDTO>> findPeople(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @Parameter(description = "Ministerio ativo: reader, commentator, minister_of_the_word, eucharistic_minister, priest")
            @RequestParam(required = false) String ministry,
            @Parameter(description = "Perfil de acesso: ROLE_ADMIN ou ROLE_OPERATOR")
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PersonAdminResponseDTO> people = personService.findPeople(
                name,
                phoneNumber,
                ministry,
                role,
                page,
                size
        );
        return ResponseEntity.ok(people);
    }

    @Operation(summary = "Busca uma pessoa por ID para administracao de usuarios")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}")
    public ResponseEntity<PersonAdminResponseDTO> findPersonById(@PathVariable Long id) {
        PersonAdminResponseDTO responseDTO = personService.findPersonById(id);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Atualiza o perfil de acesso de uma pessoa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Perfil de acesso invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Operacao administrativa conflitante")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/roles")
    public ResponseEntity<PersonRoleUpdateResponseDTO> updatePersonRole(
            @PathVariable Long id,
            @Valid @RequestBody PersonRoleUpdateRequestDTO requestDTO
    ) {
        PersonRoleUpdateResponseDTO responseDTO = personService.updatePersonRole(id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Lista os ministerios ativos de uma pessoa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ministerios listados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}/ministries")
    public ResponseEntity<PersonMinistriesResponseDTO> findPersonMinistries(@PathVariable Long id) {
        PersonMinistriesResponseDTO responseDTO = personService.findPersonMinistries(id);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(summary = "Atualiza atomicamente o conjunto de ministerios de uma pessoa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ministerios atualizados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Tipo de ministerio invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Remocao de ministerio com vinculo em escala"),
            @ApiResponse(responseCode = "422", description = "Ministerio duplicado no request")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/ministries")
    public ResponseEntity<PersonMinistriesResponseDTO> updatePersonMinistries(
            @PathVariable Long id,
            @Valid @RequestBody PersonMinistriesUpdateRequestDTO requestDTO
    ) {
        PersonMinistriesResponseDTO responseDTO = personService.updatePersonMinistries(id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }
}
