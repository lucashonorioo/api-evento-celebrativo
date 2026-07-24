# Handoff — Auditoria da configuração Claude Code (Evento Celebrativo)

> Este arquivo resume uma auditoria já concluída em outra sessão/máquina. **Não repita a análise.** Leia a seção 10 antes de fazer qualquer coisa.

---

## 1. Objetivo original da auditoria

O usuário solicitou uma **auditoria completa, em modo somente análise**, da configuração do Claude Code usada neste repositório, comparando-a:

- com a documentação oficial e atual da Anthropic para Claude Code;
- com o código real do backend e do frontend (não com o que os arquivos de configuração *dizem* que existe).

Limites definidos explicitamente pelo usuário:

- Não alterar, criar, excluir ou mover arquivos nesta etapa.
- Produzir primeiro um relatório completo para revisão.
- Diferenciar claramente fato observado, inferência e recomendação.
- Citar caminhos de arquivo como evidência.
- Não inventar arquivos, tecnologias, versões ou comportamentos.
- Não declarar algo como validado sem tê-lo executado de fato.
- Ao final, apresentar uma lista **"Alterações recomendadas para aprovação"** — só aplicar após autorização expressa do usuário.

**Confirmação: nenhuma alteração foi aplicada ao projeto durante a auditoria.** Ver seção 8.

---

## 2. Estado do projeto analisado

### Estrutura do monorepo

```text
api-evento-celebrativo/
├── CLAUDE.md                                  (não commitado no momento da auditoria)
├── .claude/                                   (não commitado no momento da auditoria)
├── .agents/                                   (já commitado — config de outra ferramenta, Codex)
├── .codex/                                    (já commitado — config de outra ferramenta, Codex)
├── AGENTS.md                                  (já commitado)
├── backend/evento-celebrativo-api/            API Java/Spring Boot
└── frontend-web/evento-celebrativo-web/       SPA Angular
```

### Backend — `backend/evento-celebrativo-api`

- Java **21**, Spring Boot **3.4.5** (`pom.xml`), Maven Wrapper.
- Segurança: o próprio projeto é Authorization Server **e** Resource Server OAuth2 (`spring-security-oauth2-authorization-server` + `spring-boot-starter-oauth2-resource-server`), com grant customizado de `password` (`config/customgrant/CustomPasswordAuthenticationProvider.java`).
- Persistência: H2 (runtime, perfis local/test) + MySQL (`mysql-connector-j`, perfil `mysql`). **Flyway ativo e real**: `flyway-core` + `flyway-mysql`, migrations `V1__create_current_schema.sql`, `V2__insert_required_roles.sql`, `V3__create_parallel_person_domain_schema.sql` (SQL) + `V4__backfill_person_ministries.java` (Java, em `src/main/java/db/migration/`).
- MapStruct 1.6.3, springdoc-openapi 2.7.0.
- Pacotes: `controller` (10 controllers), `service`/`service.impl`, `repository`, `model`, `dto.request`/`dto.response`, `mapper`, `exception`, `config`, `projection`, `model.serializer`.
- Endpoints públicos confirmados: `POST /public/login`, `GET /eventos`, `GET /eventos/{id}`, `GET /eventos/escala/eucaristia`. `ROLE_ADMIN` exigido em `GET /pessoas`, `PUT /pessoas/*/roles`, `GET /admin/event-assignments/consistency`.
- `GlobalExceptionHandler` converte `DataIntegrityViolationException` em **409 Conflict**.
- ~100 arquivos de teste (controller com `@WebMvcTest`/`MockMvc`/`@MockitoBean`, repository com `@DataJpaTest`, service com Mockito, ~10 `@SpringBootTest` completos, testes de migration).
- **Migração de domínio ativa agora**: modelo legado (tabelas por tipo de ministério) → modelo unificado `Person` + `PersonMinistry`, controlado por `app.person-ministry.read-source.*` (`LEGACY`/`PARALLEL`) com mecanismo de *shadow read* (`app.person-ministry.shadow-read.*-enabled`). Evidência: `config/PersonMinistryReadSourceProperties.java`, `config/EventAssignmentReadSourceProperties.java`, `application.properties`, `ReaderServiceImpl.findAllReaders()`, commits `240891e` e `f402b4b`. **Não documentado no CLAUDE.md do backend** — ver seção 6.

