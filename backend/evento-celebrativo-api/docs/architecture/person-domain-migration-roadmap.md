# Roadmap de Migracao do Dominio de Pessoas

Este documento resume as fases para evoluir o dominio de Pessoas, Funcoes Ministeriais, Contas de Acesso e Escalas. O ADR principal e `docs/adr/0001-separate-person-ministry-account-and-event-assignment.md`.

## Objetivo

Executar a migracao de forma incremental, preservando contratos existentes e evitando perda de historico, credenciais ou administradores.

Estado atual: a fase de ADR e decisoes foi concluida em 2026-07-17. A proxima fase e `Definicao do banco-alvo e estrategia de Flyway/baseline`.

## Fases

| Fase | Dependencias | Entrada | Saida |
| ---- | ------------ | ------- | ----- |
| 1. ADR e decisoes | Nenhuma | Codigo atual analisado | Concluida: ADR aceito em 2026-07-17 |
| 2. Definicao do banco-alvo e estrategia de Flyway/baseline | Fase 1 | Decisoes de dominio aprovadas | Banco escolhido, compatibilidade H2 avaliada e estrategia de baseline definida |
| 3. Flyway e baseline | Fase 2 | Banco-alvo e baseline definidos | Baseline versionado sem alterar modelo funcional |
| 4. Tabelas paralelas | Fase 3 | Baseline validado | `person_ministry`, `user_account`, `user_account_role`, `event_assignment` criadas de forma aditiva |
| 5. Backfill e auditoria | Fase 4 | Tabelas paralelas disponiveis | Dados copiados, contagens comparadas e divergencias registradas |
| 6. Migrar escalas | Fase 5 | `event_assignment` preenchida | Consultas e escrita de escala usando atribuicao explicita |
| 7. Migrar autenticacao | Fase 5 | `user_account` preenchida | Login lendo conta e preservando JWT atual |
| 8. Reduzir dependencia de subclasses | Fases 6 e 7 | Escalas e contas migradas | Services deixam de depender de subtipo como regra principal |
| 9. API unificada | Fase 8 | Modelo novo estabilizado | Endpoints novos para pessoa, ministerios, conta e escala |
| 10. Migrar frontend | Fase 9 | API nova disponivel | Telas usando contratos novos |
| 11. Depreciar contratos antigos | Fase 10 | Frontend migrado | Politica de deprecacao publicada |
| 12. Remover legado | Fase 11 | Periodo de estabilizacao concluido | Estruturas antigas removidas com migration destrutiva aprovada |

## Proxima fase: Definicao do banco-alvo e estrategia de Flyway/baseline

Pre-condicoes:

- Confirmar qual banco sera usado fora de testes.
- Conferir compatibilidade entre H2 e banco-alvo.
- Definir a estrategia de baseline do schema existente.
- Definir como `ddl-auto` sera usado em local, testes e futuro ambiente real.
- Confirmar que nenhuma migration destrutiva sera criada nesta etapa.

Saidas esperadas:

- Banco-alvo documentado.
- Diferencas relevantes entre H2 e banco-alvo registradas.
- Estrategia de baseline aprovada.
- Politica inicial de `ddl-auto` por ambiente definida.
- Plano de rollback para a introducao de Flyway.

Fora do escopo desta proxima fase:

- Criar tabelas novas de dominio.
- Migrar dados.
- Remover `person_type`, senha, roles ou vinculos atuais.
- Criar migration destrutiva.

## Dependencias criticas

- A fase de Flyway depende da definicao do banco-alvo e da estrategia de baseline.
- Backfill depende de migrations aditivas.
- Migracao de escalas depende de `EventAssignment`.
- Migracao de autenticacao depende de `UserAccount`.
- API unificada depende de modelo novo validado por testes e auditoria.
- Remocao de estruturas antigas depende de frontend migrado e periodo de estabilizacao.

## Criterios de entrada e saida por bloco

### Decisao e preparacao

Entrada:

- ADR revisado.
- Questoes abertas respondidas.

Saida:

- Concluido em 2026-07-17 com o ADR aceito.
- Decisoes aprovadas registradas.

### Definicao do banco-alvo e estrategia de Flyway/baseline

Entrada:

- ADR aceito.
- Decisoes de pessoa, conta, funcoes e escalas aprovadas.

Saida:

- Banco-alvo escolhido.
- Compatibilidade entre H2 e banco-alvo avaliada.
- Estrategia de baseline do schema existente definida.
- Uso de `ddl-auto` definido para local, testes e futuro ambiente real.
- Politica de rollback definida.
- Confirmacao explicita de que nao havera migration destrutiva nesta etapa.

### Schema paralelo

Entrada:

- Flyway configurado.
- Baseline validado.

Saida:

