---
name: investigate-bug
description: Investiga bugs, exceções, comportamento incorreto ou testes falhando com abordagem orientada a evidências. Use antes de corrigir quando a causa raiz ainda não estiver comprovada; não use para feature nova bem especificada.
---

# Investigar bug

Encontre a causa raiz com evidência reproduzível antes de alterar código e produza a menor correção segura.

## Fluxo

1. Leia os `CLAUDE.md` aplicáveis e identifique a área afetada.
2. Reúna mensagem completa, stack trace, request, entrada e comportamento esperado disponíveis.
3. Reproduza com o teste ou comando mais específico possível.
4. Trace o fluxo real e encontre o primeiro ponto em que o estado observado diverge do esperado.
5. Procure mudanças recentes e padrões equivalentes no projeto.
6. Formule poucas hipóteses ordenadas por probabilidade e teste uma por vez.
7. Não edite enquanto a causa estiver sustentada apenas por suposição.
8. Com a causa comprovada, implemente a menor correção coerente.
9. Adicione teste de regressão quando viável.
10. Execute validações específicas e depois amplas conforme o risco.

## Gate de conclusão obrigatório

Se a investigação resultar em alteração de código, teste, contrato, migration ou configuração executável:

1. use `validate-project` depois da correção;
2. use `review-change` depois da última alteração;
3. trate `CHANGES_REQUIRED` antes de concluir;
4. após qualquer ajuste motivado pela revisão, revalide e revise novamente.

## Saída

Informe sintomas, reprodução, causa raiz, evidência, correção, teste de regressão, comandos executados, veredito da revisão de engenharia e incertezas restantes.