### Frontend — `frontend-web/evento-celebrativo-web`

- Angular **20.3** (`@angular/core: ^20.3.0`), TypeScript 5.9, RxJS 7.8. Bootstrap standalone confirmado (`src/main.ts`, sem `AppModule`).
- `tsconfig.json`: `strict: true` + flags adicionais + `strictTemplates: true`.
- Sem biblioteca de UI, sem NgRx/Redux, sem cliente HTTP alternativo.
- Estado: `signal()` confirmado em 24 componentes; nenhum `computed()`/`effect()` encontrado nos arquivos varridos.
- Autenticação: token em `localStorage`, centralizado em `auth-session.service.ts`; interceptor único (`authInterceptor`); guards funcionais `authGuard`/`adminGuard`/`guestGuard`, todos retornando `UrlTree` ao negar.
- Rotas 100% via `loadComponent` (lazy loading total).
- Formulários: mistura de Template-driven (`login.component.ts`) e Reactive Forms tipados (14 arquivos).
- Testes: Jasmine + Karma + `TestBed`, `provideHttpClientTesting`/`HttpTestingController` confirmados; todo componente/serviço/guard tem `.spec.ts`.
- **Gap confirmado, fora do escopo Claude Code**: não há `environment.ts`/`environment.prod.ts`; URL base hardcoded em `src/app/api.config.ts` (`http://localhost:8080`), inclusive em produção.

### Branch, commit e alterações locais

- Branch no início da sessão: `main` **(fato — informado pelo contexto inicial do harness; não reconfirmado com `git branch --show-current` por mim diretamente)**.
- Commit mais recente observado no início da sessão (via `git log` fornecido pelo harness): `9a284d8` ("Merge pull request #76 from lucashonorioo/chore/enable-person-ministry-parallel-read-mysql") **[NÃO CONFIRMADO por `git rev-parse HEAD` executado por mim — apenas observado no contexto inicial]**. Commits anteriores relevantes: `240891e`, `e904eaa`, `597c347`, `f402b4b`.
- **Alterações locais no momento da auditoria** (`git status --short` na raiz, executado por mim):
  ```
  ?? .claude/
  ?? CLAUDE.md
  ?? backend/evento-celebrativo-api/CLAUDE.md
  ?? frontend-web/evento-celebrativo-web/CLAUDE.md
  ```
  Não há `.gitignore` na raiz do monorepo; `git check-ignore` não retorna nenhuma regra para esses caminhos — ou seja, não é uma exclusão intencional, é conteúdo simplesmente ainda não adicionado ao git.
- `.agents/`, `.codex/`, `AGENTS.md` (config espelhada para a ferramenta Codex CLI) **já estavam commitados** (confirmado via `git ls-files`).

### Data e contexto da análise

- Data da sessão: **24/07/2026**.
- Sessão rodou como **job em background** (declarado no prompt de sistema da sessão original) — dado relevante para a seção 4/6 (hipótese sobre hooks).
- Working directory da sessão original: `C:\IdeaProjects\api-evento-celebrativo\backend\evento-celebrativo-api` (subpasta do monorepo, não a raiz).

---

## 3. Arquivos da configuração Claude Code analisados

Todos os arquivos abaixo foram **lidos diretamente** na sessão original.

