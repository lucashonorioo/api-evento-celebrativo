---
name: investigate-bug
description: Investigue bug, exceção, comportamento incorreto ou teste falhando por evidências; use quando a causa raiz ainda não estiver comprovada.
---

# Investigar bug

1. Leia os `AGENTS.md` aplicáveis e reúna erro/stack/input/esperado disponíveis.
2. Reproduza com o teste/comando mais específico.
3. Trace o fluxo até o primeiro ponto onde observado diverge do esperado; procure mudança recente/padrão equivalente.
4. Teste poucas hipóteses ordenadas; não edite enquanto a causa for apenas suposição.
5. Com causa comprovada, faça a menor correção segura e teste de regressão quando viável.
6. Execute `validate-project`; se houve mudança de engenharia, execute `review-change`.
7. Em `CHANGES_REQUIRED`, corrija, revalide e revise novamente.

Se iniciar processo longo para reproduzir, siga `.ai/runtime/PROCESS_LIFECYCLE.md`.

Reporte sintoma/reprodução, causa + evidência, correção, regressão, validações, Engineering Review e incertezas reais.
