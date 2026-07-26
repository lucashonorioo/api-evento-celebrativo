# Roadmap de Migracao do Dominio de Pessoas

Este documento resume as fases para evoluir o dominio de Pessoas, Funcoes Ministeriais, Contas de Acesso e Escalas. O ADR principal e `docs/adr/0001-separate-person-ministry-account-and-event-assignment.md`.

## Objetivo

Executar a migracao de forma incremental, preservando contratos existentes e evitando perda de historico, credenciais ou administradores.

Estado atual: a fase de ADR e decisoes foi concluida em 2026-07-17. O banco persistente-alvo aprovado e MySQL 8.4 LTS. A introducao inicial do Flyway usa `V1` para o schema atual, `V2` para os dados obrigatorios de roles, `V3` para as estruturas paralelas do novo dominio, `V4` para o backfill auditavel de funcoes ministeriais legadas e `V5` para o backfill auditavel de atribuicoes de eventos. O seed global `import.sql` foi removido e substituido por dados explicitos por ambiente. Os profiles `local` e `test` ja usam Flyway para criar schema e roles obrigatorias, com dados demonstrativos/fixtures em localizacoes isoladas. A camada Java inicial de `PersonMinistry` foi criada e os CRUDs ministeriais legados fazem write-through para `tb_person_ministry`, sem alterar contratos HTTP. A leitura paralela por `tb_person_ministry` e a auditoria interna de compatibilidade ja existem para validacao da migracao. As listagens ministeriais legadas possuem shadow read interno. As listagens `GET /leitores`, `GET /comentaristas`, `GET /padres`, `GET /ministrosDaPalavra` e `GET /ministrosDeEucaristia` estao preparadas para origem oficial configuravel entre `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL` em todos os profiles (base, `local`, `test` e `mysql`), com rollback independente para `LEGACY` por variavel de ambiente em cada uma das cinco categorias. A camada Java inicial de `EventAssignment` tambem ja existe e os fluxos legados de criacao de evento com escala, atualizacao de escala e exclusao de evento fazem write-through para `tb_event_assignment`; `V5` garante os assignments dos eventos legados a partir de `tb_event_person` e `person_type`; a leitura paralela interna, a auditoria de equivalencia, o shadow read configuravel de assignments e a auditoria operacional administrativa sob demanda estao disponiveis para validacao, com shadow read habilitado somente no profile `local`; `GET /eventos/{id}/escala`, `GET /eventos/escala/eucaristia` e `GET /eventos/escalas` possuem origem oficial configuravel entre `LEGACY` e `PARALLEL`, com todos os profiles em `PARALLEL` por padrao e rollback independente para `LEGACY`; `GET /eventos/{id}` e listagem geral de eventos continuam legados.

Auditoria de dependencias legadas concluida em 2026-07-24: o relatorio tecnico esta em [`person-domain-legacy-dependency-audit.md`](person-domain-legacy-dependency-audit.md). A auditoria confirma que `tb_event_person`, `person_type`, subclasses legadas de `Person`, leituras `LEGACY`, flags de shadow read e servicos de compatibilidade ainda devem permanecer ate branches especificas de remocao, preservando rollback, contratos HTTP, dados existentes e integracoes.

## Fases

| Fase | Dependencias | Entrada | Saida |
| ---- | ------------ | ------- | ----- |
| 1. ADR e decisoes | Nenhuma | Codigo atual analisado | Concluida: ADR aceito em 2026-07-17 |
| 2. Definicao do banco-alvo e estrategia de Flyway/baseline | Fase 1 | Decisoes de dominio aprovadas | Concluida: MySQL 8.4 LTS aprovado, baseline manual definido e migrations iniciais planejadas |
| 3. Flyway e baseline | Fase 2 | Banco-alvo e baseline definidos | `V1` com schema atual, `V2` com roles obrigatorias e profile MySQL seguro |
| 4. Tabelas paralelas | Fase 3 | Baseline validado | Concluida: colunas preparatorias em `tb_person` e tabelas `tb_person_ministry`, `tb_user_account`, `tb_user_account_role`, `tb_event_assignment` criadas de forma aditiva |
| 5. Backfill e auditoria | Fase 4 | Tabelas paralelas disponiveis | Em andamento: `V4` garante funcoes ministeriais derivadas de `person_type` e `V5` garante atribuicoes de eventos derivadas de `tb_event_person` + `person_type`; backfill de contas ainda pendente |
| 6. Migrar escalas | Fase 5 | `event_assignment` preenchida | Em andamento: escrita em compatibilidade, backfill concluido, leitura paralela interna, auditoria, shadow read configuravel, cutovers controlados do detalhe da escala, escala eucaristica e consulta mensal disponiveis |
| 7. Migrar autenticacao | Fase 5 | `user_account` preenchida | Login lendo conta e preservando JWT atual |
| 8. Reduzir dependencia de subclasses | Fases 6 e 7 | Escalas e contas migradas | Services deixam de depender de subtipo como regra principal |
| 9. API unificada | Fase 8 | Modelo novo estabilizado | Endpoints novos para pessoa, ministerios, conta e escala |
| 10. Migrar frontend | Fase 9 | API nova disponivel | Telas usando contratos novos |
| 11. Depreciar contratos antigos | Fase 10 | Frontend migrado | Politica de deprecacao publicada |
| 12. Remover legado | Fase 11 | Periodo de estabilizacao concluido | Estruturas antigas removidas com migration destrutiva aprovada |