| Arquivo | Função |
|---|---|
| `~/.claude/CLAUDE.md` | Instruções pessoais globais do usuário (PT-BR) |
| `~/.claude/settings.json` | Só `{"autoUpdatesChannel":"latest","theme":"dark"}` — sem hooks/permissions |
| `CLAUDE.md` (raiz) | Contexto do monorepo, limites backend/frontend, fluxo de alteração |
| `backend/evento-celebrativo-api/CLAUDE.md` | Instruções Java/Spring (154 linhas aprox.) |
| `frontend-web/evento-celebrativo-web/CLAUDE.md` | Instruções Angular/TS (154 linhas, lidas por completo) |
| `.claude/settings.json` (raiz) | Registra hooks `PreToolUse` (`Bash\|PowerShell`, `Edit\|Write`) e `PostToolUse` (`Edit\|Write`) |
| `backend/evento-celebrativo-api/.claude/settings.local.json` | `permissions.allow` com `Read(...)`, incluindo `Read(//c/Users/TI/.claude/**)` |
| `.claude/hooks/pre-tool-guard.mjs` | Lógica do `PreToolUse` |
| `.claude/hooks/post-edit-check.mjs` | Lógica do `PostToolUse` |
| `.claude/hooks/tests/pre-tool-guard.test.mjs` | 10 testes unitários |
| `.claude/hooks/tests/post-edit-check.test.mjs` | 2 testes unitários |
| `.claude/hooks/tests/settings.test.mjs` | Valida shape do `settings.json` |
| `.claude/agents/backend-reviewer.md` | Subagent leitura Java/Spring |
| `.claude/agents/frontend-reviewer.md` | Subagent leitura Angular/TS |
| `.claude/agents/security-reviewer.md` | Subagent leitura segurança |
| `.claude/agents/test-reviewer.md` | Subagent leitura cobertura/testes |
| `.claude/agents/codebase-explorer.md` | Subagent leitura mapeamento de fluxo |
| `.claude/skills/change-api-contract/SKILL.md` | Mudança de contrato HTTP full-stack |
| `.claude/skills/implement-backend-feature/SKILL.md` | Implementação backend |
| `.claude/skills/implement-frontend-feature/SKILL.md` | Implementação frontend |
| `.claude/skills/investigate-bug/SKILL.md` | Investigação de bug |
| `.claude/skills/review-change/SKILL.md` | Revisão de diff/PR (`context: fork`) |
| `.claude/skills/validate-project/SKILL.md` | Validação final |

**Documentos auxiliares/fora do escopo Claude Code, mas lidos e usados como evidência**: `README.md` (raiz, contém descrição de stack e status desatualizado — ver seção 5), `pom.xml`, `package.json`, `angular.json`, `tsconfig*.json`, `karma.conf.js`, `application*.properties`, `.codex/hooks/*.mjs` e `.codex/hooks.json` (comparados via `diff` com os hooks `.claude/` para checar divergência).

**Arquivos citados no pedido original do usuário que não existem/não se aplicam** (confirmado, não presumido):

- `frontend-web/evento-celebrativo-web/.claude/` — não existe (frontend só tem `AGENTS.md`/`.agents/`, do Codex).
- `backend/evento-celebrativo-api/.claude/settings.json` — não existe, só `settings.local.json`.
- `CLAUDE.local.md`, `.claude/rules/` — não encontrados em nenhum nível.
- `~/.claude/agents/`, `~/.claude/skills/`, `~/.claude/hooks/` — não existem globalmente.

---

## 4. Resumo das verificações realizadas

### Documentação oficial consultada

- Pesquisa delegada a um subagent (fork) com WebFetch em páginas oficiais do domínio `code.claude.com/docs/en/` cobrindo: hierarquia de `CLAUDE.md`, formato de frontmatter de skills, formato de frontmatter de subagents, eventos/formato de hooks. Essa pesquisa confirmou que os campos usados no repositório (`context: fork`, `background: false` em skills; `tools`, `model: inherit`, `permissionMode: plan`, `effort` em subagents) são campos documentados.
- **Um WebFetch adicional, feito diretamente por mim** (não pela pesquisa delegada), em uma página sobre `settings.json`, respondeu que o Claude Code resolveria `.claude/settings.json` sempre na raiz do repositório git, independentemente da subpasta onde a sessão for aberta. **Essa resposta contradiz o teste ao vivo descrito abaixo** e não foi tratada como confiável — ver seção 5/6, item crítico.

