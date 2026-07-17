# Roadmap de Migracao do Dominio de Pessoas

Este documento resume as fases para evoluir o dominio de Pessoas, Funcoes Ministeriais, Contas de Acesso e Escalas. O ADR principal e `docs/adr/0001-separate-person-ministry-account-and-event-assignment.md`.

## Objetivo

Executar a migracao de forma incremental, preservando contratos existentes e evitando perda de historico, credenciais ou administradores.

Estado atual: a fase de ADR e decisoes foi concluida em 2026-07-17. O banco persistente-alvo aprovado e MySQL 8.4 LTS. A introducao inicial do Flyway usa `V1` para o schema atual, `V2` para os dados obrigatorios de roles e `V3` para as estruturas paralelas do novo dominio. O seed global `import.sql` foi removido e substituido por dados explicitos por ambiente. Os profiles `local` e `test` ja usam Flyway para criar schema e roles obrigatorias, com dados demonstrativos/fixtures em localizacoes isoladas.

## Fases

| Fase | Dependencias | Entrada | Saida |
| ---- | ------------ | ------- | ----- |
| 1. ADR e decisoes | Nenhuma | Codigo atual analisado | Concluida: ADR aceito em 2026-07-17 |
| 2. Definicao do banco-alvo e estrategia de Flyway/baseline | Fase 1 | Decisoes de dominio aprovadas | Concluida: MySQL 8.4 LTS aprovado, baseline manual definido e migrations iniciais planejadas |
| 3. Flyway e baseline | Fase 2 | Banco-alvo e baseline definidos | `V1` com schema atual, `V2` com roles obrigatorias e profile MySQL seguro |
| 4. Tabelas paralelas | Fase 3 | Baseline validado | Concluida: colunas preparatorias em `tb_person` e tabelas `tb_person_ministry`, `tb_user_account`, `tb_user_account_role`, `tb_event_assignment` criadas de forma aditiva |
| 5. Backfill e auditoria | Fase 4 | Tabelas paralelas disponiveis | Dados copiados, contagens comparadas e divergencias registradas |
| 6. Migrar escalas | Fase 5 | `event_assignment` preenchida | Consultas e escrita de escala usando atribuicao explicita |
| 7. Migrar autenticacao | Fase 5 | `user_account` preenchida | Login lendo conta e preservando JWT atual |
| 8. Reduzir dependencia de subclasses | Fases 6 e 7 | Escalas e contas migradas | Services deixam de depender de subtipo como regra principal |
| 9. API unificada | Fase 8 | Modelo novo estabilizado | Endpoints novos para pessoa, ministerios, conta e escala |
| 10. Migrar frontend | Fase 9 | API nova disponivel | Telas usando contratos novos |
| 11. Depreciar contratos antigos | Fase 10 | Frontend migrado | Politica de deprecacao publicada |
| 12. Remover legado | Fase 11 | Periodo de estabilizacao concluido | Estruturas antigas removidas com migration destrutiva aprovada |

## Estado do Flyway e proxima fase: Backfills versionados

Resultado aprovado:

- Banco persistente-alvo: MySQL 8.4 LTS.
- H2 permanece temporariamente nos profiles `local` e `test`.
- Flyway substitui o Hibernate na criacao de schema dos profiles `local` e `test`.
- `V1` representa o schema atual.
- `V2` insere apenas `ROLE_OPERATOR` e `ROLE_ADMIN`.
- Banco novo executa migrations desde `V1`.
- Banco existente deve ser auditado e receber baseline manual na versao `2`.
- `baseline-on-migrate` nao deve ser habilitado automaticamente.
- `V3` cria colunas preparatorias em `tb_person` e estruturas paralelas do novo dominio.
- O profile `local` usa Flyway, com schema criado por `V1`, roles obrigatorias por `V2` e dados demonstrativos carregados apenas por `db/local/R__load_local_demo_data.sql`.
- O profile `test` usa Flyway, com schema criado por `V1`, roles obrigatorias por `V2` e fixtures carregadas apenas por `db/test/R__load_test_fixtures.sql`.
- Hibernate usa `ddl-auto=validate` nos profiles `local`, `test`, `mysql` e `flyway-test`.
- As roles deixaram de ser duplicadas nas fixtures do profile `test`.

Pre-condicoes para backfills:

- `V1` e `V2` validadas em H2 e MySQL.
- `V3` validada em H2 e MySQL.
- Profiles `local`, `test`, `mysql` e `flyway-test` usando Flyway com localizacoes isoladas.
- Hibernate usando `ddl-auto=validate` nos profiles migrados.
- Fixtures de teste separadas de dados locais.
- Confirmacao explicita de que os proximos backfills preservarao hashes, IDs e vinculos existentes.

