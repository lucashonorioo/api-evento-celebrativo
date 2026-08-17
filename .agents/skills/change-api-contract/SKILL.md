---
name: change-api-contract
description: Coordene mudanças full stack em endpoint, path, request, response, paginação, status HTTP ou autenticação entre backend Spring e frontend Angular. Use quando um contrato compartilhado muda; não use para implementação interna sem impacto nos consumers.
---

# Alterar contrato da API

## Princípio

Trate o contrato como interface compartilhada. Não atualize apenas um lado e não presuma consumidores.

## Workflow

1. Leia os `AGENTS.md` da raiz, backend e frontend.
2. Localize todos os producers e consumers do contrato:
   - controller e configuração de segurança;
   - DTOs, mapper, service e testes backend;
   - models, serviços HTTP, componentes, formulários e testes frontend;
   - documentação OpenAPI quando existir.
3. Registre o contrato atual e o contrato desejado.
4. Classifique a mudança como compatível ou incompatível.
5. Para breaking change, defina atualização coordenada ou estratégia de transição.
6. Implemente primeiro a fonte de verdade e seus testes.
7. Atualize consumers tipados e estados de erro relevantes.
8. Teste status codes, validação, autorização, serialização e erros.
9. Revise o diff completo para garantir que nenhum consumer permaneceu no contrato antigo.

## Gate de conclusão obrigatório

Mudança de contrato é, no mínimo, risco médio e deve passar por revisão de engenharia depois da última alteração:

1. execute `validate-project` no backend e frontend afetados;
2. execute `review-change` sobre o diff full stack;
3. trate qualquer `CHANGES_REQUIRED` dentro do escopo;
4. repita validações afetadas e a revisão após correções;
5. conclua apenas com `PASS` ou `PASS WITH NOTES`.

Em alteração de autenticação, autorização, exposição pública ou dados sensíveis, trate a mudança como alto risco.

## Entrega

Inclua resumo de antes/depois do contrato, compatibilidade, consumers atualizados, validações executadas, veredito da revisão de engenharia e riscos residuais.