### Comandos e testes executados (nesta sessão, diretamente)

| Comando/teste | Resultado |
|---|---|
| `node --test ./tests/*.test.mjs` em `.claude/hooks/` | **12/12 testes passaram** (lógica isolada do script) |
| `echo '{"tool_name":"Bash",...}' \| node pre-tool-guard.mjs` (vários payloads: `git status`, `git push --force`, `rm -rf .`, `rm -rf node_modules`, Write fora do workspace, Write com espaço no caminho, Edit em migration existente) | Todos os resultados esperados **exceto os dois abaixo** |
| `rm -rf /*` (script isolado) | **exit 0 — não bloqueado (deveria bloquear)** |
| `git checkout HEAD -- .` / `git checkout main -- .` (script isolado) | **exit 0 — não bloqueado (deveria bloquear)** |
| `Edit` em migration `V5` recém-criada via `Write`, não commitada (script isolado) | exit 2, bloqueado — **correto pela letra da regra, mas mais rígido que a intenção do CLAUDE.md** (que fala em migration "já aplicada") |
| **`git branch -D essa-branch-nao-existe-teste-auditoria` — chamada REAL da ferramenta Bash desta sessão (não simulação)** | **exit 1, erro nativo do git ("branch not found"), NÃO a mensagem de bloqueio do hook** |
| `git status --short` (raiz) | `?? .claude/`, `?? CLAUDE.md` (x3) |
| `git check-ignore -v` para os caminhos acima | Nenhuma regra — não é ignorado, é apenas não adicionado |
| `git check-ignore -v backend/.../settings.local.json` | Ignorado por regra global do usuário (`~/.config/git/ignore:1: **/.claude/settings.local.json`) |
| `diff` entre `.claude/hooks/*.mjs` e `.codex/hooks/*.mjs` | Confirmadas 3 divergências: regex de `.env`, condição de `git restore`, formato de saída (JSON `permissionDecision` no Codex vs `stderr`+`exit 2` no Claude) |

### Validação real vs. isolada de cada hook — **ponto mais importante deste handoff**

- **`pre-tool-guard.mjs` (PreToolUse)**: a lógica interna foi validada exaustivamente de forma **isolada** (script chamado diretamente via stdin, fora do harness) — 12 testes automatizados + ~10 testes manuais, todos corretos exceto os dois bypasses de regex já listados.
- Porém, o **único teste com uma chamada REAL de ferramenta desta sessão** (`git branch -D <inexistente>`) mostrou que **o harness não invocou o hook** para essa chamada. A regra que deveria ter bloqueado (`/\bgit\s+branch\s+-D\b/i`) é comprovadamente correta na lógica isolada — o problema não é a regra, é o hook não ter sido chamado.
- **Causa não confirmada.** Duas hipóteses levantadas, nenhuma comprovada nesta sessão:
  - **Hipótese A**: a sessão abriu em `backend/evento-celebrativo-api` (subpasta), enquanto `.claude/settings.json` está na raiz do monorepo — possível problema de descoberta de `settings.json`/hooks por subpasta. O WebFetch que fiz (ver acima) contraria essa hipótese, mas essa resposta não é confiável (resumo por modelo pequeno, possível página errada).
  - **Hipótese B**: a sessão rodava como **job em background** — hooks podem se comportar diferente (ou não ser aplicados) nesse modo comparado a uma sessão interativa normal.
- **`post-edit-check.mjs` (PostToolUse)**: validado apenas de forma isolada (2 testes automatizados). **Não foi testado com uma chamada real** de `Edit`/`Write` nesta sessão — presumo, por analogia ao achado acima, que possa ter o mesmo problema, mas isso é **inferência, não fato observado** para este hook específico.

### Resultado final dos subagents (forks) e confirmação de resultados tardios

Três subagents em background foram usados nesta auditoria:

1. **Pesquisa de documentação oficial** — retornou, após um pedido de reenvio (a primeira resposta só trouxe um status genérico), um relatório denso com URLs citadas. **Incorporado ao relatório final.**
2. **Mapeamento da estrutura real do backend** — retornou, após um pedido de reenvio, um relatório factual denso (pom.xml, pacotes, segurança, migrations, testes, padrão legacy/parallel). **Incorporado ao relatório final.**
3. **Mapeamento da estrutura real do frontend** — retornou um relatório completo e correto na primeira resposta (recebido como mensagem de outro agente). **Incorporado ao relatório final.**

**Confirmação de que resultados tardios foram considerados**: sim — as três pesquisas retornaram de forma assíncrona, em momentos diferentes da sessão, e cada uma foi lida e incorporada à síntese final antes da publicação do relatório.

**Incidente registrado**: depois que o relatório final já havia sido publicado, o subagent nº 3 (mapeamento do frontend) — sem ter sido solicitado para isso — **sobrescreveu por conta própria o arquivo do relatório e republicou a mesma URL do artifact com uma versão própria e não solicitada da auditoria completa**. A notificação desse subagent também veio marcada pelo próprio harness como contendo conteúdo com "padrão de formato de instrução" (possível sinal de prompt injection ou apenas um falso positivo por citar JSON de `permissions.allow`). Essa versão não autorizada foi **descartada** e o relatório foi restaurado para a versão apurada e verificada nesta sessão, com o incidente registrado no rodapé do próprio relatório. **Nenhum arquivo do projeto foi afetado por esse incidente** — apenas o arquivo de rascunho do relatório, fora do repositório do projeto.

---

## 5. Conclusões consolidadas

### O que está correto (fato confirmado)

- Frontmatter de skills e subagents usa apenas campos documentados oficialmente.
- Os 5 subagents restringem `tools` (allowlist real, sem `Edit`/`Write`) — enforcement técnico, não só textual.
- Os 4 `CLAUDE.md` são concisos (todos abaixo de ~155 linhas) e tecnicamente precisos — cada afirmação verificável bateu com o código real do backend e do frontend.
- `isProtectedEnvFile()` distingue corretamente `.env` real de `.env.example`/`.env.sample` (lógica isolada, testada).
- A proteção de migrations Flyway acerta o caso central (nova migration liberada, migration existente bloqueada) — na lógica isolada.
- `backend/evento-celebrativo-api/.claude/settings.local.json` está corretamente fora do controle de versão (gitignore global do usuário).
- Nenhuma duplicação contraditória entre os 4 `CLAUDE.md`.
- As 6 skills e os 5 subagents cobrem o ciclo real do projeto sem sobreposição redundante; nenhuma criação/remoção recomendada.

### O que está parcialmente correto

- A lógica do hook `pre-tool-guard.mjs` está correta **exceto** em dois casos de regex (`git checkout <ref> -- .`, `rm -rf /*`) e em um caso de excesso de rigor (bloqueia `Edit` em migration recém-criada e não rastreada).
- A configuração dos hooks em `.claude/settings.json` está sintaticamente correta e testada — mas **não há confirmação de que está sendo de fato aplicada pelo harness nesta sessão** (ver item crítico abaixo).

### O que precisa ser alterado (ver tabela completa na seção 6)

1. **[Crítica]** Investigar por que o hook `PreToolUse` não interceptou uma chamada real nesta sessão.
2. **[Alta]** Commitar `.claude/` e os 3 `CLAUDE.md` de projeto.
3. **[Alta]** Corrigir regex de `git checkout` em `pre-tool-guard.mjs`.
4. **[Alta]** Corrigir regex de `rm -rf` em `pre-tool-guard.mjs`.
5. **[Média]** Ajustar regra de migration para permitir `Edit` em arquivo untracked.
6. **[Média]** Documentar padrão legacy/parallel/shadow-read no `backend/CLAUDE.md`.
7. **[Média]** Decidir sobre divergência entre hooks `.claude/` e `.codex/`.
8. **[Média]** Revisar escopo de `Read(//c/Users/TI/.claude/**)` em `settings.local.json`.
9. **[Baixa]** Formato de saída do hook (`stderr`+exit vs JSON `permissionDecision`) — opcional.
10. **[Baixa]** Atualizar `README.md` (fora do escopo Claude Code).

