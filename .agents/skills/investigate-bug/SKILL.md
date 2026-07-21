---
name: investigate-bug
description: Investigue bugs, exceções, comportamento incorreto ou testes falhando com abordagem orientada a evidências. Use antes de corrigir quando a causa raiz ainda não estiver comprovada; não use para features novas bem especificadas.
---

# Investigar bug

## Objetivo

Encontrar a causa raiz com evidência reproduzível antes de alterar código e produzir a menor correção segura.

## Workflow

1. Leia os AGENTS aplicáveis e identifique a área afetada.
2. Reúna a mensagem completa, stack trace, request, dados de entrada e comportamento esperado quando disponíveis.
3. Reproduza com o teste ou comando mais específico possível.
4. Trace o fluxo real de execução e localize o primeiro ponto em que estado observado diverge do esperado.
5. Procure mudanças recentes e padrões equivalentes no projeto.
6. Formule poucas hipóteses ordenadas por probabilidade e teste uma de cada vez.
7. Não edite enquanto a causa raiz ainda estiver sustentada apenas por suposição.
8. Quando a causa estiver comprovada, implemente a menor correção coerente.
9. Adicione teste de regressão sempre que viável.
10. Execute validações específicas e depois amplas conforme o risco.

## Delegação

Em bugs complexos, use `codebase_explorer` para mapear o fluxo e um reviewer especializado para validar a hipótese. Não delegue escritas paralelas.

## Saída esperada

Informe:

- sintomas e reprodução;
- causa raiz e evidência;
- correção aplicada;
- teste de regressão;
- comandos executados;
- riscos ou incertezas restantes.
