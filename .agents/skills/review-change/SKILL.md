---
name: review-change
description: Revise branch, PR ou diff do Evento Celebrativo procurando bugs, regressões, segurança, compatibilidade e lacunas de testes. Use para revisão estruturada antes de merge; não use para implementar automaticamente todas as sugestões encontradas.
---

# Revisar alteração

## Escopo

1. Determine a base da comparação, normalmente `main`, sem assumir quando houver ambiguidade material.
2. Leia os AGENTS das áreas afetadas.
3. Inspecione status, commits e diff sem alterar arquivos.
4. Priorize comportamento e risco, não preferências estilísticas.

## Delegação

Para diff relevante, delegue somente as áreas afetadas:

- `codebase_explorer`: mapa do fluxo e impacto;
- `backend_reviewer`: Java/Spring e persistência;
- `frontend_reviewer`: Angular, UX e acessibilidade;
- `test_reviewer`: cobertura e fragilidade;
- `security_reviewer`: auth, autorização, secrets e exposição.

Aguarde todos e remova duplicações. Não use todos os agents em mudanças pequenas.

## Critérios

Procure:

- bug funcional ou regressão;
- contrato quebrado;
- autorização ausente ou excessiva;
- tratamento incorreto de erro;
- concorrência, transação ou integridade;
- incompatibilidade de schema/migration;
- tipagem insegura;
- estado de UI incorreto;
- acessibilidade afetada;
- testes ausentes ou que não exercitam o comportamento real;
- arquivos ou dados sensíveis acidentais.

## Formato dos achados

Ordene por severidade. Cada achado deve incluir:

- severidade;
- arquivo e símbolo/linha quando possível;
- cenário concreto;
- impacto;
- correção sugerida mínima.

Se não houver achados, diga isso explicitamente e registre riscos residuais ou validações não executadas. Não invente problemas para preencher a revisão.
