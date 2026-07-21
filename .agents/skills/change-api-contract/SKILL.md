---
name: change-api-contract
description: Coordene mudanças full stack em endpoint, path, request, response, paginação, status HTTP ou autenticação entre backend Spring e frontend Angular. Use quando um contrato compartilhado muda; não use para implementação interna sem impacto nos consumers.
---

# Alterar contrato da API

## Princípio

Trate o contrato como interface compartilhada. Não atualize apenas um lado e não presuma consumidores.

## Workflow

1. Leia os AGENTS da raiz, backend e frontend.
2. Localize todos os producers e consumers do contrato:
   - controller e configuração de segurança;
   - DTOs, mapper, service e testes backend;
   - models, serviços HTTP, componentes, formulários e testes frontend;
   - documentação OpenAPI quando existir.
3. Registre o contrato atual e o contrato desejado.
4. Classifique a mudança como compatível ou incompatível.
5. Para breaking change, defina atualização coordenada ou estratégia de transição.
6. Implemente primeiro a fonte de verdade e seus testes.
7. Atualize os consumers tipados.
8. Teste status codes, validação, autorização, serialização e erros.
9. Revise o diff completo para garantir que nenhum consumer ficou antigo.

## Delegação

Em mudanças maiores, use em paralelo:

- `codebase_explorer` para localizar dependências;
- `backend_reviewer` para contrato e regra;
- `frontend_reviewer` para consumer e UX;
- `security_reviewer` quando a exposição ou autorização mudar.

Subagents devem apenas analisar. O agente principal integra a alteração.

## Validação

Execute a suíte backend relevante e build/testes frontend. Quando possível, valide um fluxo real ponta a ponta sem expor credenciais.

## Entrega

Inclua uma tabela ou resumo de antes/depois do contrato, compatibilidade, consumers atualizados e validações executadas.
