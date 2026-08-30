# ADR 0006: Administracao dinamica de Ministry

## 1. Status

Accepted

Data de aceitacao: 2026-08-29

## 2. Contexto

As ADRs 0003, 0004 e 0005 introduziram o catalogo persistente `Ministry`, vincularam
`PersonMinistry` a `Ministry.id` e moveram os boundaries ministeriais escopados para IDs
persistentes. O fluxo administrativo global de usuarios ainda precisava deixar de tratar os cinco
valores legados de `MinistryType` como catalogo administrativo.

Ao mesmo tempo, `tb_person_ministry.ministry_type` continua presente e obrigatorio porque escalas,
notificacoes, adaptadores CRUD legados e compatibilidade PASTOR/PRIEST ainda dependem do enum legado
nesta etapa da migracao.

## 3. Decisao

- Expor operacoes administrativas do catalogo `Ministry` em `/ministerios`.
- Manter o catalogo sem delete fisico: criar, renomear, ativar e desativar sao suportados.
- Usar `Ministry.id` como identidade nos contratos administrativos globais de
  Person/PersonMinistry.
- Retornar vinculos ministeriais administrativos como dados estruturados com `id`, `name` e,
  quando aplicavel, `coordinator`.
- Fazer o frontend admin-users carregar o catalogo do backend e usar `Ministry.id` em filtros e
  atualizacoes de vinculos.
- Criar `tb_ministry_legacy_type_mapping` como bridge persistente entre valores editaveis de
  `Ministry.id` e os cinco valores legados de `MinistryType`.
- Resolver compatibilidade legada em runtime pela tabela de mapping, nao por
  `Ministry.normalizedName`.
- Preservar seguranca de rename: um Ministry legado pode ser renomeado sem quebrar o mapping usado
  por escalas, notificacoes, CRUDs antigos e compatibilidade PASTOR/PRIEST.
- Permitir que Ministries arbitrarios, como `Acolitos`, existam no catalogo sem criar novo enum ou
  comportamento Java especifico.
- Bloquear criacao/reativacao de `PersonMinistry` para Ministries arbitrarios enquanto
  `tb_person_ministry.ministry_type` permanecer obrigatorio, retornando erro de dominio controlado
  em vez de vazar excecoes de persistencia.
- Serializar desativacao do catalogo com add/reactivate/sync de membership pelo lock da linha de
  `Ministry`. Escritas de membership para pessoa existente bloqueiam Person primeiro, depois
  Ministry IDs em ordem crescente deterministica, e fazem refresh do Ministry bloqueado antes de
  validar `active`.

## 4. Compatibilidade desta etapa

- `MinistryType` nao e removido.
- `tb_person_ministry.ministry_type` nao e removido.
- `PersonMinistry.legacyMinistryType` nao e removido.
- `EventAssignment`, `EventAssignmentType`, `ScheduleRole` e contratos do frontend de escala
  permanecem legados.
- `NotificationTargetMinistry`, contratos de request de notificacao e comandos internos de
  notificacao permanecem legados.
- Conceitos de ParishPosition/ParishCapability nao sao introduzidos; PASTOR/PRIEST permanece uma
  preocupacao de compatibilidade sobre o modelo existente.

## 5. Proxima etapa

Remover, em PR posterior, a obrigatoriedade transitoria de `tb_person_ministry.ministry_type` e o
campo correspondente `PersonMinistry.legacyMinistryType`. Essa etapa podera decidir como Ministries
arbitrarios passam a virar memberships operacionais sem mapping para enum legado.
