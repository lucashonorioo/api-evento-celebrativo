# ADR 0004: Referência persistente de Ministry em PersonMinistry

## 1. Status

Accepted

Data de aceitação: 2026-08-26

## 2. Contexto

A ADR 0003 introduziu `Ministry` e `tb_ministry` como catálogo persistente, mas manteve
`PersonMinistry` baseado em `MinistryType`. A próxima etapa incremental precisa tornar a relação
ministerial de uma pessoa dependente da identidade persistente do catálogo, sem alterar ainda os
contratos externos que continuam recebendo e retornando `MinistryType`.

## 3. Decisão

- `PersonMinistry` passa a referenciar `Ministry` por `ministry_id`.
- A identidade estrutural canônica do vínculo passa a ser `person_id + ministry_id`.
- A coluna legada `ministry_type` permanece temporariamente para compatibilidade dos contratos
  HTTP, escala e notificações ainda baseados no enum.
- A migração adiciona `ministry_id`, faz backfill a partir de `ministry_type` usando os
  `normalized_name` seedados em `tb_ministry`, valida que nenhum vínculo ficou sem catálogo,
  torna `ministry_id` obrigatório e adiciona FK e `UNIQUE(person_id, ministry_id)`.
- O código Java não depende dos IDs gerados dos ministérios seedados.
- A conversão transitória `MinistryType <-> Ministry` fica confinada em um adapter legado
  específico, para ser removida quando os boundaries deixarem de usar o enum.

## 4. Compatibilidade desta etapa

Esta etapa não altera contratos HTTP nem libera ministérios arbitrários em vínculos operacionais:

- `/ministerios/{ministryType}/pessoas` continua baseado em `MinistryType`.
- `MinistryAuthorizationService` mantém contrato externo por `MinistryType`, mas decide por
  `ministry_id` internamente.
- `EventAssignment` e `EventAssignmentType` permanecem inalterados.
- `NotificationTargetMinistry` permanece com `MinistryType`.
- `ParishStaffAssignment` e `ParishResponsibilityType` permanecem inalterados estruturalmente.
- O frontend permanece inalterado.

## 5. Próxima etapa

Migrar autorização e APIs ministeriais para usar `Ministry.id` nos boundaries externos deve ocorrer
em PR separado. Somente depois disso o enum `MinistryType` poderá ser removido dos fluxos
ministeriais e a vinculação de novos ministérios arbitrários poderá ser liberada.
