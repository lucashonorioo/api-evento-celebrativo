# Auditoria de dependencias legadas do dominio de pessoas

Data da auditoria: 2026-07-24.

Escopo: dependencias remanescentes do modelo legado durante a refatoracao para `PersonMinistry` e `EventAssignment`.

Esta auditoria e somente documental. Nenhum comportamento de producao, teste, property, migration, endpoint, DTO ou fluxo de escrita foi alterado.

## 1. Resumo executivo

O backend ja possui as estruturas paralelas principais:

- `tb_person_ministry` para funcoes ministeriais.
- `tb_event_assignment` para atribuicoes de escala por evento.
- backfills `V4` e `V5`;
- write-through entre modelo legado e paralelo;
- shadow read;
- cutovers configuraveis;
- auditoria operacional administrativa de `EventAssignment`;
- frontend administrativo para auditoria de assignments.

Apesar disso, o modelo legado ainda e uma dependencia ativa. A remocao imediata de `tb_event_person`, `person_type` ou subclasses de `Person` nao e segura.

Principais razoes:

- os fluxos de escrita de escala ainda montam `CelebrationEvent.people` e persistem `tb_event_person` antes de sincronizar `tb_event_assignment`;
- a validacao de escala ainda exige subtipo Java (`Priest.class`, `Reader.class`, etc.), nao `PersonMinistry`;
- os CRUDs ministeriais ainda criam subclasses concretas (`Reader`, `Commentator`, `Priest`, `MinisterOfTheWord`, `EucharisticMinister`);
- a administracao de usuarios expõe e filtra `personType` no contrato HTTP consumido pelo frontend;
- as leituras `LEGACY`, shadow read, auditorias e testes de rollback ainda dependem do legado;
- `V4` e `V5` sao migrations ja aplicadas e usam `person_type`/`tb_event_person` como fonte historica de backfill;
- nao existe ainda API unificada para manter ministerios de uma pessoa independentemente do subtipo legado.

Conclusao operacional:

- o frontend atual esta tecnicamente desbloqueado para continuar usando os contratos existentes;
- a refatoracao pode ser pausada depois desta auditoria sem bloquear telas atuais;
- a remocao definitiva do legado exige novas branches com migracao de escrita, contratos opcionais/novos e periodo de estabilizacao.

## 2. Arquitetura atual

### Modelo legado preservado

| Item | Evidencia | Uso atual |
| --- | --- | --- |
| `Person` com single-table inheritance | `Person`, `@DiscriminatorColumn(name = "person_type")` | JPA instancia subclasses e expõe `personType` como propriedade somente leitura. |
| Subclasses ministeriais | `Reader`, `Commentator`, `Priest`, `MinisterOfTheWord`, `EucharisticMinister` | CRUDs, repositories, validacao de escala e autenticacao via `UserDetails`. |
| Vínculo evento-pessoa | `CelebrationEvent.people`, `@JoinTable(name = "tb_event_person")` | Escrita de escala, rollback, shadow read, auditoria e leituras legacy. |
| Classificacao por subtipo/discriminator | `person_type` em `tb_person` | Backfill, filtros administrativos, queries legacy, auditoria e mapeamento de snapshots legados. |

### Modelo paralelo

| Item | Evidencia | Uso atual |
| --- | --- | --- |
| `PersonMinistry` | entidade, `PersonMinistryRepository`, `PersonMinistryReadService` | Leitura paralela das cinco listagens ministeriais e write-through dos CRUDs legados. |
| `EventAssignment` | entidade, `EventAssignmentRepository`, `EventAssignmentReadService` | Fonte oficial padrao para os tres endpoints de escala ja cortados para `PARALLEL`. |
| Compatibilidade de escrita | `PersonMinistryCompatibilityService`, `EventAssignmentCompatibilityService` | Sincroniza modelo paralelo depois da escrita legada. |
| Auditorias | `PersonMinistryConsistencyService`, `EventAssignmentConsistencyService`, `EventAssignmentOperationalAuditService` | Detectam divergencias sem alterar dados. |

### Configuracao observada

| Familia | Base | local | test | mysql |
| --- | --- | --- | --- | --- |
| `EventAssignment` scale detail/eucaristia/mensal | `PARALLEL` | `PARALLEL` | `PARALLEL` | `PARALLEL` |
| `PersonMinistry` cinco listagens | `LEGACY` | `PARALLEL` | herda `LEGACY` | herda `LEGACY` |
| `EventAssignment` shadow read | `false` | `true` para os quatro fluxos | `false` | `false` |
| `PersonMinistry` shadow read | `false` | herda `false` | herda `false` | herda `false` |

Observacao: a frase operacional "todos os profiles usando `PARALLEL`" e verdadeira para as tres leituras de `EventAssignment`; nao e verdadeira, no codigo atual, para as cinco leituras de `PersonMinistry`.

## 3. Dependencias de `tb_event_person`