## Estado do Flyway e backfill de PersonMinistry

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
- A camada Java de `PersonMinistry` ja existe, com enum `MinistryType`, entidade, repository e servico interno de compatibilidade.
- Os CRUDs legados de leitores, comentaristas, padres, ministros da Palavra e ministros da Eucaristia agora garantem o vinculo ministerial correspondente em criacao e atualizacao.
- Deletes legados removem os vinculos de `tb_person_ministry` antes da exclusao fisica da pessoa.
- `V4` realiza o backfill de `tb_person_ministry` a partir do discriminator legado `person_type`.
- O mapeamento aplicado por `V4` e: `reader` -> `READER`, `commentator` -> `COMMENTATOR`, `priest` -> `PRIEST`, `minister_of_the_word` -> `MINISTER_OF_THE_WORD`, `eucharistic_minister` -> `EUCHARISTIC_MINISTER`.
- Vinculos ministeriais ja existentes nao sao duplicados; vinculos inativos da funcao legada sao reativados; funcoes adicionais sao preservadas.
- O write-through dos CRUDs legados e o backfill `V4` coexistem durante a transicao.
- A leitura paralela permite consultar pessoas por funcao ativa em `tb_person_ministry` e carregar funcoes ativas em lote, sem substituir `ReaderRepository`, `CommentatorRepository`, `PriestRepository`, `MinisterOfTheWordRepository` ou `EucharisticMinisterRepository`.
- A auditoria interna compara o subtipo legado com o vinculo ministerial esperado, nao modifica dados e nao roda automaticamente no startup.
- Funcoes adicionais ativas sao consideradas validas. Exemplo: uma pessoa legada `Reader` pode ter `READER` e `COMMENTATOR`; isso nao e divergencia.
- A auditoria detecta vinculo esperado ausente, vinculo esperado inativo e subtipo legado sem mapeamento ministerial suportado.
- A equivalencia entre repositories legados e leitura paralela foi comprovada para os dados migrados atuais dos cinco ministerios.
- As listagens de leitores, comentaristas, padres, ministros da Palavra e ministros da Eucaristia possuem shadow read interno por `tb_person_ministry`.
- As flags de shadow read permanecem desabilitadas por padrao: `reader-enabled`, `commentator-enabled`, `priest-enabled`, `minister-of-the-word-enabled` e `eucharistic-minister-enabled`.
- `GET /leitores` possui origem oficial configuravel por `app.person-ministry.read-source.reader`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- `GET /comentaristas` possui origem oficial configuravel por `app.person-ministry.read-source.commentator`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- `GET /padres` possui origem oficial configuravel por `app.person-ministry.read-source.priest`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- `GET /ministrosDaPalavra` possui origem oficial configuravel por `app.person-ministry.read-source.minister-of-the-word`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- `GET /ministrosDeEucaristia` possui origem oficial configuravel por `app.person-ministry.read-source.eucharistic-minister`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- Base e os profiles `local`, `test` e `mysql` definem `app.person-ministry.read-source.reader=${PERSON_MINISTRY_READ_SOURCE_READER:PARALLEL}`, `app.person-ministry.read-source.commentator=${PERSON_MINISTRY_READ_SOURCE_COMMENTATOR:PARALLEL}`, `app.person-ministry.read-source.priest=${PERSON_MINISTRY_READ_SOURCE_PRIEST:PARALLEL}`, `app.person-ministry.read-source.minister-of-the-word=${PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD:PARALLEL}` e `app.person-ministry.read-source.eucharistic-minister=${PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER:PARALLEL}` para validar a leitura oficial por `tb_person_ministry`; o campo Java de cada propriedade mantem `LEGACY` como fallback interno somente quando nenhum arquivo de properties define o valor, seguindo o mesmo padrao ja adotado por `EventAssignmentReadSourceProperties`.
- O rollback operacional de cada uma das cinco listagens continua disponivel de forma independente definindo a variavel de ambiente correspondente (`PERSON_MINISTRY_READ_SOURCE_READER`, `PERSON_MINISTRY_READ_SOURCE_COMMENTATOR`, `PERSON_MINISTRY_READ_SOURCE_PRIEST`, `PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD` ou `PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER`) como `LEGACY`, sem alteracao de codigo ou banco e sem afetar as demais quatro categorias.
- No modo `LEGACY`, `GET /leitores` preserva o `ReaderRepository.findAll()` como fonte oficial e pode executar o shadow read quando a flag de leitores estiver habilitada.
- No modo `PARALLEL`, `GET /leitores` usa vinculos ativos `READER` em `tb_person_ministry` como fonte oficial, ordenando por `name ASC, id ASC`.
- O modo `PARALLEL` pode incluir pessoas de outros subtipos legados quando elas tiverem funcao adicional `READER` ativa; isso faz parte do modelo novo de multiplas funcoes.
- O rollback operacional local da leitura de leitores pode ser feito definindo `PERSON_MINISTRY_READ_SOURCE_READER=LEGACY`, sem alteracao de codigo.
- A leitura oficial `PARALLEL` de leitores nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- O ciclo de vida de leitores foi validado com leitura oficial `PARALLEL`: criacao, atualizacao, reativacao de vinculo `READER`, preservacao de funcoes adicionais e exclusao.
- No modo `LEGACY`, `GET /comentaristas` preserva o `CommentatorRepository.findAll()` como fonte oficial e pode executar o shadow read quando a flag de comentaristas estiver habilitada.
- No modo `PARALLEL`, `GET /comentaristas` usa vinculos ativos `COMMENTATOR` em `tb_person_ministry` como fonte oficial, ordenando por `name ASC, id ASC`.
- O modo `PARALLEL` pode incluir pessoas de outros subtipos legados quando elas tiverem funcao adicional `COMMENTATOR` ativa; isso faz parte do modelo novo de multiplas funcoes.
- O rollback operacional local da leitura de comentaristas pode ser feito definindo `PERSON_MINISTRY_READ_SOURCE_COMMENTATOR=LEGACY`, sem alteracao de codigo ou banco.
- A leitura oficial `PARALLEL` de comentaristas nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- O ciclo de vida de comentaristas foi validado com leitura oficial `PARALLEL`: criacao, atualizacao, reativacao de vinculo `COMMENTATOR`, preservacao de funcoes adicionais e exclusao.
- No modo `LEGACY`, `GET /padres` preserva o `PriestRepository.findAll()` como fonte oficial e pode executar o shadow read quando a flag de padres estiver habilitada.
- No modo `PARALLEL`, `GET /padres` usa vinculos ativos `PRIEST` em `tb_person_ministry` como fonte oficial, ordenando por `name ASC, id ASC`.
- O modo `PARALLEL` pode incluir pessoas de outros subtipos legados quando elas tiverem funcao adicional `PRIEST` ativa; isso faz parte do modelo novo de multiplas funcoes.
- A associacao de padres em eventos ainda usa o modelo legado e valida `Priest.class` a partir do `priestId`. Assim, uma pessoa de outro subtipo com funcao adicional `PRIEST` pode aparecer em `GET /padres` paralelo, mas nao necessariamente pode ser associada a um evento pelo fluxo atual.
- O rollback operacional local da leitura de padres pode ser feito definindo `PERSON_MINISTRY_READ_SOURCE_PRIEST=LEGACY`, sem alteracao de codigo ou banco.
- A leitura oficial `PARALLEL` de padres nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- O ciclo de vida de padres foi validado com leitura oficial `PARALLEL`: criacao, atualizacao, reativacao de vinculo `PRIEST`, preservacao de funcoes adicionais, conflito de exclusao quando vinculado a evento e exclusao de padre isolado.
- No modo `LEGACY`, `GET /ministrosDaPalavra` preserva o `MinisterOfTheWordRepository.findAll()` como fonte oficial e pode executar o shadow read quando a flag de ministros da Palavra estiver habilitada.
- No modo `PARALLEL`, `GET /ministrosDaPalavra` usa vinculos ativos `MINISTER_OF_THE_WORD` em `tb_person_ministry` como fonte oficial, ordenando por `name ASC, id ASC`.
- O modo `PARALLEL` pode incluir pessoas de outros subtipos legados quando elas tiverem funcao adicional `MINISTER_OF_THE_WORD` ativa; isso faz parte do modelo novo de multiplas funcoes.
- A associacao de ministros da Palavra em escalas ainda usa o modelo legado e valida `MinisterOfTheWord.class` a partir de `ministerOfTheWordIds`. Assim, uma pessoa de outro subtipo com funcao adicional `MINISTER_OF_THE_WORD` pode aparecer em `GET /ministrosDaPalavra` paralelo, mas nao necessariamente pode ser incluida em escala pelo fluxo atual.
- O rollback operacional local da leitura de ministros da Palavra pode ser feito definindo `PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD=LEGACY`, sem alteracao de codigo ou banco.
- A leitura oficial `PARALLEL` de ministros da Palavra nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- O ciclo de vida de ministros da Palavra foi validado com leitura oficial `PARALLEL`: criacao, atualizacao, reativacao de vinculo `MINISTER_OF_THE_WORD`, preservacao de funcoes adicionais, conflito de exclusao quando vinculado a evento e exclusao de ministro isolado.
- No modo `LEGACY`, `GET /ministrosDeEucaristia` preserva o `EucharisticMinisterRepository.findAll()` como fonte oficial e pode executar o shadow read quando a flag de ministros da Eucaristia estiver habilitada.
- No modo `PARALLEL`, `GET /ministrosDeEucaristia` usa vinculos ativos `EUCHARISTIC_MINISTER` em `tb_person_ministry` como fonte oficial, ordenando por `name ASC, id ASC`.
- O modo `PARALLEL` pode incluir pessoas de outros subtipos legados quando elas tiverem funcao adicional `EUCHARISTIC_MINISTER` ativa; isso faz parte do modelo novo de multiplas funcoes.
- A associacao de ministros da Eucaristia em escalas ainda usa o modelo legado e valida `EucharisticMinister.class` a partir de `eucharisticMinisterIds`. Assim, uma pessoa de outro subtipo com funcao adicional `EUCHARISTIC_MINISTER` pode aparecer em `GET /ministrosDeEucaristia` paralelo, mas nao necessariamente pode ser incluida em escala pelo fluxo atual.
- O rollback operacional local da leitura de ministros da Eucaristia pode ser feito definindo `PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER=LEGACY`, sem alteracao de codigo ou banco.
- A leitura oficial `PARALLEL` de ministros da Eucaristia nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- O ciclo de vida de ministros da Eucaristia foi validado com leitura oficial `PARALLEL`: criacao, atualizacao, reativacao de vinculo `EUCHARISTIC_MINISTER`, preservacao de funcoes adicionais, conflito de exclusao quando vinculado a evento e exclusao de ministro isolado.
- O write-through mantem a consistencia em tempo real entre `Reader` legado e `tb_person_ministry` durante criacao e atualizacao.
- Updates consecutivos preservam um unico vinculo `READER`; quando o vinculo esperado estiver inativo, o update reativa o mesmo registro sem criar duplicidade.
- Funcoes adicionais da pessoa sao preservadas por updates do CRUD legado de leitores.
- Deletes de leitores removem os vinculos de `tb_person_ministry` antes da pessoa, evitando vinculos orfaos.
- O rollback operacional para `LEGACY` continua disponivel mesmo apos validar o ciclo completo em `PARALLEL`.
- A comparacao do shadow read nas listagens atuais usa composicao de IDs e totais; a ordem de `findAll()` nao e considerada divergencia porque esses endpoints nao possuem contrato publico de ordenacao.
- Funcoes adicionais podem aparecer como `additionalInParallelIds` no shadow read de uma listagem legada. Isso pode representar uma capacidade valida do novo modelo, nao necessariamente corrupcao.
- Divergencias entre a leitura legada e a paralela sao apenas registradas; nenhuma correcao automatica e executada.
- Falhas na leitura paralela nao derrubam as listagens legadas e nao usam a leitura paralela como fallback.
- O profile `local` usa Flyway, com schema criado por `V1`, roles obrigatorias por `V2` e dados demonstrativos carregados apenas por `db/local/R__load_local_demo_data.sql`.
- O profile `test` usa Flyway, com schema criado por `V1`, roles obrigatorias por `V2` e fixtures carregadas apenas por `db/test/R__load_test_fixtures.sql`.
- Os profiles `test` e `mysql` ativam `PARALLEL` para leitores, comentaristas, padres, ministros da Palavra e ministros da Eucaristia, com rollback independente por variavel de ambiente.
- Os seeds `local` e `test` criam os vinculos em `tb_person_ministry` depois de inserir as pessoas demonstrativas, porque `V4` executa antes das migrations repeatable de cada profile.
- Hibernate usa `ddl-auto=validate` nos profiles `local`, `test`, `mysql` e `flyway-test`.
- As roles deixaram de ser duplicadas nas fixtures do profile `test`.

Pre-condicoes para backfills:

- `V1` e `V2` validadas em H2 e MySQL.
- `V3` validada em H2 e MySQL.
- Profiles `local`, `test`, `mysql` e `flyway-test` usando Flyway com localizacoes isoladas.
- Hibernate usando `ddl-auto=validate` nos profiles migrados.
- Fixtures de teste separadas de dados locais.
- Confirmacao explicita de que os proximos backfills preservarao hashes, IDs e vinculos existentes.

Saidas esperadas das proximas fases:

- Backfill de contas a partir de `phone_number`, `password` e roles atuais.
- Backfill de atribuicoes de escala a partir de `tb_event_person` e `person_type` concluido por `V5`.
- Consultas de auditoria comparando contagens, IDs e vinculos antes/depois.
- Auditoria de equivalencia entre leituras legadas de escala e `tb_event_assignment` disponivel internamente, sem execucao automatica.
- Avaliar a migracao controlada de leituras oficiais para `tb_person_ministry`, mantendo contratos HTTP estaveis.

Fora do escopo da proxima fase:

- Remover estruturas legadas.
- Remover `person_type`, senha, roles ou vinculos atuais.
- Criar migration destrutiva.

## Separacao de dados por profile

O arquivo global `src/main/resources/import.sql` foi removido para evitar que o mesmo conjunto de dados seja carregado implicitamente por todos os ambientes.

Estado atual:

- `src/main/resources/db/local/R__load_local_demo_data.sql` contem os dados demonstrativos do profile `local`, sem roles, incluindo usuarios de demonstracao, pessoas, locais, eventos, vinculos legados e assignments derivados.
- `src/test/resources/db/test/R__load_test_fixtures.sql` contem as fixtures da suite principal de testes, sem roles, preservando os mesmos IDs implicitos utilizados pelos testes atuais e criando assignments derivados dos vinculos legados.
- `src/main/resources/db/migration/V2__insert_required_roles.sql` continua sendo a fonte dos dados obrigatorios para bancos novos gerenciados por Flyway.
- O profile `mysql` continua isolado, com `spring.sql.init.mode=never`, Hibernate `validate` e Flyway habilitado.
- O profile `flyway-test` continua validando apenas `V1` e `V2`, sem carregar pessoas, locais ou eventos demonstrativos.
- O profile `local` esta isolado por `spring.flyway.locations=classpath:db/migration,classpath:db/local`.
- O profile `test` esta isolado por `spring.flyway.locations=classpath:db/migration,classpath:db/test`.
- Os profiles `mysql` e `flyway-test` continuam usando apenas `classpath:db/migration`.

Proximas etapas planejadas:

1. Avaliar as limitacoes transitorias das listagens paralelas: pessoas com funcoes adicionais podem aparecer nas listagens, mas eventos e escalas ainda validam subtipos legados.
2. Planejar backfill versionado de `UserAccount`.
3. Observar a estabilidade operacional do shadow read de assignments habilitado no profile `local`.
4. Observar a estabilidade dos cutovers controlados de detalhe da escala, escala eucaristica e consulta mensal antes de remover dependencias legadas.

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

- Funcoes ministeriais derivadas de `person_type` ja cobertas por `V4`.
- Contas derivadas de `Person.password`, `phoneNumber` e `roles`.
- Atribuicoes derivadas de `tb_event_person` e `person_type` ja cobertas por `V5`.
- Contagens e amostras conferidas.

### Transicao funcional

Entrada:

- Backfill aprovado.

Saida:

- Escalas possuem leitura paralela interna por `EventAssignment`; o cutover oficial ainda depende de shadow read controlado.
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
- `PersonMinistry` esta em modo de compatibilidade: novas escritas dos CRUDs ministeriais mantem a tabela paralela, `V4` garante o vinculo das pessoas legadas, e todos os profiles (base, `local`, `test` e `mysql`) agora usam leitura ministerial `PARALLEL` por padrao, com rollback independente para `LEGACY` por variavel de ambiente em cada uma das cinco funcoes.
- A leitura paralela de `PersonMinistry` esta disponivel para validacao interna, testes, shadow read das cinco funcoes ministeriais e origem oficial configuravel de `GET /leitores`, `GET /comentaristas`, `GET /padres`, `GET /ministrosDaPalavra` e `GET /ministrosDeEucaristia`. Em todos os profiles, incluindo base, as cinco listagens ministeriais usam `PARALLEL` por padrao. Eventos e escalas ainda usam o modelo legado ate a migracao posterior das atribuicoes.
- A suite principal de testes exercita `PersonMinistry` como fonte oficial padrao das cinco listagens ministeriais em todos os profiles, inclusive base; testes que validam comportamento legado (cutovers `LEGACY`, consistencia, shadow read com fonte oficial legada, falha do repository legado, ausencia de fallback, backfill e write-through) declaram override `LEGACY` explicitamente por teste, sem depender do default global.
- O profile `mysql` foi revalidado com MySQL 8.4.10 em banco novo (Flyway `V1`-`V5` e Hibernate `validate` bem-sucedidos) com o novo default `PARALLEL`; as cinco listagens ministeriais usam `tb_person_ministry` por padrao, confirmado via consulta SQL (`JOIN` unico entre `tb_person_ministry` e `tb_person`, sem N+1 e sem escrita em transacao `READ ONLY`) e preservam rollback independente para `LEGACY` — verificado isolando `PERSON_MINISTRY_READ_SOURCE_READER=LEGACY` e confirmando, pela query executada, que apenas `GET /leitores` volta a usar `ReaderRepository.findAll()` enquanto as outras quatro listagens continuam usando `tb_person_ministry`.
- `person_type`, o discriminator de `Person`, as subclasses ministeriais legadas (`Reader`, `Commentator`, `Priest`, `MinisterOfTheWord`, `EucharisticMinister`) e os respectivos repositories legados permanecem preservados e sao a fonte oficial quando uma listagem estiver em rollback `LEGACY`.
- Nenhuma escrita, migration, seed, endpoint, DTO, subtipo legado, `person_type` ou estrutura legada foi removida nesta etapa.
- A proxima etapa planejada e avaliar tornar `PersonMinistry` a fonte oficial tambem das validacoes de ministerio em escritas de escala (hoje ainda baseadas em subtipo Java), alem de continuar observando a estabilidade operacional do default `PARALLEL` antes de planejar a remocao do shadow read e da leitura `LEGACY`.
- A camada Java de `EventAssignment` foi introduzida com enum `EventAssignmentType`, entidade, repository e servico interno de compatibilidade.
- A criacao de evento com escala e a atualizacao de escala mantem `tb_event_assignment` em paralelo com `tb_event_person`, usando os participantes ja aceitos pelas regras legadas de subtipo.
- O write-through de `EventAssignment` nao consulta `tb_person_ministry` e nao muda a validacao atual de `priestId`, `readerIds`, `commentatorIds`, `ministerOfTheWordIds` ou `eucharisticMinisterIds`.
- A sincronizacao preserva assignments mantidos, incluindo `id` e `created_at`; cria participantes adicionados; remove participantes removidos; e atualiza `assignment_type` quando um registro existente muda de funcao.
- A exclusao de evento remove assignments antes da exclusao fisica do evento, dentro da mesma transacao; falhas restauram assignments, evento e vinculos legados por rollback.
- `V5` executa o backfill auditavel de `tb_event_assignment` usando `tb_event_person` + `tb_person.person_type` como fonte oficial, sem consultar `tb_person_ministry`.
- O write-through de escalas e o backfill `V5` coexistem: eventos novos/editados sao mantidos em tempo real e eventos legados recebem assignments pela migration.
- Assignments existentes corretos sao preservados com o mesmo `id`, `created_at` e `updated_at`; assignments existentes com tipo divergente sao reconciliados com o legado, preservando `id` e `created_at`.
- Assignments extras sem vinculo correspondente em `tb_event_person` provocam falha da migration; eles nao sao excluidos silenciosamente.
- Os seeds `local` e `test` criam assignments derivados em bases novas depois de inserir `tb_event_person`, porque `V5` executa antes das migrations repeatable de cada profile.
- Eventos antigos editados pelo fluxo legado continuam recebendo o conjunto completo de assignments correspondente a escala salva.
- As leituras de detalhe de escala, consulta mensal, escala eucaristica, listagens publicas e mappers de resposta continuam usando `tb_event_person`, `CelebrationEvent.people` e subtipos legados.
- As estruturas legadas permanecem como fonte das leituras e dos contratos HTTP atuais.
- A leitura paralela interna de `EventAssignment` carrega assignments e pessoas por `JOIN FETCH`, inclusive em lote para multiplos eventos, sem N+1 e sem carregar colecoes desnecessarias.
- A auditoria interna compara `tb_event_assignment` com o legado carregado por `CelebrationEvent.people`, identifica assignments ausentes, extras, tipos divergentes, duplicidades, multiplos padres e subtipos legados desconhecidos.
- A auditoria de assignments e somente leitura: nao grava, nao corrige dados, nao executa automaticamente no startup, em controllers, em services de evento ou nos fluxos de criacao/edicao de escala.
- A auditoria operacional administrativa `GET /admin/event-assignments/consistency` executa a comparacao sob demanda entre `tb_event_person` + `person_type` e `tb_event_assignment` + `assignment_type`, e e protegida por `ROLE_ADMIN`.
- A auditoria operacional e somente leitura, nao realiza reparo automatico, nao altera cutovers, nao muda defaults de origem e nao remove nem substitui `tb_event_person`, `CelebrationEvent.people` ou os subtipos legados de `Person`.
- A leitura operacional carrega os eventos paginados no banco, os participantes legados em lote e os assignments paralelos em lote, sem consultar `tb_person_ministry`, sem consulta por evento e sem consulta por pessoa.
- A resposta da auditoria operacional retorna resumo agregado e, quando solicitado, detalhes sem nomes, telefones, documentos, senhas ou outros dados pessoais.
- O shadow read de `EventAssignment` e configuravel por fluxo com as flags `event-detail-enabled`, `event-scale-detail-enabled`, `monthly-schedule-enabled` e `eucharist-scale-enabled`; o profile `local` habilita as quatro flags para validacao operacional controlada, enquanto base, `test` e `mysql` permanecem desabilitados por padrao.
- Cada fluxo pode ser desligado independentemente por variavel de ambiente, preservando rollback operacional sem alteracao de codigo ou banco.
- Quando habilitado, o shadow read executa depois da consulta legada, preserva a resposta oficial legada e nao corrige divergencias automaticamente.
- Falhas tecnicas e inconsistencias da fonte paralela sao registradas e nao falham a requisicao oficial.
- Detalhe de evento e detalhe de escala usam comparacao completa quando os participantes legados estao disponiveis; consulta mensal compara parcialmente o tipo solicitado; escala eucaristica compara parcialmente `EUCHARISTIC_MINISTER`.
- Consultas com multiplos eventos usam leitura paralela em lote de `tb_event_assignment`, sem consulta por evento e sem consulta por pessoa.
- A listagem geral de eventos permanece sem shadow read de assignments porque `findAll()` nao carrega participantes suficientes para uma comparacao completa.
- `GET /eventos/{id}/escala` possui origem oficial configuravel por `app.event-assignment.read-source.event-scale-detail`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- Base e os profiles `local`, `test` e `mysql` usam `app.event-assignment.read-source.event-scale-detail=${EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL:PARALLEL}` para validar o detalhe da escala por `tb_event_assignment`.
- No modo `PARALLEL`, o detalhe da escala agrupa participantes exclusivamente por `assignment_type`, sem usar `person_type` ou `CelebrationEvent.people` para montar os grupos da resposta.
- O rollback operacional do detalhe da escala pode ser feito definindo `EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL=LEGACY`, sem alteracao de codigo ou banco.
- A leitura oficial `PARALLEL` do detalhe da escala nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- `GET /eventos/escala/eucaristia` possui origem oficial configuravel por `app.event-assignment.read-source.eucharist-scale`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- Base e os profiles `local`, `test` e `mysql` usam `app.event-assignment.read-source.eucharist-scale=${EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE:PARALLEL}` para validar a escala eucaristica por `tb_event_assignment`.
- No modo `PARALLEL`, a escala eucaristica seleciona os eventos pela existencia de assignment `EUCHARISTIC_MINISTER` em `tb_event_assignment`, preserva filtros de data, ordenacao e paginacao, e carrega os ministros dos eventos da pagina em lote.
- A escala eucaristica paralela agrupa exclusivamente por `assignment_type = EUCHARISTIC_MINISTER`, sem usar `person_type` nem `tb_event_person` para decidir os ministros da resposta.
- A paginacao paralela da escala eucaristica usa consulta paginada para eventos elegiveis e consulta em lote para assignments/pessoas da pagina, sem consulta por evento, sem consulta por pessoa e sem paginacao em memoria.
- O rollback operacional da escala eucaristica pode ser feito definindo `EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE=LEGACY`, sem alteracao de codigo ou banco e sem afetar o detalhe da escala.
- A leitura oficial `PARALLEL` da escala eucaristica nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- `GET /eventos/{id}` nao possui participantes no contrato HTTP atual; a resposta contem apenas `id`, `nameMassOrEvent`, `eventDate`, `eventTime` e `massOrCelebration`.
- Por nao haver dado contratual derivado de `tb_event_assignment`, nao existe cutover util do detalhe geral para assignments nesta etapa; o endpoint permanece com resposta oficial legada e apenas shadow read opcional por `app.event-assignment.shadow-read.event-detail-enabled`.
- Falhas ou divergencias detectadas pelo shadow read de `GET /eventos/{id}` continuam isoladas da resposta oficial e nao corrigem dados automaticamente.
- `GET /eventos/{id}/escala` permanece como o primeiro cutover real de leitura por `tb_event_assignment`, pois seu contrato expoe participantes da escala.
- Um cutover do detalhe geral so sera relevante se o contrato futuro passar a expor participantes ou outra informacao derivada de assignments.
- `GET /eventos/escalas` possui origem oficial configuravel por `app.event-assignment.read-source.monthly-schedule`, com valores `LEGACY` e `PARALLEL`; o default global agora e `PARALLEL`.
- Base e os profiles `local`, `test` e `mysql` usam `app.event-assignment.read-source.monthly-schedule=${EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE:PARALLEL}` para validar a consulta mensal por `tb_event_assignment`.
- No modo `PARALLEL`, a consulta mensal converte o `EventScheduleType` solicitado para `EventAssignmentType`, seleciona eventos pela existencia do `assignment_type` correspondente em `tb_event_assignment`, preserva filtros, ordenacao e paginacao, e carrega os participantes dos eventos da pagina em lote.
- A consulta mensal paralela classifica exclusivamente por `assignment_type`, sem usar `person_type` nem `tb_event_person` para montar a resposta; pessoas de subtipo legado diferente podem aparecer quando possuirem o assignment solicitado naquele evento.
- A paginacao paralela da consulta mensal usa consulta paginada para eventos elegiveis e consulta em lote para assignments/pessoas da pagina, sem consulta por evento, sem consulta por pessoa e sem paginacao em memoria.
- O rollback operacional da consulta mensal pode ser feito definindo `EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE=LEGACY`, sem alteracao de codigo ou banco e sem afetar detalhe da escala ou escala eucaristica.
- A leitura oficial `PARALLEL` da consulta mensal nao possui fallback silencioso para o legado; falhas devem aparecer como falhas normais da aplicacao.
- A validacao consolidada dos tres cutovers de escala (`GET /eventos/{id}/escala`, `GET /eventos/escala/eucaristia` e `GET /eventos/escalas`) confirma que base, `local`, `test` e `mysql` executam os tres em `PARALLEL` simultaneamente por padrao.
- A suite principal de testes agora exercita `tb_event_assignment` como fonte oficial padrao para os tres endpoints de escala; testes que validam comportamento legado declaram override `LEGACY` explicitamente.
- Os rollbacks dos tres cutovers sao independentes por variavel de ambiente e nao alteram as flags de shadow read.
- A validacao consolidada compara `LEGACY` e `PARALLEL` semanticamente, cobre dados de backfill e write-through, confirma ausencia de escrita em GET, ausencia de fallback silencioso e ausencia de uso de `tb_event_person` na montagem paralela das respostas; a resposta oficial paralela ja foi validada em H2 e MySQL 8.4.
- Endpoints, DTOs, mappers de resposta e listagem geral continuam com contratos HTTP inalterados; `GET /eventos/{id}` e listagem geral de eventos continuam usando o modelo legado.
- `tb_event_person` permanece preservada e nenhuma estrutura legada foi removida; a proxima etapa sera continuar observando os resultados da auditoria operacional e a estabilidade em producao antes de planejar qualquer remocao de estruturas legadas.

