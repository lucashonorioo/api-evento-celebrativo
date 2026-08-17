---
name: review-change
description: Executa revisão independente de engenharia sobre branch, pull request ou diff do Evento Celebrativo, verificando correção, arquitetura, contratos, dados, segurança, desempenho, testes e regressões antes da conclusão ou merge. É somente leitura e não implementa os próprios achados.
context: fork
background: false
---

# Revisar alteração

## Política obrigatória

Leia `.ai/review/ENGINEERING_REVIEW.md` antes de iniciar. Ela é a fonte comum de critérios, risco, severidade, veredito e formato para Codex e Claude Code.

Leia também os `CLAUDE.md` da raiz e das áreas afetadas. Use o Graphify conforme as instruções existentes para restringir a exploração quando ele estiver disponível.

## Escopo

1. Determine a base correta da comparação. Use a base informada pelo usuário, branch/PR ou merge-base quando houver contexto suficiente; não assuma `main` se isso puder distorcer o diff.
2. Para working tree, considere `git status --short`, diff staged, diff unstaged e arquivos untracked pertencentes à tarefa.
3. Leia o diff completo antes de concluir sobre arquivos isolados.
4. Trace o fluxo alterado e consumidores/dependências diretas quando necessário para comprovar impacto.
5. Classifique a mudança como risco baixo, médio ou alto pela política comum.
6. Não edite arquivos durante a revisão.

## Especialistas

A revisão principal continua responsável pelo veredito. Delegue somente quando houver ganho real de profundidade:

- `backend-reviewer`: backend Java/Spring, arquitetura da camada, contratos, transações, JPA e persistência;
- `frontend-reviewer`: Angular/TypeScript, arquitetura frontend, estado, RxJS, UX, acessibilidade e responsividade;
- `test-reviewer`: adequação, lacunas e fragilidade dos testes;
- `security-reviewer`: autenticação, autorização, dados sensíveis e superfície de ataque;
- `codebase-explorer`: apenas quando o fluxo/impacto ainda não estiver suficientemente mapeado.

Para risco baixo, faça a revisão diretamente quando especialistas não forem necessários. Para risco médio/alto, use os especialistas das áreas materialmente afetadas. Não invoque todos por padrão.

Aguarde resultados, remova duplicações e valide cada achado contra o diff/código antes de incorporá-lo ao veredito.

## Critérios

Aplique todas as dimensões relevantes de `.ai/review/ENGINEERING_REVIEW.md`. Em especial, verifique:

- requisito, invariantes, casos de borda e regressões;
- separação de responsabilidades, coesão, acoplamento e aderência à arquitetura existente;
- contratos e compatibilidade entre backend e frontend;
- transações, integridade, migrations, queries, JPA e concorrência;
- autenticação, autorização, exposição e minimização de dados;
- tratamento de erro, logs, resiliência e observabilidade;
- desempenho e escalabilidade somente com risco concreto;
- manutenibilidade sem abstração/refatoração especulativa;
- testes que comprovem o comportamento real;
- tipagem, estado, RxJS, UX e acessibilidade quando houver frontend.

## Achados e veredito

Classifique achados como `BLOCKER`, `HIGH`, `MEDIUM` ou `LOW`.

Para cada achado, informe arquivo e símbolo/linha quando possível, evidência observada, cenário concreto, impacto e correção mínima.

Finalize com exatamente um veredito:

- `PASS`;
- `PASS WITH NOTES`;
- `CHANGES_REQUIRED`.

Se não houver achados acionáveis, declare isso explicitamente e registre somente validações não executadas e riscos residuais reais.
