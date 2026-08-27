package com.eventoscelebrativos.controller;

import com.eventoscelebrativos.dto.request.CurrentUserProfileUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.AdminPasswordResetRequestDTO;
import com.eventoscelebrativos.dto.request.ParticipationResponseRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.dto.request.PersonAdminUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonMinistriesUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.PersonRoleUpdateRequestDTO;
import com.eventoscelebrativos.dto.request.SelfPasswordChangeRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountCreateRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountEnabledRequestDTO;
import com.eventoscelebrativos.dto.response.CurrentUserProfileResponseDTO;
import com.eventoscelebrativos.dto.response.CurrentUserScheduleResponseDTO;
import com.eventoscelebrativos.dto.response.ParticipationResponseResponseDTO;
import com.eventoscelebrativos.dto.response.PersonAdminResponseDTO;
import com.eventoscelebrativos.dto.response.PersonMinistriesResponseDTO;
import com.eventoscelebrativos.dto.response.PersonRoleUpdateResponseDTO;
import com.eventoscelebrativos.dto.response.PersonParishResponsibilitiesResponseDTO;
import com.eventoscelebrativos.dto.response.UserAccountLifecycleResponseDTO;
import com.eventoscelebrativos.security.AuthenticatedUserResolver;
import com.eventoscelebrativos.service.EventParticipationResponseService;
import com.eventoscelebrativos.service.MinistryCoordinationService;
import com.eventoscelebrativos.service.ParishStaffAssignmentService;
import com.eventoscelebrativos.service.PersonService;
import com.eventoscelebrativos.service.UserAccountLifecycleService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/pessoas")
@Tag(name = "Pessoas", description = "Gerenciamento de permissões de pessoas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PersonController {

    private final PersonService personService;
    private final EventParticipationResponseService eventParticipationResponseService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final UserAccountLifecycleService userAccountLifecycleService;
    private final ParishStaffAssignmentService parishStaffAssignmentService;
    private final MinistryCoordinationService ministryCoordinationService;

    public PersonController(
            PersonService personService,
            EventParticipationResponseService eventParticipationResponseService,
            AuthenticatedUserResolver authenticatedUserResolver,
            UserAccountLifecycleService userAccountLifecycleService,
            ParishStaffAssignmentService parishStaffAssignmentService,
            MinistryCoordinationService ministryCoordinationService
    ) {
        this.personService = personService;
        this.eventParticipationResponseService = eventParticipationResponseService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.userAccountLifecycleService = userAccountLifecycleService;
        this.ministryCoordinationService = ministryCoordinationService;
        this.parishStaffAssignmentService = parishStaffAssignmentService;
    }

    @Operation(summary = "Consulta o perfil da pessoa autenticada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil consultado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @GetMapping(value = "/me")
    public ResponseEntity<CurrentUserProfileResponseDTO> findCurrentUserProfile() {
        CurrentUserProfileResponseDTO responseDTO = personService.getCurrentUserProfile(authenticatedUserResolver.requireCurrentPersonId());
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
            @Valid @RequestBody CurrentUserProfileUpdateRequestDTO requestDTO
    ) {
        CurrentUserProfileResponseDTO responseDTO = personService.updateCurrentUserProfile(
                authenticatedUserResolver.requireCurrentPersonId(), requestDTO);
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
                authenticatedUserResolver.requireCurrentPersonId(),
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
            + "(startAt/endAt). A operacao e idempotente: reenviar a mesma resposta normalizada nao altera respondedAt.")
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
            @PathVariable Long eventId,
            @RequestBody ParticipationResponseRequestDTO requestDTO
    ) {
        ParticipationResponseResponseDTO responseDTO = eventParticipationResponseService.respond(
                authenticatedUserResolver.requireCurrentPersonId(),
                eventId,
                requestDTO
        );
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(
            summary = "Altera a propria senha usando a conta autenticada.",
            description = "Usa o accountId/personId do Bearer JWT, nunca parametros externos. Valida a senha atual contra UserAccount.passwordHash, atualiza somente UserAccount.passwordHash e invalida tokens antigos via token_version."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha alterada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Senha invalida"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "409", description = "Senha atual invalida ou conta inexistente")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @PutMapping(value = "/me/conta/senha")
    public ResponseEntity<Void> changeOwnPassword(@RequestBody SelfPasswordChangeRequestDTO requestDTO) {
        userAccountLifecycleService.changeOwnPassword(requestDTO);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Lista pessoas para administracao de usuarios",
            description = "Contrato administrativo ampliado: cada item traz birthdayDate, personActive, accountExists, "
                    + "accountEnabled e username, alem dos campos ja existentes (id, name, phoneNumber, ministries, roles). "
                    + "Pessoa sem conta: accountExists=false, accountEnabled=null, username=null, roles=[]. "
                    + "Filtros novos: personActive, accountExists, accountEnabled. Quando accountExists estiver ausente, "
                    + "informar accountEnabled ou role implica accountExists=true. accountExists=false combinado com "
                    + "accountEnabled ou role retorna 400 PERSON_ADMIN_FILTERS_INVALID."
    )
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
            @Parameter(description = "true: somente pessoas ativas. false: somente pessoas inativas.")
            @RequestParam(required = false) Boolean personActive,
            @Parameter(description = "true: somente pessoas com conta. false: somente pessoas sem conta.")
            @RequestParam(required = false) Boolean accountExists,
            @Parameter(description = "true: somente contas existentes e habilitadas. false: somente contas existentes e desabilitadas.")
            @RequestParam(required = false) Boolean accountEnabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PersonAdminResponseDTO> people = personService.findPeople(
                name,
                phoneNumber,
                ministry,
                role,
                personActive,
                accountExists,
                accountEnabled,
                page,
                size
        );
        return ResponseEntity.ok(people);
    }

    @Operation(
            summary = "Busca uma pessoa por ID para administracao de usuarios",
            description = "Retorna o contrato administrativo ampliado (mesmo formato da listagem), evitando que o "
                    + "frontend precise combinar este endpoint com GET /pessoas/{id}/conta apenas para montar o estado "
                    + "inicial da pagina administrativa."
    )
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

    @Operation(
            summary = "Atualiza dados cadastrais de uma pessoa (administrativo).",
            description = "Aceita somente name, phoneNumber e birthdayDate. Qualquer outro campo no corpo - inclusive "
                    + "password, createAccess, accessRole, role, roles, ministries, active, personActive, accountEnabled, "
                    + "enabled, username, accountId, tokenVersion, passwordHash, account, ou campo desconhecido - e "
                    + "rejeitado mesmo quando enviado como null, vazio ou false. Quando o telefone muda de fato e a "
                    + "pessoa possui conta, o username e sincronizado e o tokenVersion e incrementado uma unica vez, "
                    + "invalidando tokens antigos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos ou campo nao permitido informado"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Telefone ja associado a outra pessoa")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}")
    public ResponseEntity<PersonAdminResponseDTO> updatePersonAdmin(
            @PathVariable Long id,
            @Valid @RequestBody PersonAdminUpdateRequestDTO requestDTO
    ) {
        PersonAdminResponseDTO responseDTO = personService.updatePersonAdmin(id, requestDTO);
        return ResponseEntity.ok(responseDTO);
    }

    @Operation(
            summary = "Consulta o estado de conta de uma pessoa.",
            description = "Pessoas podem existir com ou sem conta. A resposta nao expoe accountId, passwordHash nem tokenVersion."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado consultado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}/conta")
    public ResponseEntity<UserAccountLifecycleResponseDTO> findAccountState(@PathVariable Long id) {
        return ResponseEntity.ok(userAccountLifecycleService.findAccountState(id));
    }

    @Operation(
            summary = "Cria conta de acesso para pessoa ativa existente.",
            description = "O username e derivado do telefone. A conta nao e excluida fisicamente, usa um unico perfil ROLE_ADMIN ou ROLE_OPERATOR e inicia com token_version zero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Senha ou perfil invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Pessoa inativa, conta existente ou username conflitante")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/{id}/conta")
    public ResponseEntity<UserAccountLifecycleResponseDTO> createAccount(
            @PathVariable Long id,
            @RequestBody UserAccountCreateRequestDTO requestDTO
    ) {
        UserAccountLifecycleResponseDTO responseDTO = userAccountLifecycleService.createAccount(id, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @Operation(
            summary = "Habilita ou desabilita conta sem excluir fisicamente e sem alterar Person.active.",
            description = "Desabilitar invalida tokens antigos. Habilitar nao revalida tokens antigos. A regra de ultimo administrador efetivo usa UserAccount.roles, enabled e Person.active."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estado atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Request invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Conta inexistente, self-disable ou ultimo administrador")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/conta/habilitacao")
    public ResponseEntity<Void> updateAccountEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UserAccountEnabledRequestDTO requestDTO
    ) {
        userAccountLifecycleService.updateAccountEnabled(id, requestDTO);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Ativa ou desativa pessoa sem alterar enabled da conta.",
            description = "Desativacao invalida tokens quando ha conta, bloqueia assignments em andamento ou futuros e preserva historico, participacoes, ministerios e escalas existentes. "
                    + "Quando bloqueada por PERSON_HAS_ACTIVE_ASSIGNMENTS, a resposta 409 traz o corpo padrao de erro "
                    + "(timestamp, status, error, errorCode, path) acrescido do campo assignments: lista de "
                    + "{eventId, eventName, startAt, endAt, assignmentType} dos assignments com evento ainda nao encerrado "
                    + "(endAt > agora), ordenada por startAt, eventId e assignmentType. Nao inclui dados de outras pessoas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Request invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Self-deactivation, ultimo administrador ou assignments ativos (PERSON_HAS_ACTIVE_ASSIGNMENTS traz o campo assignments)")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/status")
    public ResponseEntity<Void> updatePersonActive(
            @PathVariable Long id,
            @Valid @RequestBody PersonActiveRequestDTO requestDTO
    ) {
        userAccountLifecycleService.updatePersonActive(id, requestDTO);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Redefine administrativamente a senha de outra conta.",
            description = "Nao pode ser usado para a propria conta. Atualiza somente UserAccount.passwordHash e invalida tokens antigos via token_version."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Senha invalida"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada"),
            @ApiResponse(responseCode = "409", description = "Conta inexistente ou self-reset administrativo")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/conta/senha")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody AdminPasswordResetRequestDTO requestDTO
    ) {
        userAccountLifecycleService.resetPassword(id, requestDTO);
        return ResponseEntity.noContent().build();
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

    @Operation(
            summary = "Lista os ministerios ativos de uma pessoa.",
            description = "coordinatedMinistries e o subconjunto de ministries que a pessoa coordena atualmente "
                    + "(PersonMinistry.active=true AND coordinator=true); nunca null, lista vazia quando nao ha coordenacao."
    )
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

    @Operation(
            summary = "Atualiza atomicamente o conjunto de ministerios de uma pessoa.",
            description = "Desativar um ministerio (ausente do conjunto desejado) remove sua coordenacao atomicamente. "
                    + "Reativar um ministerio previamente desativado NAO restaura a coordenacao (precisa ser concedida "
                    + "novamente via PUT /{id}/ministries/{ministryId}/coordinator). Um ministerio que permanece no "
                    + "conjunto desejado (unchanged) preserva sua coordenacao como estava."
    )
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

    @Operation(
            summary = "Concede ou reconfirma a coordenacao de um ministerio para uma pessoa.",
            description = "Coordenacao pertence ao vinculo PersonMinistry (ministryId + coordinator), separada de Person, "
                    + "UserAccount, UserAccountRole e ParishStaffAssignment. Exige PersonMinistry(ministryId).active=true "
                    + "(409 MINISTRY_COORDINATION_REQUIRES_ACTIVE_MINISTRY quando ausente ou inativo); nao cria o vinculo "
                    + "ministerial implicitamente. Um ministerio pode ter 0..N coordenadores; uma pessoa pode coordenar "
                    + "0..N ministerios. Nao exige UserAccount. Idempotente: se ja for coordenador, retorna sucesso sem "
                    + "alterar updatedAt. Esta branch apenas persiste a responsabilidade; nao concede nenhuma autoridade "
                    + "adicional ao coordenador."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Coordenacao concedida/reconfirmada com sucesso (ou ja concedida, idempotente)"),
            @ApiResponse(responseCode = "400", description = "Id de ministerio invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa ou ministerio nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Vinculo ministerial inexistente ou inativo (MINISTRY_COORDINATION_REQUIRES_ACTIVE_MINISTRY)")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping(value = "/{id}/ministries/{ministryId}/coordinator")
    public ResponseEntity<Void> grantMinistryCoordinator(@PathVariable Long id, @PathVariable Long ministryId) {
        ministryCoordinationService.grantCoordinator(id, ministryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove a coordenacao de um ministerio de uma pessoa.",
            description = "Nao desativa o vinculo ministerial nem remove nenhuma linha fisicamente. Idempotente: se a "
                    + "pessoa ja nao for coordenadora (nunca foi ou ja foi removida), retorna sucesso sem alterar nada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Coordenacao removida com sucesso (ou ja ausente, idempotente)"),
            @ApiResponse(responseCode = "400", description = "Id de ministerio invalido"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa ou ministerio nao encontrado")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}/ministries/{ministryId}/coordinator")
    public ResponseEntity<Void> revokeMinistryCoordinator(@PathVariable Long id, @PathVariable Long ministryId) {
        ministryCoordinationService.revokeCoordinator(id, ministryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Lista o historico de responsabilidades paroquiais institucionais de uma pessoa (PASTOR, PARISH_SECRETARY).",
            description = "Inclui vinculos ativos e inativos para permitir visao administrativa/historica. Responsabilidade "
                    + "paroquial e conceito separado de ministerio, conta de acesso e role: nao expoe telefone, birthdayDate, "
                    + "username, accountId, role, password nem tokenVersion."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Responsabilidades listadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuario nao autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuario sem permissao"),
            @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(value = "/{id}/responsabilidades-paroquiais")
    public ResponseEntity<PersonParishResponsibilitiesResponseDTO> findPersonParishResponsibilities(@PathVariable Long id) {
        return ResponseEntity.ok(parishStaffAssignmentService.findResponsibilitiesByPerson(id));
    }
}