## Elegibilidade ministerial das escalas por PersonMinistry

- `PersonMinistry` (`tb_person_ministry`) passou a ser a fonte oficial de elegibilidade ministerial nas escritas de escala: criacao de evento com escala, atualizacao de escala, troca de padre e adicao/remocao de participantes. `person_type` e o subtipo Java legado deixaram de decidir se uma pessoa pode exercer a funcao solicitada.
- `PersonMinistryEligibilityResolver` (`service/PersonMinistryEligibilityResolver.java`) carrega pessoas e vinculos ministeriais ativos em lote (uma consulta para `tb_person`, uma para `tb_person_ministry`, independente da quantidade de funcoes ou participantes do request) e responde, por pessoa e por `MinistryType` solicitado, se a pessoa existe e se o vinculo esta ativo.
- Enquanto `tb_event_person` e a leitura `LEGACY` continuarem existindo, uma atribuicao so e aceita se a pessoa possuir o `PersonMinistry` solicitado *e* se a atribuicao tambem for representavel no subtipo Java legado; `ScaleLegacyCompatibilityValidator` (`service/ScaleLegacyCompatibilityValidator.java`) aplica essa restricao temporaria de compatibilidade e e verificado somente depois da elegibilidade ministerial ser confirmada.
- O subtipo legado (`Reader`, `Commentator`, `Priest`, `MinisterOfTheWord`, `EucharisticMinister`) e os repositories de subtipo deixaram de ser usados como fonte de elegibilidade da escrita de escala; permanecem em uso apenas pela camada temporaria de compatibilidade legada, pelos CRUDs ministeriais e pelas leituras/mapeamentos legados ja existentes.
- Uma pessoa com `PersonMinistry` ativo para a funcao solicitada, mas cujo subtipo legado nao corresponde, e rejeitada com `BusinessException` (422) indicando incompatibilidade temporaria com o modelo legado, sem expor o nome da classe Java nem `person_type` na mensagem HTTP. Uma pessoa sem o `PersonMinistry` ativo solicitado e rejeitada mesmo que o subtipo legado seja aparentemente compativel - essa e a prova de que `person_type` nao e mais a fonte de elegibilidade.
- Pessoa inexistente continua respondendo `ResourceNotFoundException` (404); IDs duplicados na mesma lista e a mesma pessoa ocupando duas funcoes no mesmo request continuam rejeitados por `BusinessException` (422), com a mesma semantica anterior.
- O dual write permanece ativo. Na etapa em que esta secao foi escrita, a escrita legada (`CelebrationEvent.people`/`tb_event_person`) ainda era a primeira escrita da transacao e `EventAssignmentTargetResolver`/`EventAssignmentCompatibilityService` sincronizavam `tb_event_assignment` depois, a partir das pessoas ja validadas; a secao seguinte ("Escrita oficial de escala por EventAssignment") descreve a inversao dessa ordem, feita em etapa posterior.
- Nenhum contrato HTTP, DTO, endpoint, status, migration, seed, subtipo legado, `person_type`, repository legado, rollback `LEGACY` ou shadow read foi alterado ou removido nesta etapa; apenas o gate de validacao interno da escrita de escala mudou de fonte.
- Validado em MySQL 8.4.10 (banco novo, Flyway `V1`-`V5`, Hibernate `validate`): criacao e atualizacao validas, ministerio ausente com subtipo correto, `PersonMinistry` ativo com subtipo divergente, duplicidade entre funcoes, troca de padre, ausencia de escrita parcial em falha de criacao e de atualizacao, write-through consistente em `tb_event_person`/`tb_event_assignment` sem duplicar registros, leitura paralela apos gravacao e leitura `LEGACY` do detalhe de escala por override permanecendo semanticamente consistente com o write-through.
- A proxima etapa planejada nesta secao ("preparar a escrita oficial de escala baseada em `EventAssignment` como fonte primaria") foi concluida - ver secao seguinte.

## Escrita oficial de escala por EventAssignment

