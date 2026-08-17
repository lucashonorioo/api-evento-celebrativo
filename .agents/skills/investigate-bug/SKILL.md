---
name: investigate-bug
description: Investigue bugs, exceções, comportamento incorreto ou testes falhando com abordagem orientada a evidências. Use antes de corrigir quando a causa raiz ainda não estiver comprovada; não use para features novas bem especificadas.
---

# Investigar bug

## Objetivo

Encontrar a causa raiz com evidência reproduzível antes de alterar código e produzir a menor correção segura.

## Workflow

1. Leia os `AGENTS.md` aplicáveis e identifique a área afetada.
2. Reúna mensagem completa, stack trace, request, dados de entrada e comportamento esperado quando disponíveis.
3. Reproduza com o teste ou comando mais específico possível.
4. Trace o fluxo real e localize o primeiro ponto em que o estado observado diverge do esperado.
5. Procure mudanças recentes e padrões equivalentes no projeto.
6. Formule poucas hipóteses ordenadas por probabilidade e teste uma de cada vez.
7. Não edite enquanto a causa raiz estiver sustentada apenas por suposição.
8. Quando a causa estiver comprovada, implemente a menor correção coerente.
9. Adicione teste de regressão sempre que viável.
10. Execute validações específicas e depois amplas conforme o risco.

## Gate de conclusão obrigatório

Se a investigação resultar em alteração de código, teste, contrato, migration ou configuração executável:

1. execute `validate-project` depois da correção;
2. execute `review-change` depois da última alteração;
3. trate `CHANGES_REQUIRED` antes de concluir;
4. após qualquer ajuste motivado pela revisão, revalide e revise novamente.

## Saída esperada

Informe sintomas e reprodução, causa raiz e evidência, correção aplicada, teste de regressão, comandos executados, veredito da revisão de engenharia e riscos ou incertezas restantes.
