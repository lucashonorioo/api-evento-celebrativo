---
name: implement-frontend-feature
description: Implemente feature, correção ou refatoração no frontend Angular/TypeScript do Evento Celebrativo; use para rotas, componentes, HTTP, models, auth, formulários, CSS ou testes frontend.
---

# Implementar frontend

1. Leia `AGENTS.md` raiz + frontend; confirme requisito/aceite e comportamento atual.
2. Confirme contratos no backend/OpenAPI/testes e localize UI, estado, HTTP, auth e testes afetados; use exploração somente leitura quando ampla.
3. Implemente a menor mudança coerente respeitando arquitetura Angular, TypeScript estrito, RxJS/signals, contratos, segurança, UX, acessibilidade, responsividade e desempenho definidos no AGENTS especializado.
4. Atualize testes proporcionais ao risco e valide interação visual quando necessário/possível.
5. Execute `validate-project`.
6. Execute `review-change` após a última alteração. Em `CHANGES_REQUIRED`, corrija findings da tarefa, revalide e revise novamente.
7. Conclua somente com `PASS`/`PASS WITH NOTES`.

Prefira comandos finitos. Se iniciar dev server, siga `.ai/runtime/PROCESS_LIFECYCLE.md` e encerre o processo iniciado pelo agente.

Reporte comportamento, arquivos, validações, UX/a11y relevante, Engineering Review e limitações.
