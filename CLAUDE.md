# Evento Celebrativo — regras do monorepo

## Fonte de verdade e escopo

```text
backend/evento-celebrativo-api/        API Java/Spring Boot
frontend-web/evento-celebrativo-web/   aplicação Angular
```

Código, configuração, migrations e testes reais são a fonte de verdade. Os `CLAUDE.md` aninhados acrescentam as regras da área quando seus arquivos são lidos.

- Não altere a outra aplicação em tarefa exclusivamente backend/frontend sem necessidade do requisito.
- Mudança de contrato HTTP é coordenada entre producer e consumers; use `change-api-contract`.
- Preserve alterações locais e evite mudanças sem relação com a tarefa.

## Fluxo obrigatório

1. Confirme requisito, critérios de aceite, escopo e comportamento atual.
2. Localize implementação, testes, contratos e dependências diretamente afetados; procure padrão equivalente antes de criar outro.
3. Avalie segurança, dados, compatibilidade, desempenho e operação quando aplicáveis.
4. Implemente o menor conjunto coerente e atualize testes relevantes.
5. Execute `validate-project`.
6. Depois da última alteração relevante, execute `review-change`.
7. Em `CHANGES_REQUIRED`, corrija achados acionáveis da tarefa, revalide e revise novamente.
8. Conclua somente com `PASS` ou `PASS WITH NOTES`.

Testes/build passando não substituem revisão independente.

## Regras transversais

- Backend é a fonte definitiva de autenticação, autorização, domínio e persistência; frontend não inventa contratos.
- Preserve compatibilidade; breaking change exige atualização coordenada ou transição explícita.
- Nunca inclua secrets, senhas, tokens ou dados sensíveis em código, fixtures, logs ou documentação.
- Autorização é server-side; UI não substitui permissão.
- Migration Flyway versionada/aplicável é imutável; crie nova migration incremental.
- Não reduza segurança, validações ou testes para obter resultado verde.
- Não introduza dependência, camada, pattern ou refatoração ampla sem benefício concreto.
- Sem pedido explícito, não faça commit, push, merge, rebase, PR nem descarte mudanças.

## Skills, subagents e review

Use a Skill correspondente ao trabalho; use subagents somente quando especialização/divisão de contexto trouxer benefício. Reviewers e exploração são somente leitura; a integração final é do agente principal.

A política canônica é `.ai/review/ENGINEERING_REVIEW.md`. Mudanças em código ou infraestrutura de IA (`.ai/`, `.claude/`, `.codex/`, `.agents/`, `AGENTS.md`, `CLAUDE.md`) exigem o mesmo gate.

## Processos de desenvolvimento

Prefira comandos finitos. Se for realmente necessário iniciar `spring-boot:run`, `npm start`, `ng serve` ou processo equivalente, leia `.ai/runtime/PROCESS_LIFECYCLE.md`, capture o PID/process tree iniciado pelo agente e encerre-o antes da conclusão. Nunca mate Java/Node globalmente.

## Definição de concluído

- requisito/aceite atendidos e escopo preservado;
- contratos, segurança, dados e boas práticas aplicáveis preservados;
- testes/validações reais reportados;
- `git diff --check` limpo; sem artefatos, debug ou segredos;
- nenhum processo longo iniciado pelo agente permanece ativo;
- Engineering Review em `PASS`/`PASS WITH NOTES`, sem `MEDIUM+` acionável da tarefa;
- resposta final registra alterações, validações, limitações e veredito.

## Graphify

Quando arquitetura, dependências, impacto, símbolos ou exploração ampla justificarem Graphify, leia `.ai/graphify/GRAPHIFY_POLICY.md` antes de usá-lo. Não carregue essa política em tarefa local simples. `/graphify` usa a Skill instalada.