- `EventAssignment` (`tb_event_assignment`) passou a ser a fonte oficial da escrita de escala: criacao de evento com escala, atualizacao de escala, troca de padre e adicao/remocao de participantes gravam primeiro em `tb_event_assignment` e so depois derivam o espelho legado `tb_event_person`/`CelebrationEvent.people`. Antes desta etapa, a ordem era invertida (legado primeiro, `tb_event_assignment` sincronizado depois).
- `EventScaleAssignmentPlan` (`service/EventScaleAssignmentPlan.java`) e o modelo canonico imutavel da escala: pares `Person`/`EventAssignmentType` construidos diretamente a partir do campo do request e da elegibilidade confirmada por `PersonMinistry`, sem consultar `person_type` nem subclasses de `Person`. Nao e entidade JPA e nao e exposto pelo contrato HTTP. O builder do plano rejeita, com `BusinessException`, a mesma pessoa aparecendo em mais de uma funcao no mesmo request - a mesma regra de negocio de antes, agora centralizada no plano em vez de duplicada na camada de servico.
- A escrita oficial em `tb_event_assignment` continua usando `EventAssignmentCompatibilityServiceImpl.synchronizeAssignments`, reaproveitado sem alteracao: carrega os assignments existentes do evento em uma unica consulta em lote, preserva `id` e `created_at` dos assignments que nao mudaram, atualiza apenas `assignment_type` quando muda, insere os novos e remove os obsoletos em lote - sem `person_type`, sem consulta por pessoa e sem apagar/recriar a escala inteira a cada gravacao.
- `LegacyScaleMirrorService`/`LegacyScaleMirrorServiceImpl` (`service/LegacyScaleMirrorService.java`, `service/impl/LegacyScaleMirrorServiceImpl.java`) e o novo componente que deriva `CelebrationEvent.people` (e, por consequencia, `tb_event_person`) exclusivamente do conjunto de pessoas do plano/estado oficial ja aplicado, sem reconstruir a lista a partir dos campos brutos do request e sem consultar repositories de subtipo.
- Ordem transacional efetiva: validar request -> carregar pessoas e `PersonMinistry` em lote -> validar elegibilidade -> validar compatibilidade legada -> criar o evento quando for criacao (necessario para obter o `id`) ou localizar o evento existente quando for atualizacao -> aplicar o plano oficial em `tb_event_assignment` -> sincronizar `tb_event_person` a partir do estado oficial -> mapear a resposta. Tudo na mesma transacao `@Transactional` do metodo de servico; nenhuma resposta e montada antes de ambas as estruturas estarem consistentes.
- `EventAssignmentTargetResolver`, que antes classificava `event.getPeople()` por `instanceof` para montar os alvos de sincronizacao, deixou de ser chamado pela escrita de escala (o `EventScaleAssignmentPlan` ja carrega o `EventAssignmentType` correto vindo da validacao). A classe e sua suite de testes foram mantidas sem alteracao por nao haver necessidade de remocao nesta etapa.
- `EventAssignmentTarget`, a auditoria de consistencia (`EventAssignmentConsistencyService`) e as tres leituras paralelas de escala nao foram alterados; a auditoria comparou o estado apos criacao e apos atualizacao e reportou `consistent() == true` e `totalIssues == 0` em H2.
- A restricao de compatibilidade legada desta etapa nao foi flexibilizada: pessoa com `PersonMinistry` valido mas subtipo legado incompativel continua rejeitada, e a mesma pessoa em funcoes diferentes no mesmo evento continua rejeitada - garantindo que o conjunto de pessoas gravado em `tb_event_person` continue sempre representavel no modelo legado.
- Atualizacoes sem mudanca real na escala (no-op) preservam `id`, `created_at` e `updated_at` dos assignments existentes; confirmado em H2 (MockMvc) e em MySQL 8.4 via inspecao direta de `tb_event_assignment` antes/depois de um PUT com os mesmos participantes.
- Falha na escrita oficial (`EventAssignmentCompatibilityService.synchronizeAssignments`) e falha no espelho legado (`LegacyScaleMirrorService.synchronizeMirror`) revertem a transacao inteira, incluindo o evento recem-criado quando aplicavel; validado com testes de integracao H2 que forcam cada uma das duas falhas isoladamente (`EventAssignmentWriteThroughRollbackIntegrationTest`, `EventAssignmentOfficialWriteIntegrationTest`).
- Validado em MySQL 8.4.10 (banco novo, Flyway `V1`-`V5`, Hibernate `validate`): a trace do log geral do MySQL confirmou que o `INSERT` em `tb_event_assignment` acontece antes do `INSERT` em `tb_event_person` na criacao de uma escala; criacao completa, atualizacao, no-op preservando `id`/timestamps, troca de padre, write-through consistente sem duplicar, as tres leituras paralelas e a leitura `LEGACY` por override permanecendo semanticamente consistente apos a inversao.
- A proxima etapa planejada e retirar as dependencias de leitura e shadow read que ainda usam o modelo legado (`GET /eventos/{id}/escala`, `/eventos/escala/eucaristia` e `/eventos/escalas` em modo `LEGACY`, e as flags de shadow read de `EventAssignment`) antes de considerar desativar o espelho `tb_event_person` como estrutura de escrita; `tb_event_person` continua preservada e nenhuma estrutura legada foi removida nesta etapa.

## Remocao do shadow read automatico de EventAssignment

- O shadow read automatico de `EventAssignment` foi removido: `EventAssignmentShadowReadExecutor` (`service/EventAssignmentShadowReadExecutor.java`) e `EventAssignmentShadowReadProperties` (`config/EventAssignmentShadowReadProperties.java`) foram excluidos, junto das quatro flags `app.event-assignment.shadow-read.*` (`event-detail-enabled`, `event-scale-detail-enabled`, `monthly-schedule-enabled`, `eucharist-scale-enabled`) em `application.properties` e `application-local.properties`.
- `CelebrationEventServiceImpl` deixou de comparar automaticamente contra o legado em `GET /eventos/{id}` e, no modo `LEGACY`, em `GET /eventos/{id}/escala`, `GET /eventos/escala/eucaristia` e `GET /eventos/escalas`; nenhuma chamada assincrona ou sincrona de shadow permanece nesses fluxos.
- A leitura e a escrita oficiais continuam paralelas: os tres endpoints de escala seguem com origem oficial `PARALLEL` por padrao em todos os profiles, sem alteracao de contrato HTTP, DTO, status ou paginacao, e sem fallback silencioso para o legado em caso de falha da leitura paralela.
- A auditoria administrativa `GET /admin/event-assignments/consistency` continua sendo a forma oficial de comparacao entre legado e paralelo sob demanda; `EventAssignmentConsistencyService`, `EventAssignmentOperationalAuditService`/`EventAssignmentOperationalAuditServiceImpl`, as projecoes legadas em lote e a leitura paralela em lote nao foram alterados nem tiveram capacidade reduzida.
- O rollback `LEGACY` continua disponivel de forma independente para os tres endpoints via `EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL`, `EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE` e `EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE`; o espelho `tb_event_person` continua ativo e as queries legadas de cutover nao foram removidas.
- A remocao reduziu consultas e acoplamento ao legado: o detalhe de escala em `PARALLEL` passou a executar 2 consultas funcionais (sem a consulta adicional de comparacao a `tb_event_person` que o shadow read executava antes), e a escala eucaristica e a consulta mensal deixaram de executar a consulta de comparacao parcial que rodava apos a resposta legada.
- Validado em MySQL 8.4.10 (banco novo, Flyway `V1`-`V5`, Hibernate `validate`): os tres endpoints de escala responderam em `PARALLEL` sem nenhuma consulta a `tb_event_person`; a auditoria administrativa (`ROLE_ADMIN`) comparou legado e paralelo sem divergencias; o rollback `LEGACY` do detalhe de escala voltou a usar `tb_event_person` via `findByIdWithPeople` sem nenhuma consulta adicional de shadow, e os outros dois endpoints permaneceram em `PARALLEL` de forma independente.
- Testes exclusivos do shadow read automatico foram removidos (`EventAssignmentShadowReadExecutorTest`, `EventAssignmentShadowReadPropertiesTest`, `EventAssignmentShadowReadHttpIntegrationTest`, `EventAssignmentShadowReadFailureHttpIntegrationTest`); os testes de cutover `LEGACY`/`PARALLEL` e de auditoria administrativa que ja cobriam vinculo ausente, assignment extra, tipo divergente e comparacao em lote foram preservados sem reducao de cobertura.
- A proxima etapa planejada e remover o shadow read de `PersonMinistry` ou iniciar a aposentadoria das leituras `LEGACY` de `EventAssignment`, conforme as dependencias remanescentes documentadas na auditoria de dependencias legadas.

## Remocao do shadow read automatico de PersonMinistry