| Arquivo | Metodo/trecho | Endpoint/fluxo | Leitura/escrita | Executado normalmente? | Somente rollback? | Pode remover agora? | Dependencia anterior |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `src/main/java/com/eventoscelebrativos/model/CelebrationEvent.java` | `people` com `@JoinTable(name = "tb_event_person")` | criacao/atualizacao de escala, exclusao de evento, mappers de escrita | leitura e escrita | Sim, em escrita de escala | Nao | Nao | Migrar escrita oficial para `EventAssignment`. |
| `CelebrationEventServiceImpl.applyScaleToEvent` | `celebrationEvent.getPeople().clear/addAll` | `POST /eventos/com-escala`, `PUT /eventos/{id}/escala` | escrita | Sim | Nao | Nao | Novo fluxo que grave assignments como fonte primaria. |
| `CelebrationEventServiceImpl.synchronizeAssignments` | resolve `event.getPeople()` para sincronizar assignments | write-through de escala | leitura do legado + escrita paralela | Sim | Nao | Nao | Resolver targets a partir do request ou de `EventAssignment`. |
| `CelebrationEventScaleMapper` | agrupa `celebrationEvent.getPeople()` por subtipo | response de escrita de escala | leitura em memoria | Sim, nas respostas de POST/PUT escala | Nao | Nao | Mapper baseado em assignments ou DTO montado sem `people`. |
| `CelebrationEventServiceImpl.findScaleByEventIdLegacy` | `findByIdWithPeople`, `peopleByType` | `GET /eventos/{id}/escala` em `LEGACY` | leitura | Nao no default, sim em rollback | Sim | Nao | Encerrar rollback do detalhe de escala. |
| `CelebrationEventServiceImpl.findEventById` | `findByIdWithPeople` para shadow read opcional | `GET /eventos/{id}` | leitura | Sim quando shadow read do detalhe geral estiver habilitado | Nao | Nao | Decidir se detalhe geral tera contrato de participants ou remover shadow read. |
| `CelebrationEventRepository.findByIdWithPeople` | `LEFT JOIN FETCH ce.people` | detalhe legado e shadow read | leitura | Sim em shadow/local e rollback | Parcial | Nao | Remover shadow/read legacy dependente de people. |
| `CelebrationEventRepository.findEucharistScale` | native query com `tb_event_person` + `person_type` | `GET /eventos/escala/eucaristia` em `LEGACY` | leitura | Nao no default | Sim | Nao | Remover rollback da eucaristia. |
| `CelebrationEventRepository.findEventScheduleEvents` | `EXISTS` em `tb_event_person` | `GET /eventos/escalas` em `LEGACY` | leitura | Nao no default | Sim | Nao | Remover rollback mensal. |
| `CelebrationEventRepository.findEventScheduleAssignments` | busca participantes por `tb_event_person` | `GET /eventos/escalas` legacy e shadow parcial | leitura | Sim quando shadow read legado esta habilitado; rollback | Parcial | Nao | Remover shadow/rollback mensal. |
| `CelebrationEventRepository.findLegacyEventAssignmentsForAudit` | leitura em lote de `tb_event_person` + `person_type` | `GET /admin/event-assignments/consistency` | leitura | Sim, endpoint administrativo | Nao | Nao | Definir fim da auditoria comparativa ou trocar por auditoria historica arquivada. |
| `V5__backfill_event_assignments.java` | load/validate `tb_event_person` | Flyway V5 | leitura migratoria | Sim em bancos que ainda aplicarao V5 | Migration only | Nao editar migration aplicada | Nova migration futura, nunca editar V5. |
| `V1__create_current_schema.sql` | cria `tb_event_person` | banco novo | DDL | Sim em banco novo | Compatibilidade | Nao editar V1 | Migration destrutiva futura. |
| `R__load_local_demo_data.sql` | insere `tb_event_person` e depois assignments derivados | seed local | escrita de fixture | Sim em local | Test/demo | Nao nesta tarefa | Atualizar seeds quando legacy for removido. |
| `R__load_test_fixtures.sql` | insere `tb_event_person` e assignments derivados | fixtures de teste | escrita de fixture | Sim em testes | Test/demo | Nao nesta tarefa | Atualizar fixtures quando legacy for removido. |
| Testes de migrations | `EventAssignmentBackfillMigrationIntegrationTest`, `LocalFlywayMigrationIntegrationTest`, `TestProfileFlywayIntegrationTest`, `FlywayMigrationIntegrationTest` | regressao de schema/backfill | leitura/escrita de teste | Sim na suite | Migration regression | Nao | Manter ate remocao final ou congelar como regressao historica. |
| Testes repository/audit | `CelebrationEventRepositoryTest`, `EventAssignmentOperationalAuditRepositoryTest` | queries legacy e auditoria | leitura/escrita de teste | Sim na suite | Parcial | Nao | Remover com queries legadas/auditoria comparativa. |
| Testes de cutover/rollback | `*ReadCutoverLegacyIntegrationTest`, `EventAssignmentParallelCutoverConsistencyIntegrationTest`, `EventAssignmentShadowRead*`, `EventAssignmentLegacyCompatibilityIntegrationTest` | rollback/shadow/equivalencia | leitura/escrita de teste | Sim na suite | Sim/parcial | Nao | Remover apos encerrar rollback/shadow. |
| Documentacao | ADR e roadmap | historico de decisao | n/a | Sim como doc | n/a | Nao | Atualizar por etapa. |

## 4. Dependencias de `person_type`

Classificacao usada:

- `CONTRACT_REQUIRED`: parte do contrato HTTP atual ou do modelo JPA atual.
- `LEGACY_COMPATIBILITY`: necessario para coexistencia com o legado.
- `ROLLBACK_REQUIRED`: necessario enquanto rollback `LEGACY` existir.
- `MIGRATION_ONLY`: necessario por migration/backfill.
- `TEST_ONLY`: necessario apenas para teste/fixture.
- `OBSOLETE`: sem evidencia de chamada real atual.