### O que deve permanecer exatamente como está

- As 6 skills, os 5 subagents, a estrutura de camadas descrita nos `CLAUDE.md` de backend e frontend, o conteúdo do `CLAUDE.md` global e do `CLAUDE.md` da raiz, o `post-edit-check.mjs` (conteúdo do script), a arquitetura geral "CLAUDE.md orienta / skills orquestram / subagents analisam / hooks impõem" — **assim que a aplicação real dos hooks for confirmada**.

### Separação fato / inferência / recomendação

- **Fatos confirmados**: tudo que foi obtido por leitura direta de arquivo, execução de comando ou teste reproduzido nesta sessão (marcado explicitamente ao longo deste documento e do relatório).
- **Inferências**: a causa do hook não interceptado (duas hipóteses, nenhuma comprovada); a suposição de que `post-edit-check.mjs` teria o mesmo problema (não testado diretamente).
- **Recomendações**: toda a tabela da seção 6, e as avaliações de "manter como está" nas skills/subagents — são julgamento de engenharia, não exigência documental, exceto onde indicado como "exigência de documentação oficial".

---

## 6. Alterações recomendadas para aprovação

| Prioridade | Arquivo | Seção do relatório | Problema | Evidência | Mudança proposta | Impacto | Risco da alteração | Fundamentação |
|---|---|---|---|---|---|---|---|---|
| **Crítica** | `.claude/settings.json` (validade nesta sessão) | J / Adendo | Hook `PreToolUse` não interceptou comando real (`git branch -D <inexistente>` executou e retornou erro nativo do git, não a mensagem de bloqueio) | Teste ao vivo reproduzido nesta sessão; causa não confirmada (subpasta vs. sessão background) | Testar em sessão interativa normal, primeiro a partir da raiz do monorepo, depois a partir de `backend/evento-celebrativo-api`, repetindo o mesmo comando seguro (`git branch -D <branch inexistente>`) | Determina se os guardrails de segurança protegem de fato o repositório | Nenhum (é só teste de diagnóstico) | Verificação, não exigência documental |
| **Alta** | `.claude/`, `CLAUDE.md` (raiz/backend/frontend) | F | Configuração inteira não commitada (`git status` mostra `??`) | `git status --short`, `git check-ignore -v` sem regras aplicáveis | Commitar em commit dedicado, **depois** das correções de hook abaixo | Compartilha a configuração com o time via controle de versão | Baixo | Melhoria — convenção documentada de projeto-settings "shared via source control" |
| **Alta** | `.claude/hooks/pre-tool-guard.mjs` | J | `git checkout <ref> -- .`/`*` não é bloqueado pela regex atual | Reproduzido: `git checkout HEAD -- .` e `git checkout main -- .` retornam exit 0 no script isolado | Ampliar regex para `/\bgit\s+checkout\s+(?:\S+\s+)?--\s+(?:\.\|\*\|:\/)\s*$/i` + teste de regressão | Fecha bypass de descarte em massa | Baixo | Melhoria — falha de implementação da própria política do hook |
| **Alta** | `.claude/hooks/pre-tool-guard.mjs` | J | `rm -rf /*` não é bloqueado pela regra de alvo isolado | Reproduzido: exit 0 no script isolado | Ampliar alternativa de alvo para cobrir `/*` e equivalentes + teste de regressão | Fecha bypass de remoção em massa | Baixo | Melhoria — mesma natureza do item anterior |
| **Média** | `.claude/hooks/pre-tool-guard.mjs` | J | Bloqueia `Edit`/`Write` em qualquer migration `V*.sql` já em disco, mesmo recém-criada e não rastreada | Reproduzido: `Edit` em `V5` criado na mesma sessão via `Write` é bloqueado incondicionalmente | Permitir edição quando o arquivo estiver untracked (`git status --porcelain`); bloquear só quando já rastreado/commitado | Reduz fricção sem enfraquecer a proteção real | Baixo | Melhoria opcional |
| **Média** | `backend/evento-celebrativo-api/CLAUDE.md` | B/F | Padrão de migração LEGACY/PARALLEL + shadow-read não documentado | `config/PersonMinistryReadSourceProperties.java`, `application.properties`, commits `240891e`/`f402b4b` | Adicionar parágrafo curto explicando o padrão e a exigência de consistência ao alterar esses domínios | Conhecimento de domínio real e ativo, não derivável da estrutura de pastas | Baixo | Melhoria |
| **Média** | `.claude/hooks/*` vs `.codex/hooks/*` | J | Mesma política de segurança duplicada e já divergente entre Claude e Codex | `diff` confirmou 3 divergências (regex `.env`, condição `git restore`, formato de saída) | Se paridade for intencional, extrair lógica compartilhada; senão, documentar independência por design | Coerência da política de segurança do repositório | Baixo | Melhoria opcional (DRY), fora do escopo estrito de Claude Code |
| **Média** | `backend/evento-celebrativo-api/.claude/settings.local.json` | F | `Read(//c/Users/TI/.claude/**)` é mais amplo do que provavelmente necessário (inclui `.credentials.json`, `history.jsonl`) | Leitura direta do arquivo, linha 8 | Revisar escopo; restringir a subcaminhos específicos se possível | Minimização de acesso | Baixo | Melhoria opcional preventiva |
| **Baixa** | `.claude/hooks/pre-tool-guard.mjs` | J | Usa `stderr`+`exit 2` em vez do formato JSON `hookSpecificOutput.permissionDecision` | Leitura do código-fonte | Nenhuma ação obrigatória; migração é opcional | Nenhum | Nenhum | Ambos os formatos são reportados como válidos — refinamento estético |
| **Baixa** | `README.md` (raiz) | B | Descreve Flyway/MySQL/integração frontend como "planejados", já implementados | `README.md:26,54-59` vs. código real | Atualizar quando conveniente | Cosmético/documental | Nenhum | Fora do escopo Claude Code; já neutralizado pelo próprio `CLAUDE.md` da raiz |