- O shadow read automatico das cinco listagens ministeriais foi removido: `PersonMinistryShadowReadExecutor` (`service/PersonMinistryShadowReadExecutor.java`), `PersonMinistryShadowReadComparator`, `PersonMinistryShadowReadReport`, `PersonMinistryShadowReadIssueType`, `PersonMinistryShadowReadComparisonOptions` (todos em `service/`) e `PersonMinistryShadowReadProperties` (`config/PersonMinistryShadowReadProperties.java`) foram excluidos, junto das cinco flags `app.person-ministry.shadow-read.*-enabled` (`reader`, `commentator`, `priest`, `minister-of-the-word`, `eucharistic-minister`) em `application.properties`; nenhum outro profile sobrescrevia essas flags.
- `ReaderServiceImpl`, `CommentatorServiceImpl`, `PriestServiceImpl`, `MinisterOfTheWordServiceImpl` e `EucharisticMinisterServiceImpl` deixaram de comparar automaticamente o resultado legado contra `tb_person_ministry` no branch `LEGACY` de cada `findAllX()`; nenhuma chamada assincrona ou sincrona de shadow permanece nesses fluxos.
- A leitura oficial continua paralela: as cinco listagens seguem com origem oficial `PARALLEL` por padrao em todos os profiles (base, `local`, `test`, `mysql`), sem alteracao de contrato HTTP, DTO, status, ordenacao ou lista vazia, e sem fallback silencioso para o legado em caso de falha da leitura paralela.
- `PersonMinistryConsistencyService`/`PersonMinistryConsistencyServiceImpl` (auditoria interna, sem endpoint HTTP, usada por testes e validacao interna) nao foi alterado nem teve capacidade reduzida; nao e o mesmo componente do shadow read automatico e continua disponivel para comparacoes internas sob demanda.
- Os cinco rollbacks `LEGACY` continuam disponiveis de forma independente via `PERSON_MINISTRY_READ_SOURCE_READER`, `PERSON_MINISTRY_READ_SOURCE_COMMENTATOR`, `PERSON_MINISTRY_READ_SOURCE_PRIEST`, `PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD` e `PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER`; os repositories legados de subtipo, o write-through e o backfill `V4` permanecem ativos e inalterados.
- `PersonMinistry` continua sendo a fonte oficial da elegibilidade ministerial nas escritas de escala (`PersonMinistryEligibilityResolver`, `ScaleLegacyCompatibilityValidator`); esta etapa nao tocou nenhum componente de elegibilidade nem de escrita de `EventAssignment`.
- A remocao reduziu consultas e acoplamento ao legado: cada uma das cinco listagens em `PARALLEL` passou a executar exatamente 1 consulta funcional (`JOIN` entre `tb_person_ministry` e `tb_person`), sem a segunda consulta ao repository legado de subtipo que o shadow read executava antes para comparacao; confirmado via `general_log` do MySQL 8.4.10 real, sem nenhuma escrita durante os GETs.
- Validado em MySQL 8.4.10 (container `mysql:8.4`, banco novo, dados de teste inseridos manualmente porque o profile `mysql` nao carrega os seeds de `local`/`test`): Flyway `V1`-`V5` aplicadas, Hibernate `ddl-auto=validate` sem erro, as cinco listagens responderam em `PARALLEL` com exatamente 1 query cada; uma pessoa com `READER` e `COMMENTATOR` ativos apareceu corretamente nas duas listagens (multiplos ministerios), e uma pessoa de subtipo legado `commentator` com `PersonMinistry.PRIEST` ativo apareceu em `GET /padres` (subtipo divergente), confirmando que a fonte paralela usa exclusivamente `tb_person_ministry`.
- O rollback `LEGACY` de leitores foi validado isoladamente em MySQL real: com `PERSON_MINISTRY_READ_SOURCE_READER=LEGACY`, `GET /leitores` passou a executar `select ... from tb_person where person_type='reader'` (uma unica query, sem comparacao), excluindo a pessoa de subtipo divergente, enquanto `GET /comentaristas` permaneceu em `PARALLEL` de forma independente (mesma query com `JOIN` em `tb_person_ministry`), sem alteracao de codigo ou banco.
- A auditoria administrativa `GET /admin/event-assignments/consistency` e a leitura/escrita oficiais de `EventAssignment` foram verificadas sem regressao nesta mesma validacao MySQL (200 para `ROLE_ADMIN`, 403 para `ROLE_OPERATOR`, 401 sem token; `GET /eventos/escalas` respondendo normalmente), confirmando que a remocao do shadow read de `PersonMinistry` nao afetou o dominio de `EventAssignment`.
- Testes exclusivos do shadow read automatico foram removidos: `ReaderShadowReadIntegrationTest`, `PersonMinistryShadowReadExpansionIntegrationTest`, `PersonMinistryShadowReadExecutorTest`, `PersonMinistryShadowReadComparatorTest` (4 classes), alem de metodos de teste exclusivos de shadow em `ReaderServiceImplTest`, `CommentatorServiceImplTest`, `PriestServiceImplTest`, `MinisterOfTheWordServiceImplTest`, `EucharisticMinisterServiceImplTest` e `PersonMinistryReadSourcePropertiesTest`. As cinco leituras oficiais, os cinco rollbacks `LEGACY`, o write-through, o backfill, a elegibilidade de escala e a escrita oficial de `EventAssignment` continuam cobertos pelos testes preservados, sem reducao de cobertura funcional.
- A proxima etapa planejada e iniciar a aposentadoria das leituras `LEGACY` de `PersonMinistry` e de `EventAssignment`, conforme as dependencias remanescentes documentadas na auditoria de dependencias legadas.

## Remocao do suporte de leitura LEGACY das cinco listagens ministeriais

- O cutover configuravel `LEGACY | PARALLEL` das cinco listagens ministeriais foi removido: `PersonMinistry` (`tb_person_ministry`) passou a ser a unica fonte de `GET /leitores`, `GET /comentaristas`, `GET /padres`, `GET /ministrosDaPalavra` e `GET /ministrosDeEucaristia`, sem branch configuravel no codigo.
- `PersonMinistryReadSourceProperties` (`config/PersonMinistryReadSourceProperties.java`) e `PersonMinistryReadSource` (`config/PersonMinistryReadSource.java`, enum `LEGACY`/`PARALLEL`) foram excluidos, junto das cinco propriedades `app.person-ministry.read-source.*` (`reader`, `commentator`, `priest`, `minister-of-the-word`, `eucharistic-minister`) em `application.properties`, `application-local.properties`, `application-test.properties` e `application-mysql.properties`, e das respectivas variaveis de ambiente `PERSON_MINISTRY_READ_SOURCE_READER`, `PERSON_MINISTRY_READ_SOURCE_COMMENTATOR`, `PERSON_MINISTRY_READ_SOURCE_PRIEST`, `PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD` e `PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER`, que deixaram de ter qualquer efeito.
- `ReaderServiceImpl`, `CommentatorServiceImpl`, `PriestServiceImpl`, `MinisterOfTheWordServiceImpl` e `EucharisticMinisterServiceImpl` tiveram o branch `LEGACY` removido de cada `findAllX()`; o metodo passou a chamar diretamente `PersonMinistryReadService.findAllActivePeopleByMinistry(MinistryType)` e mapear a resposta, sem decisao de fonte, sem log de selecao de fonte e sem consultar `ReaderRepository.findAll()`/`CommentatorRepository.findAll()`/`PriestRepository.findAll()`/`MinisterOfTheWordRepository.findAll()`/`EucharisticMinisterRepository.findAll()` para listagem. O metodo `toDtoList(List<Subtipo>)`, exclusivo dessa montagem legada, foi removido dos cinco mappers (`ReaderMapper`, `CommentatorMapper`, `PriestMapper`, `MinisterOfTheWordMapper`, `EucharisticMinisterMapper`) por ficar sem consumidor de producao; `toDtoPersonList`, usado pela listagem oficial, foi preservado.
- Os repositories de subtipo (`ReaderRepository`, `CommentatorRepository`, `PriestRepository`, `MinisterOfTheWordRepository`, `EucharisticMinisterRepository`) nao foram removidos nem tiveram metodos excluidos: continuam sendo a base dos CRUDs ministeriais (`save`, `findById`, `getReferenceById`, `existsById`, `deleteById`, `flush`), que nao mudaram nesta etapa.
- Contratos HTTP das cinco listagens permanecem inalterados: mesmos DTOs, `200 OK`, formato de lista simples sem paginacao, mesma ordenacao (`name ASC, id ASC`) e mesmo comportamento de lista vazia; confirmado por testes de integracao e por chamadas HTTP reais em MySQL.
- Uma falha na leitura oficial (`PersonMinistryReadService`) continua propagando a excecao normalmente, sem consultar nenhum repository de subtipo como alternativa; nao existe mais nenhum caminho de codigo capaz de usar o legado como fallback, porque o branch `LEGACY` deixou de existir.
- A remocao reduziu ainda mais o acoplamento ao legado nas listagens: cada uma das cinco continua executando exatamente 1 consulta funcional (`JOIN` entre `tb_person_ministry` e `tb_person`), agora sem nenhuma configuracao capaz de desviar para o repository de subtipo; confirmado via `general_log` do MySQL 8.4.10 real, sem escrita durante os GETs.
- Backfill (`V4`) e write-through (`PersonMinistryCompatibilityService.ensureMinistry`/`deleteAllForPerson`, chamados pelos CRUDs legados) nao foram alterados; validados nesta etapa por criacao via `POST /leitores` em MySQL real, confirmando gravacao simultanea em `tb_person` e `tb_person_ministry` e visibilidade imediata na leitura oficial.
- `PersonMinistry` continua sendo a fonte oficial da elegibilidade ministerial nas escritas de escala (`PersonMinistryEligibilityResolver`, `ScaleLegacyCompatibilityValidator`); esta etapa nao tocou nenhum componente de elegibilidade nem de escrita/leitura de `EventAssignment`, confirmado sem regressao em MySQL real (`GET /eventos/escalas`, `GET /admin/event-assignments/consistency` com 200/403/401 conforme a role).
- Validado em MySQL 8.4.10 (container `mysql:8.4`, banco novo, dados de teste inseridos manualmente porque o profile `mysql` nao carrega os seeds de `local`/`test`): Flyway `V1`-`V5` aplicadas, Hibernate `ddl-auto=validate` sem erro, as cinco listagens responderam exclusivamente via `tb_person_ministry`; uma pessoa de subtipo legado `commentator` com `PersonMinistry.READER` ativo apareceu em `GET /leitores` (subtipo divergente) e em `GET /comentaristas` (multiplos ministerios); `GET /padres`, `GET /ministrosDaPalavra` e `GET /ministrosDeEucaristia` retornaram lista vazia corretamente.
- Testes removidos por serem exclusivos do cutover `LEGACY`/rollback: as 5 classes `*LegacyCutoverConsistencyIntegrationTest`, as 5 classes `*MinistryReadCutoverLegacyIntegrationTest` e a classe `PersonMinistryReadSourcePropertiesTest` (binding e rollback das cinco propriedades por profile) foram excluidas por completo. Testes de metodo exclusivos de cutover/legado tambem foram removidos dos cinco `*ServiceImplTest`. As classes `*MinistryReadCutoverParallelIntegrationTest`, `*MinistryReadCutoverParallelFailureIntegrationTest`, `*ParallelCutoverConsistencyIntegrationTest`, `PriestEventLegacyCompatibilityIntegrationTest`, `*ScaleLegacyCompatibilityIntegrationTest` e `ReaderParallelCutoverIsolatedLifecycleIntegrationTest` foram preservadas, apenas com a remocao da property override `app.person-ministry.read-source.*=PARALLEL` que se tornou redundante (a unica fonte agora e sempre `PersonMinistry`); a cobertura de leituras oficiais, falha sem fallback, multiplos ministerios, subtipo divergente e compatibilidade legada de escala continua integralmente preservada.
- A proxima etapa planejada e remover as leituras `LEGACY` de `EventAssignment` (os tres endpoints de escala), conforme as dependencias remanescentes documentadas na auditoria de dependencias legadas.

## Remocao do suporte de leitura LEGACY das tres consultas de escala