| Uso | Arquivo/metodo | Classificacao | Impacto |
| --- | --- | --- | --- |
| Discriminator JPA | `Person`, `@DiscriminatorColumn`, campo `personType` | `CONTRACT_REQUIRED` | Sem ele o Hibernate nao instancia subclasses atuais e o contrato admin perde `personType`. |
| `PersonAdminResponseDTO.personType` | `PersonAdminMapper`, `PersonController`, frontend `admin-user.models.ts` | `CONTRACT_REQUIRED` | Frontend de usuarios filtra e exibe tipo legado. Remover exige novo contrato. |
| Filtro administrativo | `PersonRepository.findAdminPageIds`, `PersonServiceImpl.ALLOWED_PERSON_TYPES` | `CONTRACT_REQUIRED` | `GET /pessoas?personType=...` depende do discriminator. |
| `EventScheduleType.getPersonType()` | `EventScheduleType`, `CelebrationEventServiceImpl.findEventSchedulesLegacy` | `ROLLBACK_REQUIRED` | Usado no modo mensal `LEGACY` e shadow parcial. |
| Query eucaristica legacy | `CelebrationEventRepository.findEucharistScale` | `ROLLBACK_REQUIRED` | Seleciona ministros por `p.person_type = 'eucharistic_minister'`. |
| Query mensal legacy | `findEventScheduleEvents`, `findEventScheduleAssignments` | `ROLLBACK_REQUIRED` | Seleciona eventos e participantes por subtipo legado. |
| Auditoria operacional de assignments | `findLegacyEventAssignmentsForAudit`, `EventAssignmentOperationalAuditServiceImpl.toAssignmentType` | `LEGACY_COMPATIBILITY` | Compara `person_type` legado contra `assignment_type`. |
| Auditoria interna de ministries | `PersonMinistryConsistencyServiceImpl`, `MinistryTypeResolver` | `LEGACY_COMPATIBILITY` | Verifica se o subtipo possui ministerio esperado; nao e endpoint HTTP. |
| Backfill de ministries | `V4__backfill_person_ministries` | `MIGRATION_ONLY` | Deriva `tb_person_ministry` de `person_type`. |
| Backfill de assignments | `V5__backfill_event_assignments` | `MIGRATION_ONLY` | Deriva `tb_event_assignment` de `tb_event_person` + `person_type`. |
| Seeds local/test | `R__load_local_demo_data.sql`, `R__load_test_fixtures.sql` | `TEST_ONLY`/demo | Criam pessoas de cada subtipo e assignments derivados. |
| Testes de subtipo divergente | testes `*Parallel*IntegrationTest`, `*ScaleLegacyCompatibilityIntegrationTest` | `TEST_ONLY` | Provam diferenca entre subtipo e role/assignment paralelos. |
| Autorizacao | `ResourceServerConfig`, `Role`, `Person.roles` | Nao depende de `person_type` para decisao | Login ainda usa `Person` como `UserDetails`, mas roles sao independentes. |

Situacoes em que o sistema ainda nao permite funcao diferente do subtipo legado:

- `POST /eventos/com-escala` e `PUT /eventos/{id}/escala` validam cada ID com `expectedType.isInstance(person)`.
- Uma pessoa com `PersonMinistry.PRIEST` adicional, mas subtipo `Reader`, pode aparecer em `GET /padres` quando essa leitura estiver em `PARALLEL`, mas ainda nao pode ser usada como `priestId` numa escala.
- O mesmo vale para `MINISTER_OF_THE_WORD` e `EUCHARISTIC_MINISTER` nas escritas de escala.
- Nao existe endpoint administrativo para adicionar/remover `PersonMinistry` de uma pessoa sem criar uma nova pessoa do subtipo legado correspondente.

## 5. Subclasses legadas de pessoa

| Subclasse | Entidade/repository | Service/controller/DTO | Endpoint | Relacao com `PersonMinistry` | Substituicao futura | Impacto frontend |
| --- | --- | --- | --- | --- | --- | --- |
| `Reader` | `Reader`, `ReaderRepository` | `ReaderServiceImpl`, `ReaderController`, `ReaderRequestDTO`, `ReaderResponseDTO`, `ReaderMapper` | `/leitores` | Write-through garante `READER`; listagem pode usar `tb_person_ministry` conforme config | `Person + ministries` com contrato unificado | Tela de leitores pode manter contrato atual se backend adaptar internamente; API unificada exigiria mudanca. |
| `Commentator` | `Commentator`, `CommentatorRepository` | `CommentatorServiceImpl`, `CommentatorController`, DTOs/mappers | `/comentaristas` | Write-through garante `COMMENTATOR`; listagem configuravel | `Person + ministries` | Mesmo impacto de leitores. |
| `Priest` | `Priest`, `PriestRepository` | `PriestServiceImpl`, `PriestController`, DTOs/mappers | `/padres` | Write-through garante `PRIEST`; listagem configuravel | `Person + ministries` | Edicao de escalas usa lista de padres; precisa continuar retornando `id/name` compativel. |
| `MinisterOfTheWord` | `MinisterOfTheWord`, `MinisterOfTheWordRepository` | `MinisterOfTheWordServiceImpl`, controller, DTOs/mappers | `/ministrosDaPalavra` | Write-through garante `MINISTER_OF_THE_WORD`; listagem configuravel | `Person + ministries` | Edicao de escalas usa lista atual; contrato pode ser preservado. |
| `EucharisticMinister` | `EucharisticMinister`, repository | `EucharisticMinisterServiceImpl`, controller, DTOs/mappers | `/ministrosDeEucaristia` | Write-through garante `EUCHARISTIC_MINISTER`; listagem configuravel | `Person + ministries` | Publico e admin de ministros da Eucaristia dependem do contrato atual. |

