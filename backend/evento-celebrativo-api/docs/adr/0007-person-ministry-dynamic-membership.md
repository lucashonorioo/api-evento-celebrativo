# ADR 0007: PersonMinistry dinamico por Ministry.id

## 1. Status

Accepted

Data de aceitacao: 2026-08-30

## 2. Contexto

A ADR 0006 consolidou `Ministry` como catalogo administrativo dinamico e introduziu
`tb_ministry_legacy_type_mapping` como bridge persistente entre os cinco valores legados de
`MinistryType` e os registros editaveis de `Ministry`.

Naquela etapa, `tb_person_ministry.ministry_type` ainda permanecia obrigatorio. Isso impedia que um
Ministry arbitrario criado no catalogo administrativo fosse associado a uma pessoa sem tambem criar
um novo enum, mapping legado e deploy Java.

Essa restricao confundia duas ideias que devem permanecer separadas:

- dado organizacional configuravel, representado por `Ministry`;
- capacidade implementada pelo software, ainda representada temporariamente por `MinistryType` em
  subsistemas legados como escala, notificacoes e responsabilidades PASTOR/PRIEST.

## 3. Decisao

- `PersonMinistry` passa a persistir somente `Person`, `Ministry`, `active`, `coordinator` e
  timestamps.
- A identidade ministerial de um vinculo de pessoa passa a ser exclusivamente `Ministry.id`.
- A coluna `tb_person_ministry.ministry_type` e removida por migration incremental.
- Ministries arbitrarios ativos podem receber memberships e coordenadores sem enum, hardcoded ID,
  lookup por nome, migration especifica ou deploy especifico para esse Ministry.
- `tb_ministry_legacy_type_mapping` permanece como infraestrutura interna de compatibilidade.
- Consumidores legados que ainda recebem ou retornam `MinistryType` devem atravessar o mapping:

```text
MinistryType legado
        <-> tb_ministry_legacy_type_mapping
        <-> Ministry.id
        <-> PersonMinistry
```

- Regras legadas como PASTOR/PRIEST e bloqueios de escala continuam aplicaveis somente quando o
  `Ministry.id` possui mapping persistente para o `MinistryType` correspondente.
- Um Ministry sem mapping legado nao ganha `EventAssignmentType`, `ScheduleRole` ou regra especial
  por inferencia. Nomes de Ministry nao definem comportamento de software.
- O boundary administrativo generico continua usando `Ministry.id`, inclusive para autorizacao de
  coordinator em `/ministerios/{ministryId}/pessoas`.

## 4. Compatibilidade desta etapa

- `MinistryType` nao e removido globalmente.
- `tb_ministry_legacy_type_mapping` nao e removida.
- `EventAssignment`, `EventAssignmentType`, `ScheduleRole`, requests de escala e frontend de escala
  permanecem temporariamente legados.
- `NotificationTargetMinistry`, requests de notificacao e frontend de notificacoes permanecem
  temporariamente legados.
- CRUDs especificos legados de Reader, Priest, Commentator, MinisterOfTheWord e
  EucharisticMinister continuam existindo, mas sua interoperabilidade com `PersonMinistry` passa por
  `Ministry.id` e pelo mapping persistente quando o caller ainda fala `MinistryType`.
- Renomear um Ministry legado, como Presbiteros, nao altera sua capacidade legada porque a bridge e
  persistente por ID, nao por `name` nem `normalizedName`.

## 5. Consequencias

- A constraint canonica de unicidade de membership e `UNIQUE(person_id, ministry_id)`.
- `ministry_id` permanece `NOT NULL` e com FK para `tb_ministry`.
- A desativacao de Ministry continua impedindo memberships ativos daquele Ministry.
- `syncMinistriesById` aceita qualquer Ministry ativo e nao filtra por mapping legado.
- Assignment blockers legados sao aplicados apenas quando houver mapping persistente para um
  `EventAssignmentType` existente.

## 6. Proxima etapa

Migrar notificacoes de `MinistryType` para `Ministry.id`, removendo mais um consumidor direto do
enum legado sem reintroduzir acoplamento por nome de Ministry.