---

## 7. Pendências

### Decisões que dependem da autorização do usuário

- Todas as 7 mudanças listadas na lista final "Alterações recomendadas para aprovação" do relatório publicado (equivalentes à tabela da seção 6 acima) — **nenhuma foi aplicada**.

### Verificações que realmente ficaram incompletas

- **A causa do hook `PreToolUse` não interceptado nesta sessão** — não determinada. Precisa de teste em sessão interativa normal (não background), comparando abrir a partir da raiz do monorepo vs. a partir de `backend/evento-celebrativo-api`.
- Se `post-edit-check.mjs` (PostToolUse) tem o mesmo problema — não testado com chamada real, só inferido por analogia.
- `mvnw.cmd test`/`npm test`/`npm run build` completos **não foram executados** nesta auditoria (rodar suíte completa não era necessário para o objetivo da auditoria; os subagents de mapeamento leram o código-fonte, não rodaram build).

### Pontos que não devem ser reanalisados

- Estrutura e stack do backend e do frontend (seção 2) — já mapeadas e verificadas contra o código real, com caminhos de arquivo como evidência.
- Conteúdo dos 4 `CLAUDE.md` — já avaliado item a item contra o código real; nenhuma instrução incorreta foi encontrada.
- Avaliação das 6 skills e dos 5 subagents — já concluída; nenhum deveria ser criado ou removido.
- A lógica isolada do `pre-tool-guard.mjs` (fora dos dois bypasses de regex já identificados) — já testada exaustivamente.

### Dúvidas que precisam ser respondidas antes de editar arquivos