Demais herdeiros: nao foram encontradas outras subclasses concretas de `Person` no codigo atual.

## 6. Leituras `LEGACY`

### PersonMinistry

| Leitura | Property | Default observado | Profiles | Variavel | Repository legado | Testes explicitos | Condicao de remocao |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /leitores` | `app.person-ministry.read-source.reader` | base/test/mysql `LEGACY`; local `PARALLEL` | base, local, test, mysql | `PERSON_MINISTRY_READ_SOURCE_READER` | `ReaderRepository.findAll()` | `ReaderMinistryReadCutoverLegacyIntegrationTest`, `ReaderLegacyCutoverConsistencyIntegrationTest` | Fonte oficial global `PARALLEL`, frontend validado, rollback encerrado. |
| `GET /comentaristas` | `app.person-ministry.read-source.commentator` | base/test/mysql `LEGACY`; local `PARALLEL` | idem | `PERSON_MINISTRY_READ_SOURCE_COMMENTATOR` | `CommentatorRepository.findAll()` | testes equivalentes de commentator | idem. |
| `GET /padres` | `app.person-ministry.read-source.priest` | base/test/mysql `LEGACY`; local `PARALLEL` | idem | `PERSON_MINISTRY_READ_SOURCE_PRIEST` | `PriestRepository.findAll()` | testes equivalentes de priest | tambem remover dependencia da escrita de escala em subtipo. |
| `GET /ministrosDaPalavra` | `app.person-ministry.read-source.minister-of-the-word` | base/test/mysql `LEGACY`; local `PARALLEL` | idem | `PERSON_MINISTRY_READ_SOURCE_MINISTER_OF_THE_WORD` | `MinisterOfTheWordRepository.findAll()` | testes equivalentes | idem. |
| `GET /ministrosDeEucaristia` | `app.person-ministry.read-source.eucharistic-minister` | base/test/mysql `LEGACY`; local `PARALLEL` | idem | `PERSON_MINISTRY_READ_SOURCE_EUCHARISTIC_MINISTER` | `EucharisticMinisterRepository.findAll()` | testes equivalentes | idem. |

### EventAssignment

| Leitura | Property | Default observado | Profiles | Variavel | Repository legado | Testes explicitos | Condicao de remocao |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET /eventos/{id}/escala` | `app.event-assignment.read-source.event-scale-detail` | `PARALLEL` | base/local/test/mysql | `EVENT_ASSIGNMENT_READ_SOURCE_EVENT_SCALE_DETAIL` | `findByIdWithPeople` + `CelebrationEvent.people` | `EventScaleDetailReadCutoverLegacyIntegrationTest` | Encerrar rollback e shadow legado do detalhe. |
| `GET /eventos/escala/eucaristia` | `app.event-assignment.read-source.eucharist-scale` | `PARALLEL` | base/local/test/mysql | `EVENT_ASSIGNMENT_READ_SOURCE_EUCHARIST_SCALE` | `findEucharistScale` | `EucharistScaleReadCutoverLegacyIntegrationTest` | Encerrar rollback da eucaristia. |
| `GET /eventos/escalas` | `app.event-assignment.read-source.monthly-schedule` | `PARALLEL` | base/local/test/mysql | `EVENT_ASSIGNMENT_READ_SOURCE_MONTHLY_SCHEDULE` | `findEventScheduleEvents`, `findEventScheduleAssignments` | `MonthlyScheduleReadCutoverLegacyIntegrationTest` | Encerrar rollback mensal e shadow parcial. |
| `GET /eventos/{id}` shadow | `app.event-assignment.shadow-read.event-detail-enabled` | base/test/mysql `false`; local `true` | local ativo por default sem `APP_PROFILE` | `EVENT_ASSIGNMENT_SHADOW_READ_EVENT_DETAIL_ENABLED` | `findByIdWithPeople` | `EventAssignmentShadowReadHttpIntegrationTest` | Decidir contrato futuro do detalhe geral. |

Auditorias:

- `EventAssignmentOperationalAuditServiceImpl` sempre usa a leitura legada em lote para comparar `tb_event_person`/`person_type` contra `tb_event_assignment`.
- `PersonMinistryConsistencyServiceImpl` nao tem endpoint HTTP administrativo, mas e usado em testes e validacoes internas.

## 7. Escritas e write-through

