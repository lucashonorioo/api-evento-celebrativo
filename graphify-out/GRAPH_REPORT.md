# Graph Report - evento-celebrativo-completo  (2026-07-26)

## Corpus Check
- 387 files · ~168,713 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 4115 nodes · 11239 edges · 167 communities (146 shown, 21 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 1265 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `aab5b10c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Commentator Controller
- Event Schedule Detail Component
- Auth Session & Interceptor
- Event/Error Response DTOs & Person Subtype Mapper/Entity
- Admin User Management Component
- Event Detail/List Component & Tests
- Person Subtype DTOs & Rollback Tests
- Event Response DTOs & Exceptions
- Celebration Event Projections & Repo Test
- Minister Of The Word DTOs
- PersonMinistry Entity & Repository Test
- Event Assignment Audit Models & Page Component
- Ministry Type & Person Repository
- Celebration Event Repository & Mapping
- Event Assignment Repository Test
- Person Management Form Helpers
- Location List & Event Schedule Specs
- Error Response & Exception Types
- Priest Parallel Cutover Consistency Test
- Angular Build Config
- Event Assignment Legacy Compatibility Test
- Minister Of The Word List/Management Component
- Person Entity & DTO Mapping
- Event Assignment Read Service Impl
- Celebration Event Controller Test
- Eucharistic Minister Parallel Cutover Test
- Minister Of The Word Parallel Cutover Test
- Person Management Templates
- Celebration Event Request/Response DTO & Mapper
- Commentator Parallel Cutover Consistency Test
- Reader Parallel Cutover Consistency Test
- Commentator List/Management Component
- Eucharistic Minister List/Management Component
- Event Schedule Assignment/Query DTOs
- Reader List/Management Component
- Authorization Server Config (OAuth2)
- Event Assignment Compatibility Service Test
- Celebration Event With Scale Request DTO
- Person Ministry Read Service & Parallel Test
- Person Ministry Backfill Migration (V4)
- Celebration Event Scale Detail Response DTO
- Ministry Type & Person Repository
- Event Schedule Edit Component
- Eucharist Schedule List Component
- Event Assignment Backfill Migration Test
- Monthly Schedule Read Cutover Parallel Test
- Access Denied & Admin Guard
- Person Service Impl Test (Roles)
- Eucharist Scale Read Cutover Parallel Test
- Celebration Event Scale Request DTO
- Event Assignment Parallel Cutover Consistency Test
- Celebration Event Scale Person/Response DTO
- Priest Event Legacy Compatibility Test
- Event Assignment Official Write Test
- Person Controller Test (Method Security)
- Celebration Event Controller
- Event Scale Assignment Plan Builder
- Event Assignment Consistency Report & Service
- CLAUDE.md — Graphify Consulta e Atualização
- Eucharistic Minister Controller
- Priest Service Impl & Test
- Event Scale Detail Read Cutover Parallel Test
- Event Schedule Create Component
- Celebration Event Scale Mapper
- Location Service Impl & Test
- Scale Participant Eligibility Integration Test
- OpenAPI Config & Minister Controller
- Event Assignment Type & Consistency Model
- Legacy Event Assignment Snapshot Resolver Test
- Local Flyway Migration Integration Test
- Test Profile Flyway Integration Test
- Reader Parallel Cutover Isolated Lifecycle Test
- Person Domain Migration Docs & Skills
- Celebration Event Request DTO & Eucharist Scale Projection
- Reader Service Impl Test
- Person Ministry Backfill Migration Test
- Eucharistic Minister Scale Legacy Compatibility Test
- Event Assignment Consistency Service Impl Test
- Person Controller
- Person Service Impl (core)
- Event Assignment Write-Through Rollback Test
- Event Assignment Parallel Read Migrated DB Test
- Minister Of The Word Scale Legacy Compatibility Test
- Person Repository Test
- Location Controller
- Priest Controller
- Endpoint Security Test
- Eucharistic Minister Controller
- Minister Of The Word Controller Test
- Priest Controller Test
- Reader Controller Test
- Minister Of The Word Entity
- Person Entity & DTO Mapping
- Angular Dev/Test Tooling Dependencies
- Angular Core Dependencies
- Resource Server Config (Security)
- Reader Request DTO & Mapper
- Person Admin/Role Update Mapper
- Eucharist Scale Event Projection & Repository
- Location Controller Test
- Eucharistic Minister Controller
- Reader Controller
- Person Admin Response DTO
- Commentator/Eucharistic Minister Mapper & Entity
- Priest Entity
- Eucharistic Minister Ministry Read Cutover Parallel Test
- Flyway Migration Integration Test
- Commentator Ministry Read Cutover Parallel Test
- Parallel Read SQL Assertion Helpers
- Person Ministry Eligibility Resolver Test
- Event Assignment Unique Constraint Migration Test
- Eucharistic Minister Controller
- Reader Response DTO & Service
- Location Response DTO & Service
- Location Request DTO & Mapper
- Location Entity
- Priest Response DTO & Service
- Claude Code Skills & Reviewer Subagents
- Custom Password Auth Provider (OAuth2)
- Custom Password Authentication Token
- Public Controller (Login Proxy)
- Monthly Schedule Read Cutover Failure Test
- Claude Hooks - Pre-Tool Guard
- Codex Hooks - Pre-Tool Guard
- Maven Wrapper Script
- SQL Capture Test Config
- graphify Skill & Reference Docs
- Custom Password Auth Converter
- Eucharistic Minister Controller
- Priest Request DTO & Mapper
- Celebration Event Scale DTOs & Method Security Config
- Person Controller
- Event Schedule Type Enum & Monthly Tests
- Commentator Ministry Read Cutover Failure Test
- Eucharistic Minister Ministry Read Cutover Failure Test
- Unknown Person & Consistency Service Test
- Event Scale Detail Read Cutover Failure Test
- Minister Of The Word Ministry Read Cutover Failure Test
- Priest Ministry Read Cutover Failure Test
- Reader Ministry Read Cutover Failure Test
- Event Assignment Consistency Issue Types
- package.json Config (Prettier)
- Frontend Component Templates (Misc)
- Mass Or Celebration Serializer
- Custom User Authorities
- Security Config (Password Encoder)
- Person Details Projection
- Claude Post-Edit Check Hook
- Angular npm Scripts
- Application Context Load Test
- Claude Hooks Settings Test Fixtures
- Claude Settings.json (Hooks Config)
- Codex Post-Edit Check Hook
- Karma Test Config
- Application Entry Point
- Legacy Person List Component Templates
- karma-coverage Dependency
- karma-jasmine Dependency
- App Root & Layout Templates
- Validate Project Skill (.agents)
- Frontend README
- Access Denied Template
- Index HTML Bootstrap Host
- Backend API Maven Coordinates
- event-schedule-list.component.spec.ts
- OpenApiConfig.java

## God Nodes (most connected - your core abstractions)
1. `Person` - 172 edges
2. `MinistryType` - 120 edges
3. `PersonMinistry` - 93 edges
4. `PersonRepository` - 88 edges
5. `PersonMinistryRepository` - 83 edges
6. `CelebrationEventServiceImplTest` - 81 edges
7. `CelebrationEvent` - 59 edges
8. `EventAssignmentType` - 59 edges
9. `AuthSessionService` - 57 edges
10. `CelebrationEventWithScaleRequestDTO` - 54 edges

## Surprising Connections (you probably didn't know these)
- `Change API Contract Skill (.agents)` --semantically_similar_to--> `Change API Contract Skill (.claude)`  [INFERRED] [semantically similar]
  .agents/skills/change-api-contract/SKILL.md → .claude/skills/change-api-contract/SKILL.md
- `Implement Backend Feature Skill (.agents)` --semantically_similar_to--> `Implement Backend Feature Skill (.claude)`  [INFERRED] [semantically similar]
  .agents/skills/implement-backend-feature/SKILL.md → .claude/skills/implement-backend-feature/SKILL.md
- `Event Assignment Audit Page Template` --references--> `EventAssignmentAuditPageComponent`  [EXTRACTED]
  frontend-web/evento-celebrativo-web/src/app/event-assignment-audit/event-assignment-audit-page/event-assignment-audit-page.component.html → frontend-web/evento-celebrativo-web/src/app/event-assignment-audit/event-assignment-audit-page/event-assignment-audit-page.component.ts
- `Login Template` --references--> `LoginComponent`  [EXTRACTED]
  frontend-web/evento-celebrativo-web/src/app/login/login.component.html → frontend-web/evento-celebrativo-web/src/app/login/login.component.ts
- `AdminUserManagementComponent template` --references--> `Auditoria de dependências legadas do domínio de pessoas`  [INFERRED]
  frontend-web/evento-celebrativo-web/src/app/admin-users/admin-user-management/admin-user-management.component.html → backend/evento-celebrativo-api/docs/architecture/person-domain-legacy-dependency-audit.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Regras de uso do Graphify (consulta, atualização, saúde, tokens)** — claude_consulta_prioritaria, claude_atualizacao_incremental, claude_saude_do_grafo, claude_uso_eficiente_de_tokens [EXTRACTED 1.00]
- **Trio de comandos de consulta do Graphify (query, path, explain)** — claude_graphify_query_command, claude_graphify_path_command, claude_graphify_explain_command [EXTRACTED 1.00]
- **Review-Change Reviewer Delegation Group** — claude_agents_codebase_explorer_codebase_explorer, claude_agents_backend_reviewer_backend_reviewer, claude_agents_frontend_reviewer_frontend_reviewer, claude_agents_test_reviewer_test_reviewer, claude_agents_security_reviewer_security_reviewer [EXTRACTED 1.00]
- **graphify Modular Reference Pipeline** — claude_skills_graphify_references_add_watch_add_watch, claude_skills_graphify_references_exports_exports, claude_skills_graphify_references_extraction_spec_extraction_spec, claude_skills_graphify_references_github_and_merge_github_and_merge, claude_skills_graphify_references_hooks_hooks, claude_skills_graphify_references_query_query, claude_skills_graphify_references_transcribe_transcribe, claude_skills_graphify_references_update_update [INFERRED 0.85]
- **Read-Only Reviewer Agent Design Pattern** — claude_agents_backend_reviewer_backend_reviewer, claude_agents_frontend_reviewer_frontend_reviewer, claude_agents_security_reviewer_security_reviewer, claude_agents_test_reviewer_test_reviewer [INFERRED 0.85]
- **Documentação da migração do domínio de pessoas (ADR, auditoria legada, roadmap)** — backend_evento_celebrativo_api_docs_adr_0001_separate_person_ministry_account_and_event_assignment_adr, backend_evento_celebrativo_api_docs_architecture_person_domain_legacy_dependency_audit_audit_report, backend_evento_celebrativo_api_docs_architecture_person_domain_migration_roadmap_roadmap [EXTRACTED 1.00]
- **Fluxo de skills do Claude Code referenciado pelo AGENTS.md raiz** — agents_monorepo_instructions, _claude_skills_implement_frontend_feature_skill_definition, _claude_skills_investigate_bug_skill_definition, _claude_skills_review_change_skill_definition, _claude_skills_validate_project_skill_definition [EXTRACTED 1.00]
- **Padrão de cutover LEGACY/PARALLEL com shadow read para PersonMinistry e EventAssignment** — backend_evento_celebrativo_api_claude_person_ministry_read_source_pattern, backend_evento_celebrativo_api_claude_event_assignment_read_source_pattern, backend_evento_celebrativo_api_claude_shadow_read_mechanism [INFERRED 0.85]
- **Person and Location CRUD Register-Edit-Delete Form Pattern** — frontend_web_evento_celebrativo_web_src_app_eucharistic_ministers_eucharistic_minister_management_eucharistic_minister_management_component_template, frontend_web_evento_celebrativo_web_src_app_ministers_of_the_word_minister_of_the_word_management_minister_of_the_word_management_component_template, frontend_web_evento_celebrativo_web_src_app_priests_priest_management_priest_management_component_template, frontend_web_evento_celebrativo_web_src_app_readers_reader_management_reader_management_component_template, frontend_web_evento_celebrativo_web_src_app_locations_location_management_location_management_component_template [INFERRED 0.85]
- **Read-only Registry List Pattern With Admin-only Manage Link** — frontend_web_evento_celebrativo_web_src_app_locations_location_list_location_list_component_template, frontend_web_evento_celebrativo_web_src_app_priests_priest_list_priest_list_component_template, frontend_web_evento_celebrativo_web_src_app_ministers_of_the_word_minister_of_the_word_list_minister_of_the_word_list_component_template, frontend_web_evento_celebrativo_web_src_app_readers_reader_list_reader_list_component_template [INFERRED 0.85]
- **Event Schedule Create List Detail Edit Navigation Flow** — frontend_web_evento_celebrativo_web_src_app_event_schedules_event_schedule_create_event_schedule_create_component_template, frontend_web_evento_celebrativo_web_src_app_event_schedules_event_schedule_list_event_schedule_list_component_template, frontend_web_evento_celebrativo_web_src_app_event_schedules_event_schedule_detail_event_schedule_detail_component_template, frontend_web_evento_celebrativo_web_src_app_event_schedules_event_schedule_edit_event_schedule_edit_component_template [INFERRED 0.85]

## Communities (167 total, 21 thin omitted)

### Community 0 - "Commentator Controller"
Cohesion: 0.12
Nodes (14): CommentatorController, DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize, PutMapping, RequestMapping (+6 more)

### Community 1 - "Event Schedule Detail Component"
Cohesion: 0.08
Nodes (7): CelebrationEventRequestDTO, CelebrationEventResponseDTO, CelebrationEventMapper, Mapper, Mapping, Override, Transactional

### Community 2 - "Auth Session & Interceptor"
Cohesion: 0.05
Nodes (29): authInterceptor(), JwtPayload, LoginRequest, TokenResponse, AuthService, Injectable, AuthSessionService, createToken() (+21 more)

### Community 3 - "Event/Error Response DTOs & Person Subtype Mapper/Entity"
Cohesion: 0.26
Nodes (5): Commentator, DiscriminatorValue, Entity, GrantedAuthority, Override

### Community 4 - "Admin User Management Component"
Cohesion: 0.06
Nodes (31): AdminUserManagementComponent, conflictMessageFor(), emptyFilters(), extractMessage(), listErrorMessageFor(), PersonTypeOption, QueryResult, roleUpdateErrorMessageFor() (+23 more)

### Community 5 - "Event Detail/List Component & Tests"
Cohesion: 0.12
Nodes (12): EmptyTestComponent, TestShellComponent, Component, EventListComponent, EmptyTestComponent, TestShellComponent, Component, Component (+4 more)

### Community 6 - "Person Subtype DTOs & Rollback Tests"
Cohesion: 0.06
Nodes (28): Page, Pageable, Query, Repository, PersonRepository, Override, Service, Transactional (+20 more)

### Community 7 - "Event Response DTOs & Exceptions"
Cohesion: 0.09
Nodes (21): ArgumentCaptor, DatabaseException, Mapper, Mapping, MinisterOfTheWordMapper, Mapper, Mapping, PriestMapper (+13 more)

### Community 8 - "Celebration Event Projections & Repo Test"
Cohesion: 0.10
Nodes (3): EventScheduleAssignmentResponseDTO, EventScheduleQueryResponseDTO, EventScheduleEventProjection

### Community 9 - "Minister Of The Word DTOs"
Cohesion: 0.19
Nodes (6): Override, Transactional, ExtendWith, PasswordEncoder, Test, MinisterOfTheWordServiceImplTest

### Community 10 - "PersonMinistry Entity & Repository Test"
Cohesion: 0.11
Nodes (3): PriestRequestDTO, PriestResponseDTO, PriestService

### Community 11 - "Event Assignment Audit Models & Page Component"
Cohesion: 0.08
Nodes (6): EventAssignmentAuditEvent, EventAssignmentAuditIssue, errorMessageFor(), EventAssignmentAuditPageComponent, trimmedOrUndefined(), Component

### Community 12 - "Ministry Type & Person Repository"
Cohesion: 0.08
Nodes (22): MinistryType, COMMENTATOR, EUCHARISTIC_MINISTER, MINISTER_OF_THE_WORD, PRIEST, READER, Page, Pageable (+14 more)

### Community 14 - "Event Assignment Repository Test"
Cohesion: 0.20
Nodes (8): AutoConfigureTestDatabase, EventAssignmentRepositoryTest, DataJpaTest, EntityManagerFactory, JdbcTemplate, Statistics, Test, TestEntityManager

### Community 15 - "Person Management Form Helpers"
Cohesion: 0.23
Nodes (22): COMMENTATOR_LABELS, EUCHARISTIC_MINISTER_LABELS, MINISTER_OF_THE_WORD_LABELS, normalizePersonManagementRequest(), PersonManagementFormValue, todayLocalDate(), notBlankValidator(), pastDateValidator() (+14 more)

### Community 16 - "Location List & Event Schedule Specs"
Cohesion: 0.08
Nodes (13): createResponse(), setup(), errorMessageFor(), LocationListComponent, Component, deleteErrorMessageFor(), LocationManagementComponent, saveErrorMessageFor() (+5 more)

### Community 17 - "Error Response & Exception Types"
Cohesion: 0.08
Nodes (15): ErrorResponse, FieldMessage, BadRequestException, BusinessException, ConflictException, ErrorResponseException, ResourceNotFoundException, ValidationErrorResponse (+7 more)

### Community 18 - "Priest Parallel Cutover Consistency Test"
Cohesion: 0.11
Nodes (13): AutoConfigureMockMvc, JdbcTemplate, JsonNode, MockMvc, MvcResult, ObjectMapper, ResultActions, SpringBootTest (+5 more)

### Community 19 - "Angular Build Config"
Cohesion: 0.05
Nodes (45): build, extract-i18n, serve, test, builder, configurations, defaultConfiguration, options (+37 more)

### Community 20 - "Event Assignment Legacy Compatibility Test"
Cohesion: 0.15
Nodes (10): AssignmentSnapshot, EventAssignmentLegacyCompatibilityIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest, Test (+2 more)

### Community 21 - "Minister Of The Word List/Management Component"
Cohesion: 0.10
Nodes (9): errorMessageFor(), MinisterOfTheWordListComponent, Component, MinisterOfTheWordManagementComponent, Component, MinisterOfTheWordRequest, MinisterOfTheWordResponse, MinisterOfTheWordService (+1 more)

### Community 22 - "Person Entity & DTO Mapping"
Cohesion: 0.09
Nodes (17): Override, Service, Transactional, PersonMinistryConsistencyServiceImpl, Summary, Component, MinistryTypeResolver, PersonMinistryConsistencyEntry (+9 more)

### Community 23 - "Event Assignment Read Service Impl"
Cohesion: 0.16
Nodes (7): EventAssignmentReadServiceImpl, Override, Service, Transactional, EventAssignmentReadServiceImplTest, ExtendWith, Test

### Community 24 - "Celebration Event Controller Test"
Cohesion: 0.11
Nodes (3): CelebrationEventControllerTest, Test, WithMockUser

### Community 25 - "Eucharistic Minister Parallel Cutover Test"
Cohesion: 0.11
Nodes (13): EucharisticMinisterParallelCutoverConsistencyIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, JsonNode, MockMvc, MvcResult, ObjectMapper, ResultActions (+5 more)

### Community 26 - "Minister Of The Word Parallel Cutover Test"
Cohesion: 0.11
Nodes (13): AutoConfigureMockMvc, JdbcTemplate, JsonNode, MockMvc, MvcResult, ObjectMapper, ResultActions, SpringBootTest (+5 more)

### Community 27 - "Person Management Templates"
Cohesion: 0.08
Nodes (18): Eucharistic Minister Management Template, Location List Template, Location Management Template, Minister Of The Word List Template, Minister Of The Word Management Template, errorMessageFor(), PriestListComponent, Priest List Template (+10 more)

### Community 28 - "Celebration Event Request/Response DTO & Mapper"
Cohesion: 0.20
Nodes (7): Override, Service, Transactional, PersonMinistryCommandServiceImpl, ExtendWith, Test, PersonMinistryCommandServiceImplTest

### Community 29 - "Commentator Parallel Cutover Consistency Test"
Cohesion: 0.10
Nodes (14): PersonPayload, CommentatorParallelCutoverConsistencyIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, JsonNode, MockMvc, MvcResult, ObjectMapper (+6 more)

### Community 30 - "Reader Parallel Cutover Consistency Test"
Cohesion: 0.11
Nodes (13): AutoConfigureMockMvc, JdbcTemplate, JsonNode, MockMvc, MvcResult, ObjectMapper, ResultActions, SpringBootTest (+5 more)

### Community 31 - "Commentator List/Management Component"
Cohesion: 0.10
Nodes (9): CommentatorListComponent, errorMessageFor(), Component, CommentatorManagementComponent, Component, CommentatorRequest, CommentatorResponse, CommentatorService (+1 more)

### Community 32 - "Eucharistic Minister List/Management Component"
Cohesion: 0.10
Nodes (9): errorMessageFor(), EucharisticMinisterListComponent, Component, EucharisticMinisterManagementComponent, Component, EucharisticMinisterRequest, EucharisticMinisterResponse, EucharisticMinisterService (+1 more)

### Community 33 - "Event Schedule Assignment/Query DTOs"
Cohesion: 0.13
Nodes (10): Entity, PrePersist, PreUpdate, Table, PersonMinistry, DataJpaTest, JdbcTemplate, Test (+2 more)

### Community 34 - "Reader List/Management Component"
Cohesion: 0.10
Nodes (9): errorMessageFor(), ReaderListComponent, Component, ReaderManagementComponent, Component, ReaderRequest, ReaderResponse, ReaderService (+1 more)

### Community 35 - "Authorization Server Config (OAuth2)"
Cohesion: 0.15
Nodes (18): AuthorizationServerSettings, AuthorizationServerConfig, Bean, Configuration, HttpSecurity, OAuth2AuthorizationService, OAuth2Token, OAuth2TokenGenerator (+10 more)

### Community 36 - "Event Assignment Compatibility Service Test"
Cohesion: 0.35
Nodes (4): EventAssignmentTarget, EventAssignmentCompatibilityServiceImplTest, Test, SuppressWarnings

### Community 38 - "Person Ministry Read Service & Parallel Test"
Cohesion: 0.14
Nodes (11): PersonMinistryConsistencyService, Page, Pageable, JdbcTemplate, Page, SpringBootTest, Test, Transactional (+3 more)

### Community 39 - "Person Ministry Backfill Migration (V4)"
Cohesion: 0.10
Nodes (21): Connection, Context, Override, V4__backfill_person_ministries, AssignmentTypeUpdate, EventPersonKey, ExistingAssignment, Connection (+13 more)

### Community 41 - "Ministry Type & Person Repository"
Cohesion: 0.19
Nodes (8): CommentatorServiceImpl, Override, Service, Transactional, CommentatorServiceImplTest, ExtendWith, PasswordEncoder, Test

### Community 42 - "Event Schedule Edit Component"
Cohesion: 0.04
Nodes (53): errorMessageFor(), EventScheduleDetailComponent, isEventScheduleType(), parseEventId(), ParticipantSection, Component, validBackQueryParams(), isEventScheduleType() (+45 more)

### Community 43 - "Eucharist Schedule List Component"
Cohesion: 0.12
Nodes (12): EucharistScheduleListComponent, isIsoDate(), firstDayOfCurrentMonth(), formatLocalDate(), lastDayOfCurrentMonth(), Component, validatePeriod(), EucharistSchedulePage (+4 more)

### Community 44 - "Event Assignment Backfill Migration Test"
Cohesion: 0.20
Nodes (6): EventAssignmentBackfillMigrationIntegrationTest, DataSource, JdbcTemplate, MigrateResult, Test, Timestamp

### Community 45 - "Monthly Schedule Read Cutover Parallel Test"
Cohesion: 0.14
Nodes (11): AutoConfigureMockMvc, EntityManager, EntityManagerFactory, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest, Statistics (+3 more)

### Community 46 - "Access Denied & Admin Guard"
Cohesion: 0.11
Nodes (18): AccessDeniedComponent, Component, adminGuard(), App, appConfig, routes, expectAppRouteProtection(), expectLazyComponent() (+10 more)

### Community 47 - "Person Service Impl Test (Roles)"
Cohesion: 0.18
Nodes (4): AfterEach, ExtendWith, Test, PersonServiceImplTest

### Community 48 - "Eucharist Scale Read Cutover Parallel Test"
Cohesion: 0.14
Nodes (11): EucharistScaleReadCutoverParallelIntegrationTest, AutoConfigureMockMvc, EntityManager, EntityManagerFactory, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest (+3 more)

### Community 49 - "Celebration Event Scale Request DTO"
Cohesion: 0.08
Nodes (13): CelebrationEventScaleRequestDTO, CelebrationEventScaleDetailMapper, CelebrationEventScaleMapper, Mapper, Mapping, EventAssignmentReadService, CelebrationEventServiceImpl, Page (+5 more)

### Community 50 - "Event Assignment Parallel Cutover Consistency Test"
Cohesion: 0.09
Nodes (20): EventAssignmentParallelCutoverConsistencyIntegrationTest, AfterEach, AutoConfigureMockMvc, Bean, EntityManager, EntityManagerFactory, JdbcTemplate, JsonNode (+12 more)

### Community 51 - "Celebration Event Scale Person/Response DTO"
Cohesion: 0.15
Nodes (10): EventAssignmentRepository, Query, Repository, EventAssignmentCompatibilityServiceImpl, Override, Service, Transactional, PersonAssignmentTypeKey (+2 more)

### Community 52 - "Priest Event Legacy Compatibility Test"
Cohesion: 0.22
Nodes (4): JdbcTemplate, SpringBootTest, Test, PriestEventLegacyCompatibilityIntegrationTest

### Community 53 - "Event Assignment Official Write Test"
Cohesion: 0.20
Nodes (8): EventAssignmentOfficialWriteIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest, Test, WithMockUser

### Community 54 - "Person Controller Test (Method Security)"
Cohesion: 0.18
Nodes (9): EnableMethodSecurity, Import, MockMvc, Test, TestConfiguration, WebMvcTest, WithMockUser, MethodSecurityTestConfig (+1 more)

### Community 55 - "Celebration Event Controller"
Cohesion: 0.16
Nodes (14): CelebrationEventController, DeleteMapping, GetMapping, Operation, Page, PostMapping, PreAuthorize, PutMapping (+6 more)

### Community 56 - "Event Scale Assignment Plan Builder"
Cohesion: 0.21
Nodes (6): Builder, Entry, EventScaleAssignmentPlan, PersonAssignmentTypeKey, EventScaleAssignmentPlanTest, Test

### Community 57 - "Event Assignment Consistency Report & Service"
Cohesion: 0.29
Nodes (5): EventPersonSchemaRemovalMigrationIntegrationTest, DataSource, JdbcTemplate, MigrateResult, Test

### Community 58 - "CLAUDE.md — Graphify Consulta e Atualização"
Cohesion: 0.09
Nodes (29): Atualização incremental do grafo (graphify update .), Consulta prioritária (priorizar Graphify antes de busca ampla), Contexto (estrutura do monorepo), Contratos backend e frontend, Definição de concluído, Extensões do projeto (skills, subagents, hooks), Fluxo de alteração, Fonte de verdade (+21 more)

### Community 59 - "Eucharistic Minister Controller"
Cohesion: 0.19
Nodes (6): Override, Transactional, EucharisticMinisterServiceImplTest, ExtendWith, PasswordEncoder, Test

### Community 60 - "Priest Service Impl & Test"
Cohesion: 0.18
Nodes (6): Override, Transactional, ExtendWith, PasswordEncoder, Test, PriestServiceImplTest

### Community 61 - "Event Scale Detail Read Cutover Parallel Test"
Cohesion: 0.15
Nodes (11): EventScaleDetailReadCutoverParallelIntegrationTest, AutoConfigureMockMvc, EntityManager, EntityManagerFactory, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest (+3 more)

### Community 62 - "Event Schedule Create Component"
Cohesion: 0.12
Nodes (10): destinationFor(), EventScheduleCreateComponent, filterByName(), normalizeTime(), PersonOption, saveErrorMessageFor(), SearchName, SelectionControlName (+2 more)

### Community 63 - "Celebration Event Scale Mapper"
Cohesion: 0.10
Nodes (5): CelebrationEvent, Entity, Override, Table, EventAssignmentCompatibilityService

### Community 64 - "Location Service Impl & Test"
Cohesion: 0.23
Nodes (4): Override, Transactional, Test, LocationServiceImplTest

### Community 65 - "Scale Participant Eligibility Integration Test"
Cohesion: 0.22
Nodes (8): AutoConfigureMockMvc, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest, Test, WithMockUser, ScaleParticipantEligibilityIntegrationTest

### Community 66 - "OpenAPI Config & Minister Controller"
Cohesion: 0.22
Nodes (12): DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize, PutMapping, RequestMapping, ResponseEntity (+4 more)

### Community 67 - "Event Assignment Type & Consistency Model"
Cohesion: 0.11
Nodes (11): EventAssignmentType, COMMENTATOR, EUCHARISTIC_MINISTER, MINISTER_OF_THE_WORD, PRIEST, READER, EventAssignmentGroup, PersonAssignmentTypeKey (+3 more)

### Community 68 - "Legacy Event Assignment Snapshot Resolver Test"
Cohesion: 0.10
Nodes (12): EventScheduleType, COMMENTATOR, EUCHARISTIC_MINISTER, MINISTER_OF_THE_WORD, PRIEST, READER, CelebrationEventRepositoryTest, DataJpaTest (+4 more)

### Community 69 - "Local Flyway Migration Integration Test"
Cohesion: 0.18
Nodes (6): ActiveProfiles, Flyway, JdbcTemplate, SpringBootTest, Test, LocalFlywayMigrationIntegrationTest

### Community 70 - "Test Profile Flyway Integration Test"
Cohesion: 0.18
Nodes (6): ActiveProfiles, Flyway, JdbcTemplate, SpringBootTest, Test, TestProfileFlywayIntegrationTest

### Community 71 - "Reader Parallel Cutover Isolated Lifecycle Test"
Cohesion: 0.15
Nodes (8): AutoConfigureMockMvc, JdbcTemplate, MockMvc, ObjectMapper, ResultActions, SpringBootTest, Test, ReaderParallelCutoverIsolatedLifecycleIntegrationTest

### Community 72 - "Person Domain Migration Docs & Skills"
Cohesion: 0.13
Nodes (24): Skill: Implementar alteração frontend, Skill: Investigar bug, Skill: Revisar alteração, Skill: Validar projeto, AGENTS.md — Instruções do monorepo, AGENTS.md — Backend Java/Spring Boot, CLAUDE.md — Backend Java/Spring Boot, Padrão LEGACY/PARALLEL de leitura de EventAssignment (read-source + shadow-read) (+16 more)

### Community 73 - "Celebration Event Request DTO & Eucharist Scale Projection"
Cohesion: 0.13
Nodes (15): EventAssignmentAuditIssueType, EventAssignmentAuditQuery, EventAssignmentAuditResponse, EventAssignmentAuditSummary, EventAssignmentType, AuditResult, IssueMetric, createResponse() (+7 more)

### Community 74 - "Reader Service Impl Test"
Cohesion: 0.10
Nodes (13): ReaderRequestDTO, Mapper, Mapping, ReaderMapper, Override, PasswordEncoder, Service, Transactional (+5 more)

### Community 75 - "Person Ministry Backfill Migration Test"
Cohesion: 0.24
Nodes (6): DataSource, JdbcTemplate, MigrateResult, Test, Timestamp, PersonMinistryBackfillMigrationIntegrationTest

### Community 76 - "Eucharistic Minister Scale Legacy Compatibility Test"
Cohesion: 0.21
Nodes (4): EucharisticMinisterScaleLegacyCompatibilityIntegrationTest, JdbcTemplate, SpringBootTest, Test

### Community 77 - "Event Assignment Consistency Service Impl Test"
Cohesion: 0.12
Nodes (5): EventScheduleEditComponent, filterByName(), loadErrorMessageFor(), parseEventId(), Component

### Community 78 - "Person Controller"
Cohesion: 0.11
Nodes (16): ApiResponses, GetMapping, Operation, Page, PreAuthorize, PutMapping, RequestMapping, ResponseEntity (+8 more)

### Community 79 - "Person Service Impl (core)"
Cohesion: 0.22
Nodes (5): Override, Page, Service, Transactional, PersonServiceImpl

### Community 80 - "Event Assignment Write-Through Rollback Test"
Cohesion: 0.23
Nodes (4): EventAssignmentWriteThroughRollbackIntegrationTest, JdbcTemplate, SpringBootTest, Test

### Community 82 - "Minister Of The Word Scale Legacy Compatibility Test"
Cohesion: 0.24
Nodes (4): JdbcTemplate, SpringBootTest, Test, MinisterOfTheWordScaleLegacyCompatibilityIntegrationTest

### Community 83 - "Person Repository Test"
Cohesion: 0.32
Nodes (4): DataJpaTest, Test, TestEntityManager, PersonRepositoryTest

### Community 84 - "Location Controller"
Cohesion: 0.12
Nodes (14): DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize, PutMapping, RequestMapping, ResponseEntity (+6 more)

### Community 85 - "Priest Controller"
Cohesion: 0.20
Nodes (12): DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize, PutMapping, RequestMapping, ResponseEntity (+4 more)

### Community 86 - "Endpoint Security Test"
Cohesion: 0.23
Nodes (6): EndpointSecurityTest, AutoConfigureMockMvc, MockMvc, SpringBootTest, Test, WithMockUser

### Community 87 - "Eucharistic Minister Controller"
Cohesion: 0.22
Nodes (5): EucharisticMinisterControllerTest, MockMvc, Test, WebMvcTest, WithMockUser

### Community 88 - "Minister Of The Word Controller Test"
Cohesion: 0.22
Nodes (5): MockMvc, Test, WebMvcTest, WithMockUser, MinisterOfTheWordControllerTest

### Community 89 - "Priest Controller Test"
Cohesion: 0.22
Nodes (5): MockMvc, Test, WebMvcTest, WithMockUser, PriestControllerTest

### Community 90 - "Reader Controller Test"
Cohesion: 0.14
Nodes (7): EucharisticMinisterRequestDTO, EucharisticMinisterMapper, Mapper, Mapping, EucharisticMinisterServiceImpl, PasswordEncoder, Service

### Community 91 - "Minister Of The Word Entity"
Cohesion: 0.20
Nodes (10): AutoConfigureMockMvc, JdbcTemplate, MockMvc, MvcResult, ObjectMapper, SpringBootTest, Test, Transactional (+2 more)

### Community 92 - "Person Entity & DTO Mapping"
Cohesion: 0.19
Nodes (10): AutoConfigureMockMvc, JdbcTemplate, MockMvc, MvcResult, ObjectMapper, SpringBootTest, Test, Transactional (+2 more)

### Community 93 - "Angular Dev/Test Tooling Dependencies"
Cohesion: 0.11
Nodes (19): @angular/build, @angular/cli, @angular/compiler-cli, devDependencies, @angular/build, @angular/cli, @angular/compiler-cli, jasmine-core (+11 more)

### Community 94 - "Angular Core Dependencies"
Cohesion: 0.11
Nodes (19): @angular/common, @angular/compiler, @angular/core, @angular/forms, @angular/platform-browser, @angular/router, dependencies, @angular/common (+11 more)

### Community 95 - "Resource Server Config (Security)"
Cohesion: 0.23
Nodes (13): Bean, Configuration, EnableMethodSecurity, HttpSecurity, Order, SecurityFilterChain, ResourceServerConfig, CorsConfigurationSource (+5 more)

### Community 96 - "Reader Request DTO & Mapper"
Cohesion: 0.22
Nodes (5): CommentatorControllerTest, MockMvc, Test, WebMvcTest, WithMockUser

### Community 97 - "Person Admin/Role Update Mapper"
Cohesion: 0.11
Nodes (9): Mapper, PersonAdminMapper, Mapper, PersonRoleUpdateMapper, Entity, Override, Table, Role (+1 more)

### Community 98 - "Eucharist Scale Event Projection & Repository"
Cohesion: 0.09
Nodes (13): EucharistScaleEventProjection, EventScheduleAssignmentProjection, CelebrationEventRepository, Page, Pageable, Query, Repository, EucharistScaleReadCutoverParallelFailureIntegrationTest (+5 more)

### Community 99 - "Location Controller Test"
Cohesion: 0.23
Nodes (5): MockMvc, Test, WebMvcTest, WithMockUser, LocationControllerTest

### Community 100 - "Eucharistic Minister Controller"
Cohesion: 0.14
Nodes (7): deleteErrorMessageFor(), EventManagementComponent, futureOrPresentDateValidator(), normalizeTime(), saveErrorMessageFor(), todayLocalDate(), Component

### Community 101 - "Reader Controller"
Cohesion: 0.07
Nodes (23): Bean, Configuration, OpenApiConfig, DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize (+15 more)

### Community 103 - "Commentator/Eucharistic Minister Mapper & Entity"
Cohesion: 0.26
Nodes (5): EucharisticMinister, DiscriminatorValue, Entity, GrantedAuthority, Override

### Community 104 - "Priest Entity"
Cohesion: 0.08
Nodes (17): Entity, Override, Table, Person, AutoConfigureMockMvc, JdbcTemplate, MockMvc, MvcResult (+9 more)

### Community 105 - "Eucharistic Minister Ministry Read Cutover Parallel Test"
Cohesion: 0.22
Nodes (10): EucharisticMinisterMinistryReadCutoverParallelIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, MockMvc, MvcResult, ObjectMapper, SpringBootTest, Test (+2 more)

### Community 106 - "Flyway Migration Integration Test"
Cohesion: 0.25
Nodes (6): FlywayMigrationIntegrationTest, ActiveProfiles, Flyway, JdbcTemplate, SpringBootTest, Test

### Community 107 - "Commentator Ministry Read Cutover Parallel Test"
Cohesion: 0.22
Nodes (9): CommentatorMinistryReadCutoverParallelIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, MockMvc, MvcResult, ObjectMapper, SpringBootTest, Test (+1 more)

### Community 109 - "Person Ministry Eligibility Resolver Test"
Cohesion: 0.39
Nodes (4): Component, PersonMinistryEligibilityResolver, Test, PersonMinistryEligibilityResolverTest

### Community 110 - "Event Assignment Unique Constraint Migration Test"
Cohesion: 0.35
Nodes (5): EventAssignmentUniqueConstraintMigrationIntegrationTest, DataSource, JdbcTemplate, MigrateResult, Test

### Community 112 - "Reader Response DTO & Service"
Cohesion: 0.21
Nodes (8): AutoConfigureMockMvc, JdbcTemplate, MockMvc, ObjectMapper, SpringBootTest, Test, WithMockUser, PersonMinistryOfficialWriteIntegrationTest

### Community 113 - "Location Response DTO & Service"
Cohesion: 0.22
Nodes (12): EucharisticMinisterController, DeleteMapping, GetMapping, Operation, PostMapping, PreAuthorize, PutMapping, RequestMapping (+4 more)

### Community 114 - "Location Request DTO & Mapper"
Cohesion: 0.15
Nodes (9): LocationRequestDTO, Mapper, Mapping, LocationMapper, Repository, LocationRepository, Service, LocationServiceImpl (+1 more)

### Community 115 - "Location Entity"
Cohesion: 0.17
Nodes (5): EventAssignment, Entity, PrePersist, PreUpdate, Table

### Community 116 - "Priest Response DTO & Service"
Cohesion: 0.17
Nodes (4): CommentatorRequestDTO, CommentatorMapper, Mapper, Mapping

### Community 117 - "Claude Code Skills & Reviewer Subagents"
Cohesion: 0.41
Nodes (12): Change API Contract Skill (.agents), Implement Backend Feature Skill (.agents), Implement Frontend Feature Skill (.agents), Investigate Bug Skill (.agents), Review Change Skill (.agents), Backend Reviewer Agent, Codebase Explorer Agent, Frontend Reviewer Agent (+4 more)

### Community 118 - "Custom Password Auth Provider (OAuth2)"
Cohesion: 0.36
Nodes (9): AuthenticationProvider, CustomPasswordAuthenticationProvider, Authentication, OAuth2AuthorizationService, OAuth2Token, OAuth2TokenGenerator, PasswordEncoder, UserDetailsService (+1 more)

### Community 119 - "Custom Password Authentication Token"
Cohesion: 0.23
Nodes (5): Override, CustomPasswordAuthenticationToken, Authentication, Nullable, OAuth2AuthorizationGrantAuthenticationToken

### Community 120 - "Public Controller (Login Proxy)"
Cohesion: 0.27
Nodes (9): Operation, PostMapping, RequestMapping, ResponseEntity, RestController, Tag, LoginProxyRequest, PublicController (+1 more)

### Community 121 - "Monthly Schedule Read Cutover Failure Test"
Cohesion: 0.16
Nodes (6): AutoConfigureMockMvc, JdbcTemplate, MockMvc, SpringBootTest, Test, MonthlyScheduleReadCutoverParallelFailureIntegrationTest

### Community 122 - "Claude Hooks - Pre-Tool Guard"
Cohesion: 0.38
Nodes (9): destructiveCommandRules, evaluateFileWrite(), evaluateHookPayload(), evaluateShellCommand(), isPathTrackedByGit(), isProtectedEnvFile(), main(), normalizePath() (+1 more)

### Community 123 - "Codex Hooks - Pre-Tool Guard"
Cohesion: 0.36
Nodes (9): deny(), destructiveCommandRules, evaluateHookPayload(), evaluatePatch(), evaluateShellCommand(), extractPatchEntries(), main(), normalizePath() (+1 more)

### Community 124 - "Maven Wrapper Script"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 125 - "SQL Capture Test Config"
Cohesion: 0.23
Nodes (5): DiscriminatorValue, Entity, GrantedAuthority, Override, MinisterOfTheWord

### Community 126 - "graphify Skill & Reference Docs"
Cohesion: 0.20
Nodes (10): .claude/CLAUDE.md graphify Trigger Instruction, graphify Add/Watch Reference, graphify Exports Reference, graphify Extraction Spec Reference, graphify GitHub Clone & Merge Reference, graphify Hooks & CLAUDE.md Integration Reference, graphify Query/Path/Explain Reference, graphify Transcribe Reference (+2 more)

### Community 127 - "Custom Password Auth Converter"
Cohesion: 0.39
Nodes (6): AuthenticationConverter, CustomPasswordAuthenticationConverter, Authentication, Override, HttpServletRequest, MultiValueMap

### Community 128 - "Eucharistic Minister Controller"
Cohesion: 0.19
Nodes (3): EucharistScaleEventResponseDTO, Page, Pageable

### Community 129 - "Priest Request DTO & Mapper"
Cohesion: 0.16
Nodes (4): Entity, Override, Table, Location

### Community 130 - "Celebration Event Scale DTOs & Method Security Config"
Cohesion: 0.26
Nodes (5): DiscriminatorValue, Entity, GrantedAuthority, Override, Priest

### Community 131 - "Person Controller"
Cohesion: 0.24
Nodes (5): DiscriminatorValue, Entity, GrantedAuthority, Override, Reader

### Community 132 - "Event Schedule Type Enum & Monthly Tests"
Cohesion: 0.29
Nodes (3): EventDetailComponent, Event Detail Template, Component

### Community 133 - "Commentator Ministry Read Cutover Failure Test"
Cohesion: 0.39
Nodes (7): CommentatorMinistryReadCutoverParallelFailureIntegrationTest, AutoConfigureMockMvc, DirtiesContext, JdbcTemplate, MockMvc, SpringBootTest, Test

### Community 134 - "Eucharistic Minister Ministry Read Cutover Failure Test"
Cohesion: 0.39
Nodes (7): EucharisticMinisterMinistryReadCutoverParallelFailureIntegrationTest, AutoConfigureMockMvc, DirtiesContext, JdbcTemplate, MockMvc, SpringBootTest, Test

### Community 135 - "Unknown Person & Consistency Service Test"
Cohesion: 0.29
Nodes (6): EnableMethodSecurity, Import, MockMvc, TestConfiguration, WebMvcTest, MethodSecurityConfig

### Community 136 - "Event Scale Detail Read Cutover Failure Test"
Cohesion: 0.39
Nodes (6): EventScaleDetailReadCutoverParallelFailureIntegrationTest, AutoConfigureMockMvc, JdbcTemplate, MockMvc, SpringBootTest, Test

### Community 137 - "Minister Of The Word Ministry Read Cutover Failure Test"
Cohesion: 0.39
Nodes (7): AutoConfigureMockMvc, DirtiesContext, JdbcTemplate, MockMvc, SpringBootTest, Test, MinisterOfTheWordMinistryReadCutoverParallelFailureIntegrationTest

### Community 138 - "Priest Ministry Read Cutover Failure Test"
Cohesion: 0.39
Nodes (7): AutoConfigureMockMvc, DirtiesContext, JdbcTemplate, MockMvc, SpringBootTest, Test, PriestMinistryReadCutoverParallelFailureIntegrationTest

### Community 139 - "Reader Ministry Read Cutover Failure Test"
Cohesion: 0.39
Nodes (7): AutoConfigureMockMvc, DirtiesContext, JdbcTemplate, MockMvc, SpringBootTest, Test, ReaderMinistryReadCutoverParallelFailureIntegrationTest

### Community 141 - "package.json Config (Prettier)"
Cohesion: 0.25
Nodes (7): name, prettier, overrides, printWidth, singleQuote, private, version

### Community 142 - "Frontend Component Templates (Misc)"
Cohesion: 0.36
Nodes (8): Event Assignment Audit Page Template, Event Schedule Create Template, Event Schedule Detail Template, Event Schedule Edit Template, Event Schedule List Template, Event List Template, Event Management Template, Login Template

### Community 143 - "Mass Or Celebration Serializer"
Cohesion: 0.43
Nodes (5): Override, MassOrCelebrationSerializer, JsonGenerator, JsonSerializer, SerializerProvider

### Community 145 - "Security Config (Password Encoder)"
Cohesion: 0.53
Nodes (4): Bean, Configuration, PasswordEncoder, SecurityConfig

### Community 147 - "Claude Post-Edit Check Hook"
Cohesion: 0.60
Nodes (3): buildHookOutput(), main(), runDiffCheck()

### Community 148 - "Angular npm Scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 149 - "Application Context Load Test"
Cohesion: 0.60
Nodes (3): ApiDeEventosCelebrativosApplicationTests, SpringBootTest, Test

### Community 150 - "Claude Hooks Settings Test Fixtures"
Cohesion: 0.40
Nodes (4): here, projectClaudeDir, settings, settingsPath

### Community 151 - "Claude Settings.json (Hooks Config)"
Cohesion: 0.40
Nodes (4): hooks, PostToolUse, PreToolUse, $schema

### Community 153 - "Karma Test Config"
Cohesion: 0.40
Nodes (4): chromeProfile, fs, os, path

### Community 155 - "Legacy Person List Component Templates"
Cohesion: 0.50
Nodes (4): CommentatorListComponent template, CommentatorManagementComponent template, EucharistScheduleListComponent template, EucharisticMinisterListComponent template

### Community 166 - "OpenApiConfig.java"
Cohesion: 0.25
Nodes (5): JWKSource, JwtDecoder, KeyPair, RSAKey, SecurityContext

## Knowledge Gaps
- **154 isolated node(s):** `destructiveCommandRules`, `protectedPathRules`, `here`, `settingsPath`, `projectClaudeDir` (+149 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **21 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Person` connect `Priest Entity` to `Celebration Event Scale DTOs & Method Security Config`, `Event/Error Response DTOs & Person Subtype Mapper/Entity`, `Person Controller`, `Person Subtype DTOs & Rollback Tests`, `Event Response DTOs & Exceptions`, `Minister Of The Word DTOs`, `Ministry Type & Person Repository`, `Celebration Event Repository & Mapping`, `Event Assignment Repository Test`, `Event Assignment Legacy Compatibility Test`, `Person Entity & DTO Mapping`, `Event Assignment Read Service Impl`, `Celebration Event Request/Response DTO & Mapper`, `Event Schedule Assignment/Query DTOs`, `.setName`, `Event Assignment Compatibility Service Test`, `Person Ministry Read Service & Parallel Test`, `Ministry Type & Person Repository`, `Person Service Impl Test (Roles)`, `Celebration Event Scale Request DTO`, `Celebration Event Scale Person/Response DTO`, `Priest Event Legacy Compatibility Test`, `Event Assignment Official Write Test`, `Event Scale Assignment Plan Builder`, `Eucharistic Minister Controller`, `Priest Service Impl & Test`, `Scale Participant Eligibility Integration Test`, `Event Assignment Type & Consistency Model`, `Reader Service Impl Test`, `Eucharistic Minister Scale Legacy Compatibility Test`, `Person Service Impl (core)`, `Event Assignment Write-Through Rollback Test`, `Minister Of The Word Scale Legacy Compatibility Test`, `Person Repository Test`, `Reader Controller Test`, `Minister Of The Word Entity`, `Person Entity & DTO Mapping`, `Person Admin/Role Update Mapper`, `Commentator/Eucharistic Minister Mapper & Entity`, `Eucharistic Minister Ministry Read Cutover Parallel Test`, `Commentator Ministry Read Cutover Parallel Test`, `Person Ministry Eligibility Resolver Test`, `Location Entity`, `Priest Response DTO & Service`, `SQL Capture Test Config`?**
  _High betweenness centrality (0.126) - this node is a cross-community bridge._
- **Why does `PersonRepository` connect `Person Subtype DTOs & Rollback Tests` to `Event Response DTOs & Exceptions`, `Ministry Type & Person Repository`, `Priest Parallel Cutover Consistency Test`, `Event Assignment Legacy Compatibility Test`, `Person Entity & DTO Mapping`, `Eucharistic Minister Parallel Cutover Test`, `Minister Of The Word Parallel Cutover Test`, `Celebration Event Request/Response DTO & Mapper`, `Commentator Parallel Cutover Consistency Test`, `Reader Parallel Cutover Consistency Test`, `Person Ministry Read Service & Parallel Test`, `Person Service Impl Test (Roles)`, `Priest Event Legacy Compatibility Test`, `Event Assignment Official Write Test`, `Scale Participant Eligibility Integration Test`, `Eucharistic Minister Scale Legacy Compatibility Test`, `Person Service Impl (core)`, `Event Assignment Write-Through Rollback Test`, `Minister Of The Word Scale Legacy Compatibility Test`, `Person Repository Test`, `Minister Of The Word Entity`, `Person Entity & DTO Mapping`, `Priest Entity`, `Eucharistic Minister Ministry Read Cutover Parallel Test`, `Commentator Ministry Read Cutover Parallel Test`, `Person Ministry Eligibility Resolver Test`, `Reader Response DTO & Service`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **Why does `MinistryType` connect `Ministry Type & Person Repository` to `Person Subtype DTOs & Rollback Tests`, `Event Response DTOs & Exceptions`, `Celebration Event Repository & Mapping`, `Priest Parallel Cutover Consistency Test`, `Person Entity & DTO Mapping`, `Eucharistic Minister Parallel Cutover Test`, `Minister Of The Word Parallel Cutover Test`, `Celebration Event Request/Response DTO & Mapper`, `Commentator Parallel Cutover Consistency Test`, `Reader Parallel Cutover Consistency Test`, `Event Schedule Assignment/Query DTOs`, `Person Ministry Read Service & Parallel Test`, `Celebration Event Scale Request DTO`, `Priest Event Legacy Compatibility Test`, `Eucharistic Minister Controller`, `Priest Service Impl & Test`, `Scale Participant Eligibility Integration Test`, `Reader Parallel Cutover Isolated Lifecycle Test`, `Eucharistic Minister Scale Legacy Compatibility Test`, `Minister Of The Word Scale Legacy Compatibility Test`, `Minister Of The Word Entity`, `Person Entity & DTO Mapping`, `Priest Entity`, `Eucharistic Minister Ministry Read Cutover Parallel Test`, `Commentator Ministry Read Cutover Parallel Test`, `Person Ministry Eligibility Resolver Test`, `Reader Response DTO & Service`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **What connects `destructiveCommandRules`, `protectedPathRules`, `here` to the rest of the system?**
  _154 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Commentator Controller` be split into smaller, more focused modules?**
  _Cohesion score 0.11553030303030302 - nodes in this community are weakly interconnected._
- **Should `Event Schedule Detail Component` be split into smaller, more focused modules?**
  _Cohesion score 0.0782608695652174 - nodes in this community are weakly interconnected._
- **Should `Auth Session & Interceptor` be split into smaller, more focused modules?**
  _Cohesion score 0.04569083447332421 - nodes in this community are weakly interconnected._