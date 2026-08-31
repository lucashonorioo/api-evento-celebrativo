# Evento Celebrativo — regras do monorepo

## Fonte de verdade e escopo

Este repositório contém:

```text
backend/evento-celebrativo-api/        API Java/Spring Boot
frontend-web/evento-celebrativo-web/   aplicação Angular
```

Código, configuração, migrations e testes reais são a fonte de verdade. Antes de editar backend ou frontend, leia o `AGENTS.md` da área correspondente. Em mudança full stack, leia ambos.

- Não altere a outra aplicação em tarefa exclusivamente backend/frontend sem necessidade do requisito.
- Mudança de contrato HTTP é coordenada entre producer e consumers; use `change-api-contract`.
- Preserve alterações locais e evite mudanças sem relação com a tarefa.

## Fluxo obrigatório

1. Confirme requisito, critérios de aceite, escopo e comportamento atual.
2. Localize implementação, testes, contratos e dependências diretamente afetados; procure padrão equivalente antes de criar outro.
3. Avalie segurança, dados, compatibilidade, desempenho e operação quando aplicáveis.
4. Implemente o menor conjunto coerente de alterações e atualize testes relevantes.
5. Execute `validate-project` com validações proporcionais ao risco.
6. Depois da última alteração relevante, execute `review-change`.
7. Se houver `CHANGES_REQUIRED`, corrija apenas achados acionáveis da tarefa, revalide e abra nova revisão.
8. Conclua somente com `PASS` ou `PASS WITH NOTES`.

Testes/build passando não substituem a revisão independente.

## Regras transversais

- O backend é a fonte definitiva de autenticação, autorização, regras de negócio e persistência.
- O frontend não inventa endpoints, campos, roles, paginação ou status HTTP.
- Preserve compatibilidade de contratos em uso; breaking changes exigem atualização coordenada ou estratégia de transição.
- Nunca inclua secrets, senhas, tokens ou dados sensíveis em código, fixtures, logs ou documentação.
- Autorização é server-side; ocultação de UI não é controle de segurança.
- Não edite migration Flyway versionada que já possa ter sido aplicada; crie migration incremental.
- Não execute limpeza destrutiva de banco/repositório sem solicitação explícita.
- Não reduza autenticação, autorização, validações ou testes para fazer uma tarefa passar.
- Não introduza dependência, camada, pattern ou refatoração ampla sem benefício concreto.

## Git

Sem solicitação explícita, não faça commit, push, merge, rebase, PR nem exclua branch. Não descarte trabalho existente. Quando solicitado, use branches `feature/`, `fix/`, `test/`, `chore/`, `docs/`, `refactor/` ou `perf/`, com objetivo claro.

## Skills e subagents

- bug/teste falhando: `investigate-bug`;
- backend: `implement-backend-feature`;
- frontend: `implement-frontend-feature`;
- contrato API: `change-api-contract`;
- validação: `validate-project`;
- revisão: `review-change`.

Use subagents somente quando a especialização ou divisão de contexto melhorar a tarefa. Exploração e reviewers são somente leitura; o agente principal integra alterações.

A política canônica da revisão é `.ai/review/ENGINEERING_REVIEW.md`. Alterações de implementação e da infraestrutura de IA versionada (`.ai/`, `.claude/`, `.codex/`, `.agents/`, `AGENTS.md`, `CLAUDE.md`) exigem esse gate.

## Processos de desenvolvimento

Prefira comandos finitos (`test`, `build`, validações específicas). Não inicie servidor backend/frontend apenas para validar algo que um comando finito comprova.

Se precisar iniciar `spring-boot:run`, `npm start`, `ng serve` ou outro processo de longa duração, leia `.ai/runtime/PROCESS_LIFECYCLE.md`, registre o PID/process tree iniciado pelo agente e encerre-o antes da conclusão. Nunca finalize processos Java/Node do usuário por nome global.

## Definição de concluído

A tarefa só termina quando:

- requisito/aceite estão atendidos sem mudança fora do escopo;
- contratos, segurança, dados e boas práticas aplicáveis foram preservados;
- testes relevantes foram atualizados e validações reais reportadas;
- `git diff --check` está limpo e o diff não contém artefatos, logs temporários ou segredos;
- nenhum processo de longa duração iniciado pelo agente permanece ativo;
- Engineering Review está em `PASS` ou `PASS WITH NOTES`, sem `MEDIUM/HIGH/BLOCKER` acionável da tarefa;
- a resposta final informa o que foi alterado, validado, não validado e o veredito.

## Graphify

Use Graphify para restringir exploração quando a tarefa envolver arquitetura, dependências, impacto, símbolos ou localização ampla. Nesse caso, leia `.ai/graphify/GRAPHIFY_POLICY.md` antes da consulta. Não carregue essa política por rotina em tarefas locais simples.

Quando o usuário invocar `$graphify`/`/graphify`, siga a política canônica; no Claude use a Skill instalada e no Codex use o CLI disponível.