| Fluxo | Modelo legado | Modelo paralelo | Ordem/transacao | Validador principal atual | Falha paralela | Falha legada | Risco de divergencia parcial | Cobertura | Rollback |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Criacao de leitor/comentarista/padre/ministro | Salva subclasse em `tb_person` com `person_type` | `ensureMinistry` cria/reativa `tb_person_ministry` | legado primeiro; mesma transacao do service | DTO + repository de subtipo | Transacao reverte pessoa | Nao chega ao paralelo | Baixo, se transacao mantida | `*ServiceImplTest`, `*WriteThroughRollbackIntegrationTest` | Sim, leitura legacy ainda existe. |
| Atualizacao de pessoa ministerial | Atualiza subclasse e senha | `ensureMinistry` garante vinculo | legado primeiro; mesma transacao | repository de subtipo | Transacao reverte atualizacao | Nao chega ao paralelo | Baixo | testes por service/write-through | Sim. |
| Exclusao de pessoa ministerial | Delete por repository de subtipo | `deleteAllForPerson` antes do delete | paralelo primeiro, depois legado; mesma transacao | FK/constraint do legado | Transacao falha antes do delete | Transacao reverte delete de ministries | Baixo | testes de conflito/exclusao | Sim. |
| Criacao de evento simples | `tb_celebration_event` | nenhum assignment | apenas legado | DTO de evento | n/a | falha aborta | n/a | controller/service tests | n/a. |
| Criacao de evento com escala | `CelebrationEvent.people` grava `tb_event_person` | `synchronizeAssignments` grava `tb_event_assignment` | legado primeiro; mesma transacao | subtipo Java por campo do DTO | Transacao reverte evento e `tb_event_person` | Nao sincroniza paralelo | Baixo, dependente da transacao | `EventAssignmentLegacyCompatibilityIntegrationTest`, cutovers | Rollback de leitura sim. |
| Atualizacao de escala | Limpa/regrava `people` e local | sincroniza assignments: cria, atualiza tipo, remove extras | legado primeiro; mesma transacao | subtipo Java por ID | Transacao reverte escala | Nao sincroniza paralelo | Baixo | `EventAssignmentWriteThroughRollbackIntegrationTest` | Sim. |
| Remocao de participante | Remove de `people` | delete assignment correspondente | mesma transacao via synchronize | request de escala | rollback total | rollback total | Baixo | write-through tests | Sim. |
| Troca de padre | troca `Priest` em `people` | remove/cria/atualiza assignment `PRIEST` | mesma transacao | `Priest.class` | rollback total | rollback total | Baixo | cutover/write-through tests | Sim. |
| Exclusao de evento | `deleteById` remove evento e join table por JPA/FK | `deleteAllForEvent` antes de excluir evento | paralelo primeiro; mesma transacao | `existsById` + FK | aborta antes de excluir evento | reverte delete de assignments | Baixo | compatibility tests | Sim. |
| Atualizacao de roles | `tb_person_role` | nenhum modelo novo de account | legado | regras administrativas | n/a | falha aborta | n/a | `PersonServiceImplTest`, HTTP/security | n/a. |

Responsavel principal atual pela validacao:

- Ministerios: subtipo/repository legado e DTO de cada CRUD.
- Escalas: subtipo Java carregado de `PersonRepository.findById`; `PersonMinistry` ainda nao e fonte de permissao de escala.
- Roles: `Person.roles` legado.

## 8. Matriz por endpoint e frontend

| Endpoint backend | Frontend consumidor | Contrato atual | Modelo interno atual | Remocao legada muda JSON? | Status frontend |
| --- | --- | --- | --- | --- | --- |
| `GET /eventos` | `EventService.findAll` | lista `id`, `nameMassOrEvent`, `eventDate`, `eventTime`, `massOrCelebration` | `CelebrationEvent` sem participantes | Nao, se contrato mantido | Mudanca interna transparente. |
| `GET /eventos/{id}` | `EventService.findById` | mesmo DTO de evento simples | legado; shadow read opcional | Nao, enquanto nao expuser escala | Mudanca interna transparente. |
| `POST /eventos`, `PUT /eventos/{id}`, `DELETE /eventos/{id}` | telas admin de eventos | DTO simples | legado | Nao | Nao bloqueado. |
| `POST /eventos/com-escala` | `EventScheduleService.createEventWithSchedule` | campos de escala por arrays `readerIds`, etc. | valida subtipo e grava `tb_event_person` + assignments | Nao se DTO preservado; sim se virar API generica por assignments | Frontend nao bloqueado hoje; futuro contrato unificado pode exigir ajuste. |
| `PUT /eventos/{id}/escala` | `EventScheduleService.updateEventSchedule` | `locationId`, `priestId`, listas por funcao | idem | Nao se backend aceitar mesmo DTO e gravar novo modelo internamente | Mudanca interna transparente possivel. |
| `GET /eventos/{id}/escala` | `EventScheduleService.findByEventId` | grupos `priest/readers/commentators/ministers/eucharisticMinisters` | `EventAssignment` no default | Nao | Nao bloqueado. |
| `GET /eventos/escalas` | `EventScheduleService.findMonthlySchedules` | pagina com `assignmentType` e `assignments` | `EventAssignment` no default | Nao | Nao bloqueado. |
| `GET /eventos/escala/eucaristia` | `EucharistScheduleService` | pagina com nomes de ministros | `EventAssignment` no default | Nao | Nao bloqueado. |
| `/leitores`, `/comentaristas`, `/padres`, `/ministrosDaPalavra`, `/ministrosDeEucaristia` | services especificos de pessoas | `id`, `name`, `phoneNumber`, `birthdayDate`; requests com senha | subtipo legado em escrita; listagem configuravel | Nao se backend mantiver rotas/DTOs; sim se trocar por API unica sem compat | Nao bloqueado para telas atuais. |
| `GET /pessoas`, `GET /pessoas/{id}`, `PUT /pessoas/{id}/roles` | `AdminUserService` | `personType`, roles, phone | `Person.personType` + roles | Sim se remover `personType` sem substituto | Mudanca de contrato necessaria para remover `person_type`. |
| `GET /admin/event-assignments/consistency` | `EventAssignmentAuditService` | resumo/issues sem dados pessoais | compara legado vs paralelo | Sim se remover legado/auditoria comparativa | Frontend de auditoria fica bloqueado por remocao do legado sem novo contrato. |
| `POST /public/login` | `AuthService` | token OAuth/JWT | `Person` como `UserDetails` + roles | Potencialmente sim quando separar `UserAccount` | Requer branch propria de auth. |

Classificacao:

- `frontend nao bloqueado`: telas atuais de eventos, escalas, pessoas ministeriais, auditoria e usuarios podem continuar com os contratos existentes.
- `mudanca interna transparente`: migrar escrita de escala para `EventAssignment` pode ser transparente se mantiver DTOs por funcao.
- `mudanca de contrato necessaria`: remover `personType` de admin de usuarios, remover endpoint de auditoria comparativa ou criar API unificada de pessoas/ministerios.
- `frontend bloqueado`: nenhuma tela atual esta bloqueada para evolucoes que preservem contrato; telas novas de gestao de multiplos ministerios dependem de contrato backend novo.

## 9. Configuracao de profiles

`spring.profiles.active=${APP_PROFILE:local}` esta definido em `src/main/resources/application.properties`.

Comportamento:

- sem `APP_PROFILE`, a aplicacao sobe com profile `local`;
- em desenvolvimento isso e conveniente porque carrega H2 local, Flyway local e dados demonstrativos;
- em testes, `src/test/resources/application.properties` define `spring.profiles.active=test`, entao a suite usa profile `test`;
- em MySQL, e necessario definir `APP_PROFILE=mysql` e as variaveis de datasource;
- em homologacao/producao, se `APP_PROFILE` ficar ausente por erro operacional, o sistema tentara subir com profile `local`, H2 em memoria e seeds locais.

Recomendacao profissional:

- manter nesta auditoria sem alteracao;
- em branch separada de hardening operacional, trocar o default para falhar fechado em ambientes nao locais ou exigir `APP_PROFILE` explicitamente fora de desenvolvimento;
- documentar em deploy que `APP_PROFILE=mysql` e obrigatorio em MySQL/producao.

## 10. Migrations e banco

### Estruturas ainda necessarias

| Estrutura | Banco novo | Banco atualizado | Rollback | Compatibilidade | Leitura/escrita LEGACY |
| --- | --- | --- | --- | --- | --- |
| `tb_person.person_type` | Sim, V1/JPA | Sim | Sim | Sim | Sim |
| `tb_event_person` | Sim, V1/JPA | Sim | Sim | Sim | Sim |
| Subclasses SINGLE_TABLE | Sim | Sim | Sim | Sim | Sim |
| `tb_person_ministry` | Sim, V3 | Sim, V3/V4 | Nao para rollback legacy, mas sim para paralelo | Sim | Nao |
| `tb_event_assignment` | Sim, V3 | Sim, V3/V5 | Nao para rollback legacy, mas sim para paralelo | Sim | Nao |
| `tb_user_account` | Criada por V3 | Criada por V3 | Ainda nao usada por login atual | Preparacao | Nao |

### Migrations futuras provaveis

| Migration futura | Objetivo | Pre-condicao | Risco |
| --- | --- | --- | --- |
| Reforcar constraints de `tb_person_ministry` | garantir FK/unique/indices definitivos e talvez historico de inativacao | API de ministerios estabilizada | Medio. |
| Indices compostos em `tb_event_assignment` | otimizar filtros por `assignment_type`, periodo via join e event_id | volume real observado | Baixo/medio. |
| Nova fonte oficial de escrita de escala | talvez constraints para permitir ou negar multipla funcao por pessoa/evento | decisao de dominio | Alto se contrato mudar. |
| Remover FKs/joins legados | eliminar `tb_event_person` | rollback encerrado, auditoria concluida, frontend compativel | Alto/destrutivo. |
| Remover `person_type` | trocar heranca por `Person` concreto + ministries | API unificada e auth desacoplada | Alto/destrutivo. |
| Remover tabelas/colunas de compatibilidade | limpar `tb_event_person`, discriminator e talvez services legacy | periodo de estabilidade | Alto. |
| Backfill/limpeza de `tb_user_account` | separar credenciais e roles da pessoa | branch de autenticacao | Alto por impacto de login. |

Nao criar nem editar migrations nesta branch.

## 11. Testes que dependem do legado

| Grupo | Exemplos | Dependencia | Classificacao |
| --- | --- | --- | --- |
| Cutover ministerial legacy | `ReaderMinistryReadCutoverLegacyIntegrationTest`, equivalentes das cinco funcoes | repositories por subtipo e `LEGACY` | `KEEP_UNTIL_FINAL_REMOVAL` |
| Cutover ministerial parallel | `*MinistryReadCutoverParallelIntegrationTest` | cria subtipos e ministries para provar divergencia valida | `CONVERT_TO_PARALLEL` depois de API nova |
| Consistencia ministerial | `*ParallelCutoverConsistencyIntegrationTest`, `PersonMinistryConsistencyServiceImplTest` | subtipo esperado vs ministry | `KEEP_UNTIL_FINAL_REMOVAL` |
| Write-through ministerial | `*WriteThroughRollbackIntegrationTest` | pessoa subclasse + `tb_person_ministry` | `KEEP_UNTIL_FINAL_REMOVAL` |
| Backfill V4 | `PersonMinistryBackfillMigrationIntegrationTest` | `person_type` | `MIGRATION_REGRESSION` |
| Backfill V5 | `EventAssignmentBackfillMigrationIntegrationTest` | `tb_event_person` + `person_type` | `MIGRATION_REGRESSION` |
| Cutover de escala legacy | `EventScaleDetailReadCutoverLegacyIntegrationTest`, `EucharistScaleReadCutoverLegacyIntegrationTest`, `MonthlyScheduleReadCutoverLegacyIntegrationTest` | queries legacy | `KEEP_UNTIL_FINAL_REMOVAL` |
| Cutover de escala parallel | `*ReadCutoverParallelIntegrationTest`, `EventAssignmentParallelCutoverConsistencyIntegrationTest` | prova ausencia de `tb_event_person` no paralelo e dados divergentes | `CONVERT_TO_PARALLEL`/manter como regressao |
| Shadow read | `EventAssignmentShadowRead*`, `ReaderShadowReadIntegrationTest`, `PersonMinistryShadowReadExpansionIntegrationTest` | legado + paralelo simultaneamente | `REMOVE_WITH_LEGACY` |
| Repositories legacy | `CelebrationEventRepositoryTest` | queries nativas com `tb_event_person`/`person_type` | `REMOVE_WITH_LEGACY` |
| Auditoria operacional | `EventAssignmentOperationalAudit*` | compara legado vs paralelo | `KEEP_UNTIL_AUDIT_REPLACED` |
| Testes unitarios de services legados | `ReaderServiceImplTest`, `PriestServiceImplTest`, etc. | repositories de subtipo e mappers | `CONVERT_TO_PARALLEL` ou `REMOVE_WITH_LEGACY` conforme contrato escolhido |