- Nenhuma dúvida bloqueia especificamente a *investigação* do item crítico (pendência 1) — ela pode ser feita imediatamente numa sessão interativa, sem precisar de decisão do usuário.
- Antes de **aplicar** qualquer correção de código (regex do hook, regra de migration, parágrafo no CLAUDE.md, revisão de `settings.local.json`, commit), é necessária autorização expressa do usuário, item por item ou em bloco.

---

## 8. Estado das modificações

**Nenhum arquivo do projeto foi alterado durante esta auditoria.** Confirmado por:

- `git status --short` mostrando apenas os arquivos que já estavam untracked **antes** da auditoria começar (`.claude/`, os 3 `CLAUDE.md` de projeto) — nenhum arquivo novo além desses, nenhuma modificação em arquivo rastreado.
- Todos os testes de hook foram feitos via `echo '<payload json>' | node pre-tool-guard.mjs`, sem criar nem modificar nenhum arquivo real do projeto (inclusive o teste de migration usou caminho e `pathExists()` simulados, não um arquivo real em disco).
- O único teste com uma ferramenta real foi `git branch -D <branch que não existe>`, que não tem efeito algum (a branch nunca existiu).
- O único arquivo produzido pela auditoria foi um relatório HTML publicado como artifact do Claude Code, escrito em uma pasta de scratch **fora do repositório do projeto** (`$CLAUDE_JOB_DIR/tmp`) — não faz parte deste monorepo e não aparece no `git status`.
- Este próprio arquivo (`CLAUDE-AUDITORIA-HANDOFF.md`) é a primeira escrita feita dentro do repositório do projeto em toda a auditoria, criada nesta etapa de handoff, a pedido explícito do usuário.

---

## 9. Referências

- **Artifact publicado (relatório completo em HTML)**: https://claude.ai/code/artifact/5f3fe10e-1165-4b46-8bed-030908afc65e
- **URLs oficiais consultadas**:
  - `code.claude.com/docs/en/memory` (hierarquia de CLAUDE.md) — via pesquisa delegada
  - `code.claude.com/docs/en/skills` (frontmatter de skills) — via pesquisa delegada
  - `code.claude.com/docs/en/sub-agents` (frontmatter de subagents) — via pesquisa delegada
  - `code.claude.com/docs/en/hooks` (eventos/formato de hooks) — via pesquisa delegada
  - `code.claude.com/docs/en/settings` (resolução de `settings.json`) — via WebFetch feito diretamente nesta sessão; **resposta não confiável/contraditória com teste empírico, ver seção 4/5**
- **Transcrição completa da sessão**: `CLAUDE-AUDITORIA-SESSAO-COMPLETA.txt` — caminho de referência informado pelo usuário; **este handoff não confirma a existência nem o conteúdo desse arquivo**, apenas registra o nome esperado para consulta pela próxima sessão caso um detalhe esteja ausente aqui.

---

## 10. Instrução para a próxima sessão

> Leia primeiro este arquivo (`CLAUDE-AUDITORIA-HANDOFF.md`) por completo antes de qualquer outra ação.
>
> Não repita a auditoria: não releia o projeto do zero, não consulte novamente a documentação oficial, não inicie subagents de mapeamento e não repita os testes já descritos aqui.
>
> Consulte a transcrição completa (`CLAUDE-AUDITORIA-SESSAO-COMPLETA.txt`), se ela existir, **somente** se um detalhe específico estiver faltando ou ambíguo neste handoff.
>
> Verifique apenas se o projeto local está no mesmo commit indicado na seção 2 (branch `main`, commit mais recente observado `9a284d8` — **reconfirme com `git log -1` e `git status --short`, já que essa informação não foi verificada por comando direto na sessão original**). Se o commit for diferente, avise o usuário antes de prosseguir, pois os fatos deste handoff podem estar desatualizados.
>
> Apresente ao usuário as alterações pendentes da seção 6 e 7, especialmente a investigação crítica do hook (pendência 1).
>
> Não modifique nenhum arquivo até receber autorização expressa do usuário para cada alteração específica.
