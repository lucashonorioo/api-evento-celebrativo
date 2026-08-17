---
name: review-change
description: Executa revisão independente de engenharia sobre branch, PR ou diff do Evento Celebrativo, verificando correção, arquitetura, contratos, dados, segurança, desempenho, testes e regressões antes da conclusão ou merge. É somente leitura e não implementa os próprios achados.
---

# Revisar alteração

## Política obrigatória

Leia `.ai/review/ENGINEERING_REVIEW.md` antes de iniciar. Ela define critérios, severidade, risco, veredito e formato comum de revisão para Codex e Claude Code.

Leia também os `AGENTS.md` da raiz e das áreas afetadas. Em caso de conflito, instruções mais específicas da área prevalecem para detalhes técnicos, sem reduzir o rigor do gate de revisão.

## Escopo

1. Determine a base correta da comparação. Use a base informada pelo usuário, branch/PR ou merge-base quando houver contexto suficiente; não assuma `main` quando isso puder distorcer o diff.
2. Para working tree, considere `git status --short`, diff staged, diff unstaged e arquivos untracked pertencentes à tarefa.
3. Identifique backend, frontend, contrato, persistência, segurança, testes e configuração afetados.
4. Leia o diff completo antes de concluir sobre arquivos isolados.
5. Trace consumidores e dependências diretas quando forem necessários para comprovar o comportamento alterado.
6. Não edite arquivos durante a revisão.

## Revisão

Aplique todas as dimensões relevantes de `.ai/review/ENGINEERING_REVIEW.md`, com atenção especial a:

- requisito, invariantes e regressões;
- separação de responsabilidades, coesão, acoplamento e aderência à arquitetura existente;
- contratos HTTP e compatibilidade backend/frontend;
- transações, integridade, migrations, JPA, queries e concorrência;
- autenticação, autorização e exposição de dados;
- tratamento de erro, logs e resiliência;
- desempenho e escalabilidade apenas quando houver risco concreto;
- manutenibilidade sem abstração ou refatoração especulativa;
- cobertura e qualidade dos testes;
- tipagem, estado, RxJS, UX, acessibilidade e responsividade quando houver frontend.

Não trate preferência estética como defeito. Não exija pattern, camada, abstração ou dependência nova sem benefício concreto e demonstrável.

## Profundidade por risco

- **Baixo:** faça a revisão completa diretamente sobre o diff e o fluxo local.
- **Médio:** amplie a leitura para dependências/consumidores diretos e valide interações entre camadas afetadas.
- **Alto:** revise explicitamente todas as superfícies críticas envolvidas, incluindo segurança, dados, contratos, concorrência e estratégia de testes conforme aplicável.

Se o ambiente disponibilizar especialistas ou subagents de revisão, use somente os necessários e consolide os resultados. A ausência deles não reduz a responsabilidade desta skill de executar a revisão completa.

## Achados e veredito

Classifique cada achado como `BLOCKER`, `HIGH`, `MEDIUM` ou `LOW` conforme a política comum.

Cada achado deve conter:

- arquivo e símbolo/linha quando possível;
- evidência observada;
- cenário concreto de falha ou custo;
- impacto;
- correção mínima sugerida.

Finalize com exatamente um veredito:

- `PASS`;
- `PASS WITH NOTES`;
- `CHANGES_REQUIRED`.

Se não houver achados acionáveis, diga isso explicitamente. Registre validações não executadas e riscos residuais sem transformá-los automaticamente em defeitos.