- O cutover configuravel `LEGACY | PARALLEL` das tres consultas de escala foi removido: `EventAssignment` (`tb_event_assignment`) passou a ser a unica fonte de `GET /eventos/{id}/escala`, `GET /eventos/escala/eucaristia` e `GET /eventos/escalas`, sem branch configuravel no codigo. `GET /eventos/{id}` nao foi alterado: ja usava apenas `celebrationEventRepository.findById(id)`, sem participantes e sem read source.
- `EventAssignmentReadSourceProperties` (`config/EventAssignmentReadSourceProperties.java`) e `EventAssignmentReadSource` (`config/EventAssignmentReadSource.java`, enum `LEGACY`/`PARALLEL`) foram excluidos, junto das tres propriedades `app.event-assignment.read-source.*` (`event-scale-detail`, `eucharist-scale`, `monthly-schedule`) em `application.properties`, `application-local.properties`, `application-test.properties` e `application-mysql.properties`, e das variaveis `EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL`, `EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE` e `EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE`, que deixaram de ter qualquer efeito.
- `CelebrationEventServiceImpl.findEucharistScale`, `findEventSchedules` e `findScaleByEventId` tiveram o branch `LEGACY` removido; cada metodo passou a executar diretamente o que antes era o caminho `PARALLEL`, sem decisao de fonte e sem log de selecao de fonte. Os metodos privados `findEucharistScaleLegacy`, `findEventSchedulesLegacy`, `findScaleByEventIdLegacy`, `toLegacyEucharistScaleResponse`, `findAssignmentsByEvent` e `peopleByType` ficaram sem consumidor e foram removidos.
- Na camada de dados, as queries nativas exclusivas do caminho legado `CelebrationEventRepository.findEucharistScale`, `findEventScheduleEvents` e `findEventScheduleAssignments` (todas baseadas em `tb_event_person`/`person_type`) foram removidas por nao terem mais nenhum consumidor de producao. `findByIdWithPeople` (`LEFT JOIN FETCH ce.people`) foi preservada: alem do endpoint legado removido, ela e usada por testes de escrita oficial e de leitura paralela em banco migrado (`EventAssignmentOfficialWriteIntegrationTest`, `EventAssignmentParallelReadMigratedDatabaseIntegrationTest`) para carregar o espelho `tb_event_person` e compara-lo com `tb_event_assignment` via `EventAssignmentConsistencyService.compareEvent`, que tambem e reaproveitado internamente pela comparacao em lote da auditoria administrativa. Na camada de apresentacao, o overload de `CelebrationEventScaleDetailMapper.toDto` que agrupava pessoas por subtipo legado (7 argumentos) e os helpers `toPersonDtos(List<? extends Person>)`/`toPersonDto(Person)`, exclusivos desse caminho, tambem foram removidos.
- Nenhuma query de auditoria administrativa foi tocada: `findEventIdsForAssignmentAudit`, `findEventIdForAssignmentAudit`, `findLegacyEventAssignmentsForAudit`/`findLegacyEventAssignmentsForAuditInternal` continuam identicas, e `GET /admin/event-assignments/consistency` continua comparando `tb_event_person` e `tb_event_assignment` sob demanda, sem nenhuma reducao de capacidade.
- Contratos HTTP dos tres endpoints permanecem inalterados: mesmos DTOs, status, filtros, paginacao e ordenacao; confirmado por testes de integracao e por chamadas HTTP reais em MySQL. Uma falha na leitura oficial continua propagando a excecao normalmente, sem nenhum caminho de codigo capaz de usar `tb_event_person` como alternativa, porque o branch `LEGACY` deixou de existir.
- Escrita oficial em `tb_event_assignment`, espelho `tb_event_person`, atomicidade (rollback conjunto em falha) e a restricao temporaria de compatibilidade legada (`ScaleLegacyCompatibilityValidator`) nao foram alterados nesta etapa; a inversao de escrita e a validacao de elegibilidade por `PersonMinistry` continuam como nas etapas anteriores.
- `EventAssignmentTargetResolver` foi reavaliado e permanece sem uso pela escrita de escala (papel assumido por `EventScaleAssignmentPlan` em etapa anterior a esta serie); nao ha relacao direta entre essa classe e a remocao das leituras legadas, entao ela foi mantida sem alteracao e continua documentada como candidata futura de remocao, condicionada a prova de ausencia total de dependencia.
- Validado em MySQL 8.4.10 (container `mysql:8.4`, banco novo, dados de teste inseridos manualmente porque o profile `mysql` nao carrega os seeds de `local`/`test`): Flyway `V1`-`V5` aplicadas, Hibernate `ddl-auto=validate` sem erro; os tres endpoints responderam exclusivamente via `tb_event_assignment`, confirmado por `general_log` sem nenhuma referencia a `tb_event_person` — detalhe da escala com 2 consultas funcionais, escala eucaristica e consulta mensal com 2 consultas cada (sem consulta de contagem porque o total ficou abaixo do tamanho de pagina); nenhuma escrita ocorreu durante os tres GETs. A escrita oficial via `POST /eventos/com-escala` gravou primeiro em `tb_event_assignment` e depois no espelho `tb_event_person`, com os mesmos participantes em ambas as tabelas. A auditoria administrativa respondeu `200` para `ROLE_ADMIN` e `401` sem token, comparando os dois eventos inseridos sem divergencias (`totalIssues: 0`).
- Testes removidos por serem exclusivos do cutover `LEGACY`/rollback: as classes `EventScaleDetailReadCutoverLegacyIntegrationTest`, `EucharistScaleReadCutoverLegacyIntegrationTest`, `MonthlyScheduleReadCutoverLegacyIntegrationTest` e `EventAssignmentReadSourcePropertiesTest` (binding e rollback das tres propriedades por profile) foram excluidas por completo. Metodos exclusivos do branch `LEGACY`, redundantes com o equivalente `PARALLEL` ja existente, tambem foram removidos de `CelebrationEventServiceImplTest`, `CelebrationEventRepositoryTest`, `EventAssignmentParallelCutoverConsistencyIntegrationTest` (o teste que comparava resposta `LEGACY` contra `PARALLEL` e o teste de rollback independente entre os tres cutovers) e `EventAssignmentOfficialWriteIntegrationTest` (o teste que validava leitura `LEGACY` apos escrita oficial). As classes `*ReadCutoverParallelIntegrationTest` e `*ReadCutoverParallelFailureIntegrationTest` foram preservadas, apenas com a remocao da property override que se tornou redundante; a cobertura de leitura oficial, falha sem fallback, multiplos participantes, subtipo divergente, ausencia de escrita e contrato continua integralmente preservada. Suite: 586 testes antes desta etapa, 521 depois, 0 falhas e 0 erros.
- A proxima etapa planejada e remover as leituras `LEGACY` das cinco listagens ministeriais de `PersonMinistry` ja foi concluida em etapa anterior desta serie; o proximo passo remanescente e avaliar o encerramento da restricao de compatibilidade legada (`ScaleLegacyCompatibilityValidator`) e preparar a desativacao do espelho `tb_event_person`, conforme as dependencias documentadas na auditoria de dependencias legadas.

## Remocao da auditoria operacional temporaria de EventAssignment

- O endpoint administrativo temporario `GET /admin/event-assignments/consistency` foi removido, junto de toda a infraestrutura exclusiva a ele: `EventAssignmentOperationalAuditController`, `EventAssignmentOperationalAuditService`/`EventAssignmentOperationalAuditServiceImpl`, os DTOs `EventAssignmentAuditResponseDTO`, `EventAssignmentAuditSummaryDTO`, `EventAssignmentAuditEventDTO` e `EventAssignmentAuditIssueDTO`, a projecao `LegacyEventAssignmentProjection` e as queries de repository exclusivas `CelebrationEventRepository.findEventIdsForAssignmentAudit`, `findEventIdForAssignmentAudit`, `findLegacyEventAssignmentsForAudit`/`findLegacyEventAssignmentsForAuditInternal`. Nenhum desses componentes tinha consumidor de producao ou de teste fora da propria auditoria administrativa.
- A regra de seguranca exclusiva `.requestMatchers(HttpMethod.GET, "/admin/event-assignments/consistency").hasAuthority("ROLE_ADMIN")` foi removida de `ResourceServerConfig`; a rota passou a cair em `anyRequest().authenticated()` e, por nao existir mais nenhum controller mapeado para ela, uma requisicao autenticada (`ROLE_ADMIN` ou `ROLE_OPERATOR`) retorna `404` do proprio `DispatcherServlet`, enquanto uma requisicao sem token continua retornando `401`; esse comportamento foi confirmado por teste de integracao (`EndpointSecurityTest`) e por chamadas HTTP reais em MySQL 8.4, sem impor `404` por suposicao.
- `EventAssignmentConsistencyService`/`EventAssignmentConsistencyServiceImpl`, `EventAssignmentConsistencyReport`, `EventAssignmentConsistencyIssue`, `EventAssignmentConsistencyIssueType` e `LegacyEventAssignmentSnapshotResolver` **nao** foram removidos: continuam sendo usados diretamente (bean real, sem mock) por `EventAssignmentOfficialWriteIntegrationTest` e `EventAssignmentParallelReadMigratedDatabaseIntegrationTest` para comparar o espelho `tb_event_person` com `tb_event_assignment` apos escritas oficiais e em banco migrado. `CelebrationEventRepository.findByIdWithPeople` tambem foi preservada pelo mesmo motivo, junto de `CelebrationEventRepositoryTest`.
- `EventAssignmentTargetResolver`, documentado desde a etapa anterior como candidato futuro de remocao condicionado a prova de ausencia total de dependencia, foi reavaliado nesta etapa: confirmada a ausencia de qualquer consumidor de producao (o papel de montar os alvos da escrita de escala ja havia sido assumido por `EventScaleAssignmentPlan` em etapa anterior), a classe e sua suite de testes dedicada (`EventAssignmentTargetResolverTest`) foram removidas.
- `LegacyScaleMirrorService`/`LegacyScaleMirrorServiceImpl`, a escrita em `tb_event_person` como espelho temporario, `ScaleLegacyCompatibilityValidator`, `PersonMinistryEligibilityResolver` e a ordem transacional (`EventAssignment` oficial antes do espelho legado) nao foram alterados nesta etapa.
- Validado em MySQL 8.4.10 (container `mysql:8.4`, banco novo, dados de teste inseridos manualmente porque o profile `mysql` nao carrega os seeds de `local`/`test`): Flyway `V1`-`V5` aplicadas, Hibernate `ddl-auto=validate` sem erro, aplicacao subiu sem beans orfaos. A rota da auditoria retornou `401` sem token e `404` tanto para `ROLE_ADMIN` quanto para `ROLE_OPERATOR` autenticados. `POST /eventos/com-escala` gravou primeiro em `tb_event_assignment` e depois em `tb_event_person` (confirmado pela ordem no `general_log`); um `PUT` subsequente sem mudanca real (no-op) preservou `id`/`created_at`/`updated_at` dos assignments; uma tentativa de atribuir padre com `PersonMinistry` valido mas subtipo legado incompativel foi rejeitada com `422` pelo `ScaleLegacyCompatibilityValidator`, sem alterar o estado gravado (confirmando atomicidade). As tres consultas de escala (`GET /eventos/{id}/escala`, `GET /eventos/escala/eucaristia`, `GET /eventos/escalas`) responderam sem nenhuma referencia a `tb_event_person` no `general_log` (2 consultas cada), e `GET /leitores` continuou respondendo com 1 consulta via `tb_person_ministry`.
- Testes removidos por serem exclusivos da auditoria administrativa: `EventAssignmentOperationalAuditControllerTest`, `EventAssignmentOperationalAuditHttpIntegrationTest`, `EventAssignmentOperationalAuditServiceImplTest`, `EventAssignmentOperationalAuditRepositoryTest` e `EventAssignmentTargetResolverTest` (5 classes). `EndpointSecurityTest` teve as 3 expectativas exclusivas da rota de auditoria removidas e 2 novas adicionadas para documentar o comportamento real da rota aposentada (`401` sem token, `404` autenticado). Nenhum teste de escrita oficial, espelho legado, atomicidade, elegibilidade por `PersonMinistry`, compatibilidade legada, backfill, migration ou dos cinco CRUDs ministeriais foi removido ou teve cobertura reduzida. Suite: 521 testes antes desta etapa, 478 depois, 0 falhas e 0 erros.
- Impacto no frontend: a tela administrativa que consome `GET /admin/event-assignments/consistency` ficou sem endpoint correspondente e passara a receber `404` para qualquer usuario autenticado; ela devera ser removida ou substituida quando o desenvolvimento frontend for retomado. Nenhum outro contrato frontend (eventos, escalas, listagens ministeriais, usuarios) foi alterado nesta etapa.
- `tb_event_person` continua sendo escrita como espelho temporario e nenhuma estrutura legada foi removida; a proxima etapa planejada e avaliar o encerramento da restricao de compatibilidade legada (`ScaleLegacyCompatibilityValidator`) e preparar a desativacao do espelho `tb_event_person`, conforme as dependencias documentadas na auditoria de dependencias legadas.

