---
name: review-change
description: Revisa branch, pull request ou diff do Evento Celebrativo procurando bugs, regressões, segurança, compatibilidade e lacunas de testes. Use para revisão estruturada antes de merge; não use para implementar automaticamente todos os achados.
context: fork
background: false
---

# Revisar alteração

## Escopo

1. Determine a base da comparação, normalmente `main`, sem assumir quando houver ambiguidade material.
2. Leia os `CLAUDE.md` das áreas afetadas.
3. Inspecione status, commits e diff sem alterar arquivos.
4. Trace o comportamento alterado e seus consumidores.
5. Priorize comportamento e risco, não preferências estilísticas.

## Especialistas

Use apenas os necessários:

- `codebase-explorer`: mapa do fluxo e impacto;
- `backend-reviewer`: Java, Spring, JPA e contratos;
- `frontend-reviewer`: Angular, estado, UX e acessibilidade;
- `test-reviewer`: cobertura e fragilidade;
- `security-reviewer`: autenticação, autorização, secrets e exposição.

Consolide resultados, remova duplicações e não invoque todos em mudança pequena.

## Critérios

Procure:

- bug funcional ou regressão;
- contrato quebrado ou consumer desatualizado;
- autorização ausente, excessiva ou apenas visual;
- tratamento incorreto de erro;
- problema de concorrência, transação, integridade ou migration;
- consulta ou renderização claramente não escalável;
- tipagem insegura ou estado de UI incorreto;
- acessibilidade ou responsividade afetada;
- logging inadequado ou exposição de dados;
- teste ausente, frágil ou que não exercita o comportamento real;
- arquivos, artefatos ou dados sensíveis acidentais.

## Formato

Ordene achados por severidade. Para cada um, informe arquivo e símbolo/linha quando possível, cenário concreto, impacto, evidência e correção mínima.

Se não houver achados, declare isso e registre riscos residuais ou validações não executadas. Não invente problemas.