## 12. Codigo potencialmente obsoleto

Nao foi identificada peca claramente removivel agora sem quebrar rollback, testes ou contrato.

| Codigo | Evidencia de uso | Status |
| --- | --- | --- |
| `ReaderRepository`, `CommentatorRepository`, `PriestRepository`, `MinisterOfTheWordRepository`, `EucharisticMinisterRepository` | usados em CRUDs, leitura `LEGACY`, `findById`, update/delete | Nao obsoleto. |
| `CelebrationEventRepository.findEucharistScale`, `findEventScheduleEvents`, `findEventScheduleAssignments` | usados por rollbacks e shadow reads; testados explicitamente | Obsoleto apenas apos remocao de rollback/shadow. |
| `LegacyEventAssignmentSnapshotResolver` | usado por `EventAssignmentConsistencyServiceImpl` e testes | Nao obsoleto enquanto auditoria/shadow existirem. |
| `PersonMinistryConsistencyServiceImpl` | usado por testes e validacao interna; sem controller HTTP | Candidato a mover/remover apos decidir se havera auditoria operacional de ministries. |
| `PersonMinistryShadowReadExecutor` | injetado nos cinco services e usado se flags forem habilitadas | Nao obsoleto enquanto flags existirem. |
| `EventAssignmentShadowReadExecutor` | usado em detalhe geral, legacy scale/eucaristia/mensal | Nao obsoleto enquanto shadow existir. |
| `EventAssignmentCompatibilityService` | chamado nas escritas de escala e exclusao de evento | Nao obsoleto ate nova fonte oficial de escrita. |
| `EventAssignmentReadSourceProperties` fallback Java `LEGACY` | properties globais usam `PARALLEL`; fallback sem config ainda `LEGACY` | Candidato a revisar em branch propria se quiser falhar/parallel por codigo, mas nao afeta runtime configurado. |

## 13. Riscos

| Risco | Impacto | Mitigacao |
| --- | --- | --- |
| Remover `tb_event_person` antes de migrar escrita | Quebra POST/PUT de escala, rollback e auditoria | Migrar escrita oficial primeiro e manter compatibilidade ate estabilizar. |
| Remover `person_type` antes de API unificada | Quebra JPA, admin de usuarios, filters e CRUDs ministeriais | Criar `Person` concreto + ministries e contrato novo/compativel. |
| Manter `APP_PROFILE:local` em producao por erro | Risco de subir H2 local sem datasource real | Hardening de profile em branch separada. |
| Listagens ministeriais ainda default `LEGACY` fora local | O modelo novo de multiplas funcoes nao e exercitado por default em test/mysql/producao | Branch separada para ativar `PersonMinistry` globalmente, se aprovado. |
| Escrever escala por subtipo, ler por assignment | Pessoa com assignment divergente aparece em leitura, mas nao pode ser gravada pelo fluxo atual | Migrar validacao para `PersonMinistry` e decidir regra de multipla funcao por evento. |
| Auditoria de EventAssignment depende do legado | Perde utilidade quando legado for removido | Encerrar ou substituir por auditoria de invariantes do novo modelo. |

## 14. Sequencia recomendada de branches