- Novas tabelas criadas sem remover colunas ou tabelas atuais.
- Aplicacao antiga ainda inicia com schema expandido.

### Backfill

Entrada:

- Tabelas paralelas disponiveis.
- Scripts revisados.

Saida:

- Pessoas copiadas.
- Funcoes ministeriais derivadas de `person_type`.
- Contas derivadas de `Person.password`, `phoneNumber` e `roles`.
- Atribuicoes derivadas de `tb_event_person` e `person_type`.
- Contagens e amostras conferidas.

### Transicao funcional

Entrada:

- Backfill aprovado.

Saida:

- Escalas passam a ler `EventAssignment`.
- Login passa a ler `UserAccount`.
- Contratos antigos continuam respondendo.

### Migracao de frontend

Entrada:

- API unificada disponivel.

Saida:

- Telas administrativas usam pessoa, ministerios e conta separadamente.
- Tela de escalas usa atribuicoes explicitas.
- Endpoints antigos deixam de ser caminho principal.

## Riscos

- Perda de hashes de senha.
- Perda de administradores ou bloqueio de acesso.
- Perda ou reclassificacao incorreta do historico de escalas.
- Divergencia entre tabelas antigas e novas durante dual-write.
- Queries com N+1 ao introduzir ministerios, contas e atribuicoes.
- `MultipleBagFetchException` em consultas que carreguem muitas colecoes.
- Diferencas de SQL entre H2 e o banco real.
- Quebra de JWT se o claim `username` ou `authorities` mudar.
- Frontend migrar antes de contratos novos estarem estaveis.
- Migrations destrutivas antes do periodo de estabilizacao.
- Escolha de banco-alvo tardia atrasar Flyway e backfill.
- Baseline incorreto impedir rollback simples.

## Decisoes adiadas

Estes itens nao bloqueiam a primeira migracao:

- Validade temporal de funcoes ministeriais.
- Auditoria completa.
- Permitir duas funcoes para a mesma pessoa no mesmo evento.
- Trocar username independente do telefone.
- Exclusao fisica de pessoas.
- Remocao definitiva das estruturas legadas.

## Branches sugeridas

- `docs/person-domain-evolution`: ADR e roadmap.
- `feature/backend-flyway-baseline`: configuracao de Flyway e baseline.
- `feature/backend-person-domain-parallel-schema`: tabelas paralelas.
- `feature/backend-person-domain-backfill`: scripts e validacoes de backfill.
- `feature/backend-event-assignment`: leitura e escrita de escalas por `EventAssignment`.
- `feature/backend-user-account-auth`: autenticacao por `UserAccount`.
- `feature/backend-unified-people-api`: API unificada.
- `feature/frontend-people-domain-v2`: migracao das telas administrativas.

## Itens que bloqueiam o frontend

- Contrato da API unificada de pessoa e ministerios.
- Contrato da API de conta de acesso.
- Contrato de escala usando `EventAssignment`.
- Implementacao da politica aprovada de pessoa inativa nas buscas e seletores.
- Implementacao da regra aprovada de uma unica funcao por pessoa no mesmo evento.
- Mapeamento final de roles e permissoes administrativas.

## Itens que podem ser feitos em paralelo

- Prototipar componentes frontend com mocks tipados.
- Criar testes de contrato para novos DTOs antes da implementacao completa.
- Documentar exemplos de payload.
- Preparar scripts de auditoria de contagens.
- Revisar mensagens de erro e politica de status HTTP.
- Avaliar queries e indices necessarios para listagens administrativas.

## Ordem recomendada

1. ADR aprovado.
2. Definir banco-alvo e estrategia de Flyway/baseline.
3. Introduzir Flyway com baseline.
4. Criar tabelas paralelas.
5. Executar backfill em ambiente descartavel.
6. Comparar contagens e historico.
7. Migrar escala para atribuicao explicita.
8. Migrar autenticacao para conta.
9. Criar API unificada.
10. Migrar frontend.
11. Depreciar endpoints antigos.
12. Remover legado somente apos estabilizacao.

## Observacoes

- Criacao, edicao e exclusao dos modelos novos nao devem ser implementadas antes da fase de schema paralelo.
- Nenhuma migration destrutiva deve ser criada antes da estabilizacao do novo frontend e backend.
- O frontend nao deve depender de mudancas internas de banco; deve depender apenas de contratos HTTP versionados ou estaveis.
- Endpoints atuais permanecem funcionando durante a migracao.
- Tabelas antigas permanecem inicialmente.
- Mudancas de banco comecam de forma aditiva.
- Nao remover `person_type`, senha, roles ou vinculos atuais nas primeiras etapas.
- A versao anterior da aplicacao deve continuar compativel com o schema expandido.
- Hashes devem ser copiados sem alteracao.
- Backfills deverao possuir consultas de auditoria.