## Desativacao do espelho legado de escala e liberacao de multiplas funcoes por pessoa (2026-07-25)

- Migration `V6__allow_multiple_event_assignments_per_person` (Java, `db.migration`, detecta o banco via `Connection.getMetaData().getDatabaseProductName()` porque a sintaxe de remocao da constraint diverge entre H2 `DROP CONSTRAINT` e MySQL `DROP INDEX`) substitui a unicidade de `tb_event_assignment` de `UNIQUE(event_id, person_id)` (`uk_tb_event_assignment_event_person`, criada em `V3`) por `UNIQUE(event_id, person_id, assignment_type)` (`uk_tb_event_assignment_event_person_type`). A identidade logica de um assignment passa a ser `eventId + personId + assignmentType`; a regra de no maximo um `PRIEST` por evento continua garantida pela aplicacao (o DTO de escala so aceita um `priestId`), nao pela constraint. Validada em banco atualizado (V1-V5 seguido de V6, preservando dados existentes) e em banco novo (V1-V6), em H2 e MySQL 8.4.10.
- A escrita oficial de escala parou de popular `tb_event_person`: `CelebrationEventServiceImpl.createEventWithScale` nao grava mais nenhum vinculo legado para eventos novos, e `updateEventScale` executa `celebrationEvent.getPeople().clear()` (sem recriar) na mesma transacao da sincronizacao oficial, removendo em lote apenas os vinculos legados daquele evento. `LegacyScaleMirrorService`/`LegacyScaleMirrorServiceImpl` ficaram sem consumidor real e foram removidos, junto de `LegacyScaleMirrorServiceImplTest`. Eventos legados nunca tocados apos esta branch podem manter linhas historicas em `tb_event_person` ate a migration destrutiva final; `tb_event_person` nao foi removida fisicamente.
- `ScaleLegacyCompatibilityValidator` foi removido (classe, chamada em `CelebrationEventServiceImpl.addOptionalPerson` e `ScaleLegacyCompatibilityValidatorTest`): a elegibilidade de uma pessoa para uma funcao de escala passa a depender exclusivamente de `PersonMinistry` via `PersonMinistryEligibilityResolver` (ja carregava pessoas e ministerios em lote, sem alteracao necessaria), sem nenhuma verificacao do subtipo Java legado ou de `person_type`.
- Uma pessoa pode agora ocupar mais de uma funcao no mesmo evento quando possuir todos os `PersonMinistry` correspondentes (ex.: `PersonMinistry` `READER` e `COMMENTATOR` na mesma pessoa gera dois assignments distintos); apenas o mesmo par `personId + assignmentType` duplicado continua sendo rejeitado. Quatro pontos que ainda tratavam "mesma pessoa" como identidade unica (independente do tipo) foram corrigidos para usar o par `personId + assignmentType` como chave: `EventScaleAssignmentPlan.Builder`, `EventAssignmentCompatibilityServiceImpl.validateTargets` e `.synchronizeAssignments` (o diff de escrita passou a inserir/remover/preservar por par, em vez de mutar o tipo de um assignment existente in place — trocar a funcao de uma pessoa agora remove o par antigo e cria o novo, preservando o `id` apenas quando o par nao muda), e `EventAssignmentGroup.validateSnapshot` (lado de leitura de `GET /eventos/{id}/escala`, que sem essa correcao rejeitaria com excecao um evento legitimamente multifuncao).
- `CelebrationEventScaleMapper` (resposta de `POST /eventos/com-escala` e `PUT /eventos/{id}/escala`) foi corrigido para montar `priest`/`readers`/`commentators`/`ministersOfTheWord`/`eucharisticMinisters` a partir de `EventScaleAssignmentPlan.entries()` agrupados por `EventAssignmentType`, em vez de `celebrationEvent.getPeople()` agrupado por subtipo Java legado (`Priest.class`, `Reader.class`, etc.); a implementacao antiga dependia inteiramente do espelho legado e, alem de ficar vazia assim que a escrita parou de popular `tb_event_person`, jamais teria conseguido representar uma pessoa em duas funcoes.
- `EventAssignmentConsistencyService`/`EventAssignmentConsistencyReport`/`EventAssignmentConsistencyIssue`/`EventAssignmentConsistencyIssueType`/`LegacyEventAssignmentSnapshotResolver` e `CelebrationEventRepository.findByIdWithPeople` nao foram removidos: continuam usados por `EventAssignmentParallelReadMigratedDatabaseIntegrationTest` (dados de fixture ja migrados, nunca escritos pelo novo fluxo) sem alteracao. `EventAssignmentOfficialWriteIntegrationTest` foi adaptado: os testes que comparavam o espelho contra o paralelo apos escrita oficial foram substituidos por asserts diretos de que `tb_event_person` permanece em zero linhas para eventos criados/atualizados pelo novo fluxo, e os dois testes que simulavam falha do `LegacyScaleMirrorService` foram substituidos por testes de atomicidade da escrita oficial (falha em `EventAssignmentCompatibilityService.synchronizeAssignments` reverte evento, assignments e a limpeza do espelho).
- `CelebrationEvent.people`/`@ManyToMany`/`@JoinTable(tb_event_person)` permanecem mapeados na entidade nesta branch, apenas sem uso funcional na escrita/leitura de escala; a remocao do mapeamento fica para a migration destrutiva final.
- Validado em MySQL 8.4.10 real (container `mysql:8.4`, banco novo): upgrade V1-V5 seguido de V6 e banco novo V1-V6 aplicados sem erro; `information_schema.table_constraints` confirmou a troca de `uk_tb_event_assignment_event_person` por `uk_tb_event_assignment_event_person_type`; um segundo `INSERT` com o mesmo `event_id`/`person_id` e `assignment_type` diferente foi aceito (prova de multifuncao no nivel de banco) e um `INSERT` repetindo o mesmo triplo foi rejeitado com `ERROR 1062 Duplicate entry ... for key 'uk_tb_event_assignment_event_person_type'`; Hibernate `ddl-auto=validate` nao acusou beans orfaos; `GET /eventos/escala/eucaristia` e `GET /eventos/{id}` responderam `200` sem explosao de consultas no `general_log`. Container, volume e portas 8080/3307 foram removidos ao final.
- Suite: 484 testes (478 antes desta etapa), 0 falhas, 0 erros. Alem da migration V6 e do teste dedicado `EventAssignmentUniqueConstraintMigrationIntegrationTest`, foram criados/ajustados testes cobrindo pessoa em duas e em tres funcoes, duplicidade do mesmo par, diff de escrita por par (adicao/remocao de apenas uma funcao preservando a outra), leitura paralela com multifuncao, e a aceitacao de subtipo legado divergente nas quatro integracoes que antes provavam rejeicao (`EucharisticMinisterScaleLegacyCompatibilityIntegrationTest`, `MinisterOfTheWordScaleLegacyCompatibilityIntegrationTest`, `PriestEventLegacyCompatibilityIntegrationTest`, `ScaleParticipantEligibilityIntegrationTest`).
- Proxima etapa planejada: preparar a migration destrutiva de remocao fisica de `tb_event_person` e do mapeamento `CelebrationEvent.people`, apos periodo de estabilidade sem dependencia de linhas historicas.