Saidas esperadas da proxima fase:

- Backfill de funcoes ministeriais a partir de `person_type`.
- Backfill de contas a partir de `phone_number`, `password` e roles atuais.
- Backfill de atribuicoes de escala a partir de `tb_event_person` e subtipo/`person_type`.
- Consultas de auditoria comparando contagens, IDs e vinculos antes/depois.

Fora do escopo da proxima fase:

- Remover estruturas legadas.
- Remover `person_type`, senha, roles ou vinculos atuais.
- Criar migration destrutiva.

## Separacao de dados por profile

O arquivo global `src/main/resources/import.sql` foi removido para evitar que o mesmo conjunto de dados seja carregado implicitamente por todos os ambientes.

Estado atual:

- `src/main/resources/db/local/R__load_local_demo_data.sql` contem os dados demonstrativos do profile `local`, sem roles, incluindo usuarios de demonstracao, pessoas, locais, eventos e vinculos.
- `src/test/resources/db/test/R__load_test_fixtures.sql` contem as fixtures da suite principal de testes, sem roles, preservando os mesmos IDs implicitos utilizados pelos testes atuais.
- `src/main/resources/db/migration/V2__insert_required_roles.sql` continua sendo a fonte dos dados obrigatorios para bancos novos gerenciados por Flyway.
- O profile `mysql` continua isolado, com `spring.sql.init.mode=never`, Hibernate `validate` e Flyway habilitado.
- O profile `flyway-test` continua validando apenas `V1` e `V2`, sem carregar pessoas, locais ou eventos demonstrativos.
- O profile `local` esta isolado por `spring.flyway.locations=classpath:db/migration,classpath:db/local`.
- O profile `test` esta isolado por `spring.flyway.locations=classpath:db/migration,classpath:db/test`.
- Os profiles `mysql` e `flyway-test` continuam usando apenas `classpath:db/migration`.

Proximas etapas planejadas:

1. Criar backfills versionados para estruturas paralelas.
2. Executar backfill em ambiente descartavel.
3. Auditar contagens e vinculos antes de alterar leitura/escrita funcional.

## Dependencias criticas

- A fase de Flyway depende da definicao do banco-alvo e da estrategia de baseline.
- As migrations de novo dominio dependem da estabilizacao de `V1` e `V2`; a primeira versao disponivel para elas e `V3`.
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
- Banco-alvo aprovado: MySQL 8.4 LTS.
- Compatibilidade entre H2 e banco-alvo avaliada.
- Estrategia de baseline do schema existente definida.
- Uso de `ddl-auto` definido para local, testes e futuro ambiente real.
- Politica de rollback definida.
- Confirmacao explicita de que nao havera migration destrutiva nesta etapa.
- Baseline de banco existente definido como manual na versao `2`, apos auditoria.

### Schema paralelo

Entrada:

- Flyway configurado.
- Baseline validado.
- Baseline validado com Flyway nos profiles `local`, `test`, `mysql` e `flyway-test`.

Saida:

- Novas tabelas criadas sem remover colunas ou tabelas atuais.
- Aplicacao antiga ainda inicia com schema expandido.
- `V3` concluida sem migrar dados e sem alterar o modelo legado ativo.

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
- Restricao `UNIQUE(event_id, person_id)` em `tb_event_assignment` precisar ser revista se o dominio passar a permitir multiplas funcoes da mesma pessoa no mesmo evento.

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
- `chore/add-flyway-baseline`: dependencias Flyway, migrations `V1`/`V2`, profile MySQL e teste isolado de migrations.
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
3. Introduzir Flyway com `V1` para schema atual, `V2` para roles obrigatorias e profiles isolados.
4. Criar tabelas paralelas a partir de `V3`.
5. Executar backfills versionados em ambiente descartavel.
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
- Profiles `local`, `test`, `mysql` e `flyway-test` usam localizacoes Flyway separadas para evitar carga cruzada de dados.
- `V3` e aditiva: nao copia pessoas, contas, roles ou atribuicoes; apenas adiciona colunas, tabelas, constraints e indices.
- O modelo legado continua ativo ate que backfills e mudancas funcionais sejam implementados em etapas posteriores.
- `tb_event_assignment` preserva inicialmente a regra de uma unica funcao por pessoa no mesmo evento por meio de `UNIQUE(event_id, person_id)`.
