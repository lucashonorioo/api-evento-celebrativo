# ADR 0003: Fundação do catálogo persistente de ministérios

## 1. Status

Accepted

Data de aceitação: 2026-08-25

## 2. Contexto

O sistema ainda usa `MinistryType` como representação operacional dos cinco ministérios
implementados pelo software (`PRIEST`, `READER`, `COMMENTATOR`, `MINISTER_OF_THE_WORD` e
`EUCHARISTIC_MINISTER`). Essa representação continua necessária nos fluxos atuais de
`PersonMinistry`, escalas, notificações e autorização.

A direção arquitetural, porém, é separar o dado organizacional configurável da capacidade
implementada pelo software. Um ministério paroquial, como "Acólitos", "Música" ou "Pastoral
Familiar", deve poder existir como dado persistente sem exigir novo enum, DTO, controller,
migration e deploy.

## 3. Decisão

- Introduzir `Ministry` como entidade persistente em `tb_ministry`.
- A identidade persistente do ministério é `id BIGINT AUTO_INCREMENT`.
- Não há UUID, `code`, slug técnico, `systemPurpose`, `legacyCode`, `parish_id` ou `tenant_id`.
- `name` é o nome exibido e editável.
- `normalizedName` é derivado deterministicamente de `name` e usado apenas para comparação,
  busca e unicidade.
- A normalização é centralizada no domínio: trim, colapso de whitespace, Unicode Normalization,
  remoção de diacríticos e uppercase com `Locale.ROOT`.
- `Ministry` inicia ativo e expõe operações coesas de domínio: `rename`, `activate` e
  `deactivate`.
- A constraint `UNIQUE(normalized_name)` é a proteção definitiva contra duplicidade e concorrência.
- A migration `V26__create_ministry_catalog.sql` provisiona os cinco ministérios equivalentes ao
  catálogo legado atual apenas como seed de dados.

## 4. Compatibilidade desta etapa

Esta etapa é somente fundação expand. Nenhum cutover foi executado:

- `PersonMinistry` continua usando `MinistryType`.
- `EventAssignment` e `EventAssignmentType` permanecem inalterados.
- Notificações continuam usando `MinistryType`.
- Responsabilidades paroquiais continuam em `ParishStaffAssignment`/`ParishResponsibilityType`.
- Nenhum endpoint HTTP novo foi criado.
- O frontend permanece inalterado.
- A aplicação Java não depende dos IDs gerados para os cinco ministérios seedados.

## 5. Próxima etapa

Migrar `PersonMinistry` para referenciar `Ministry` por `ministry_id` deve ocorrer em um PR
separado, com estratégia própria de expand/backfill/cutover.
