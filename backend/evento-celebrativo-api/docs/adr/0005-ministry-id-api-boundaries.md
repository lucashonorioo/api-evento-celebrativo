# ADR 0005: Boundaries ministeriais por Ministry ID

## 1. Status

Accepted

Data de aceitação: 2026-08-27

## 2. Contexto

A ADR 0004 tornou `PersonMinistry` estruturalmente canônico por `person_id + ministry_id`, mas os
boundaries HTTP e de autorização ministerial ainda recebiam `MinistryType`. Isso mantinha os fluxos
escopados de administração de pessoas e coordenação restritos aos cinco valores compile-time do enum,
mesmo quando a persistência já possuía a identidade estável de `Ministry`.

Esta etapa migra apenas os boundaries ministeriais escopados. Ela não cria CRUD dinâmico de
`Ministry`, não libera vínculo operacional para ministérios arbitrários e não remove `MinistryType`
do restante do sistema.

## 3. Decisão

- O endpoint escopado passa de `/ministerios/{ministryType}/pessoas` para
  `/ministerios/{ministryId}/pessoas`.
- Os endpoints administrativos de coordenação passam a receber `ministryId` em
  `/pessoas/{personId}/ministries/{ministryId}/coordinator`.
- `MinistryAuthorizationService` passa a expor `canManageMinistry(Long ministryId)` mantendo o bean
  name `ministryAuthorizationService` para uso em `@PreAuthorize`.
- A decisão de autorização consulta `PersonMinistry` por `person_id + ministry_id`, com
  `active=true` e `coordinator=true`. `ROLE_ADMIN` continua com override global.
- `MinistryPersonManagementService` e `MinistryCoordinationService` passam a receber `Ministry.id`
  nos boundaries migrados.
- Operações de listagem, detalhe, criação, atualização, vínculo e remoção escopadas carregam
  `Ministry` por ID e retornam `404` quando o catálogo informado não existe.
- Não há nova migration nesta etapa; `tb_person_ministry.ministry_type` permanece como coluna legada
  obrigatória enquanto outros contratos ainda dependem de `MinistryType`.

## 4. Compatibilidade desta etapa

- `LegacyMinistryTypeResolver` permanece temporariamente para escala, notificações, CRUDs legados,
  administração global de ministérios da pessoa e preenchimento transitório de `ministry_type`.
- A criação de novos vínculos escopados por `ministryId` ainda exige que o `Ministry` informado tenha
  equivalente legado, porque `ministry_type` continua obrigatório.
- `EventAssignment` e `EventAssignmentType` permanecem inalterados.
- `NotificationTargetMinistry` permanece com `MinistryType`.
- `ParishStaffAssignment` e `ParishResponsibilityType` permanecem inalterados.
- A API global de ministérios da pessoa (`/pessoas/{id}/ministries`) permanece legada nesta etapa.
- O frontend permanece inalterado.

## 5. Próxima etapa

Criar a administração dinâmica do catálogo `Ministry` e migrar os contratos administrativos de
`PersonMinistry` para IDs persistentes deve ocorrer em PR separado. Depois disso será possível
remover a coluna legada `ministry_type`, eliminar `MinistryType` dos boundaries ministeriais restantes
e liberar ministérios arbitrários de forma controlada.