| Ordem | Branch sugerida | Objetivo | Dependencias | Risco | Frontend | Migration | Rollback | Validacoes obrigatorias |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `chore/observe-person-domain-parallel-defaults` | Estabilizacao e observacao de auditorias/metricas | Estado atual | Baixo | Nao impacta | Nao | Mantem | Suite, auditoria admin, logs, MySQL. |
| 2 | `chore/enable-person-ministry-parallel-read-default` | Ativar cinco listagens ministeriais `PARALLEL` globalmente | Auditoria de ministries consistente | Medio | Transparente se DTO igual | Nao | Variaveis `PERSON_MINISTRY_READ_SOURCE_*` | Testes de listagens, frontend smoke, rollback independente. |
| 3 | `feature/person-ministry-management-api` | Criar contrato para uma pessoa ter multiplas funcoes ministeriais | Definir UX e contrato | Alto | Sim, telas novas/alteradas | Talvez indices/constraints | Manter CRUDs antigos | HTTP/security/frontend/build. |
| 4 | `feature/event-scale-write-from-assignments` | Tornar `EventAssignment` fonte principal de escrita de escala mantendo DTO atual | PersonMinistry habilitado/validacao definida | Alto | Pode ser transparente | Talvez constraints/indices | Write-through inverso temporario para `tb_event_person` | Ausencia de divergencia, rollback, MySQL. |
| 5 | `chore/remove-event-assignment-shadow-read` | Remover shadow read de assignments apos estabilidade | Fonte parallel oficial estabilizada | Medio | Nao | Nao | Rollback ainda pode ficar | Suite + logs sem shadow. |
| 6 | `chore/remove-event-assignment-legacy-read` | Remover propriedades/enums/queries `LEGACY` de escala | Rollback oficialmente encerrado | Alto | Nao se contrato mantido | Nao | Nao | Regressao endpoints, MySQL, performance. |
| 7 | `chore/remove-person-ministry-shadow-and-legacy-read` | Remover shadow/legacy das listagens ministeriais | PersonMinistry global e estavel | Alto | Nao se contrato mantido | Nao | Nao | Cinco CRUDs/listagens, segurança. |
| 8 | `feature/user-account-auth-read-model` | Migrar login/roles para `tb_user_account` | Backfill de contas definido | Alto | Potencialmente transparente | Sim | Plano de rollback auth | Login/JWT/security. |
| 9 | `feature/person-domain-unified-person-api` | Introduzir `Person + ministries` como API oficial | API e frontend aprovados | Alto | Sim | Talvez | Manter endpoints antigos durante deprecacao | Contrato full stack. |
| 10 | `chore/deprecate-ministry-subtype-endpoints` | Depreciar `/leitores`, etc. ou torna-los adaptadores | Frontend migrado | Medio | Sim se remover rotas | Nao | Periodo de compat | E2E/contratos. |
| 11 | `db/remove-event-person-legacy-schema` | Remover `tb_event_person` e FKs | Escrita/leitura/auditoria sem legado | Alto/destrutivo | Nao se contrato mantido | Sim | Backup/restore, nao rollback simples | Migration MySQL, backups, dados reais. |
| 12 | `db/remove-person-type-inheritance` | Remover `person_type` e subclasses | API unificada + auth novo | Muito alto | Sim ja deve estar migrado | Sim | Plano de reversao de banco | Migration completa, carga real, suite. |
| 13 | `chore/final-person-domain-cleanup` | Remover testes/docs/codigo morto de compatibilidade | Estruturas removidas | Medio | Nao | Nao | Nao | Suite completa, diff review. |

Estimativa: 10 a 13 etapas restantes, dependendo de a equipe manter endpoints antigos como adaptadores ou migrar o frontend para API unificada.

## 15. Criterios para remocao definitiva

Antes de remover `tb_event_person`:

- escrita de escala gravando `EventAssignment` como fonte primaria;
- nenhuma query de producao usando `tb_event_person`;
- auditoria operacional sem divergencias por periodo definido;
- rollback legacy oficialmente encerrado;
- testes `LEGACY` removidos ou congelados como regressao historica fora da suite principal;
- plano de migration destrutiva com backup e validacao MySQL.

Antes de remover `person_type` e subclasses:

- API de pessoa unificada disponivel;
- frontend migrado ou endpoints antigos adaptados internamente;
- autenticacao/roles desacopladas de subclasses;
- listagens ministeriais usando somente `PersonMinistry`;
- escrita de ministerios independente de subtipo;
- migrations para limpar discriminator e ajustar JPA;
- criterio de rollback operacional definido, provavelmente via backup/restore e nao por flag.

## 16. Itens bloqueadores

- Escala ainda valida funcao por subtipo Java, nao por `PersonMinistry`.
- CRUDs ministeriais ainda criam subclasses e nao uma pessoa com multiplos ministerios.
- `PersonAdminResponseDTO` e frontend admin ainda usam `personType`.
- `EventAssignment` ainda e sincronizado a partir de `CelebrationEvent.people` nas escritas.
- Auditoria operacional de assignments depende do legado como lado de comparacao.
- `tb_user_account` existe, mas login/roles atuais ainda usam `Person`.
- Remocao destrutiva de schema ainda nao possui plano de migration/backout.

## 17. Itens nao bloqueadores

- Frontend atual pode continuar evoluindo telas que usam os contratos existentes.
- Os tres endpoints de escala ja podem operar por `EventAssignment` em `PARALLEL`.
- A remocao de shadow read pode ser planejada separadamente depois de estabilidade.
- As rotas ministeriais podem continuar existindo como adaptadores mesmo com backend novo.
- A auditoria operacional pode continuar como ferramenta temporaria ate o legado ser encerrado.
- E seguro pausar a refatoracao apos esta auditoria, desde que nao se tente remover legado sem as etapas acima.

## 18. Retorno ao frontend

Respostas objetivas:

- O frontend esta tecnicamente desbloqueado agora? Sim, para funcionalidades que preservem contratos atuais.
- O que pode continuar sem aguardar backend? Telas de eventos, escalas, auditoria de assignments, usuarios/roles, listagens e CRUDs ministeriais existentes.
- O que depende de novos contratos? Gestao de multiplas funcoes ministeriais por pessoa, API unificada de pessoa, eventual remocao de `personType` da administracao de usuarios e novo modelo de autenticacao/conta.
- Alguma alteracao futura obrigara retrabalho? Sim, se a equipe decidir substituir os cinco endpoints ministeriais e o contrato de admin usuarios por uma API unificada sem adaptadores.
- E seguro pausar a refatoracao depois desta auditoria? Sim. O estado atual preserva rollback e contratos; o risco principal e operacional, nao de bloqueio imediato do frontend.
