---
name: implement-backend-feature
description: Implemente feature, correção ou refatoração no backend Java/Spring do Evento Celebrativo; use para mudanças em controller, DTO, service, repository, entidade, segurança, migration ou testes backend.
---

# Implementar backend

1. Leia `AGENTS.md` raiz + backend; confirme requisito/aceite e comportamento atual.
2. Localize o fluxo e testes diretamente afetados; para tarefa ampla, use exploração somente leitura.
3. Implemente a menor mudança coerente respeitando arquitetura, contratos, transações, dados, segurança, desempenho e boas práticas definidas no AGENTS especializado.
4. Atualize testes proporcionais ao risco; bug deve ter regressão quando viável.
5. Execute `validate-project`.
6. Execute `review-change` após a última alteração. Em `CHANGES_REQUIRED`, corrija apenas findings da tarefa, revalide e revise novamente.
7. Conclua somente com `PASS`/`PASS WITH NOTES`.

Prefira comandos finitos. Se iniciar servidor, siga `.ai/runtime/PROCESS_LIFECYCLE.md` e encerre o processo iniciado pelo agente.

Reporte comportamento, arquivos relevantes, decisões, dados/migrations, validações, Engineering Review e limitações reais.
