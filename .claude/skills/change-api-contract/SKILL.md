---
name: change-api-contract
description: Coordena mudanças full stack em endpoints, paths, requests, responses, paginação, status HTTP ou autenticação entre o backend Spring e o frontend Angular. Use quando um contrato compartilhado mudar; não use para implementação interna sem impacto nos consumidores.
---

# Alterar contrato da API

Trate o contrato HTTP como interface compartilhada. Não atualize apenas um lado e não presuma consumidores.

## Fluxo

1. Leia os `CLAUDE.md` da raiz, backend e frontend.
2. Localize produtores e consumidores:
   - controller, configuração de segurança, DTOs, mapper, service e testes backend;
   - models, serviços HTTP, componentes, formulários e testes frontend;
   - OpenAPI, exemplos e documentação equivalente, quando existirem.
3. Registre o contrato atual e o desejado, incluindo path, método, autenticação, request, response, status e erros.
4. Classifique a mudança como compatível ou incompatível.
5. Para breaking change, defina atualização coordenada ou transição explícita.
6. Implemente a fonte de verdade e seus testes.
7. Atualize todos os consumidores tipados e estados de UI afetados.
8. Valide serialização, validação, autorização, paginação, status de sucesso e respostas de erro.
9. Revise o diff completo para localizar consumidores ou documentação desatualizados.

## Delegação

Em mudanças amplas, use somente para análise:

- `codebase-explorer` para mapear dependências;
- `backend-reviewer` para contrato e regra backend;
- `frontend-reviewer` para consumo, estado e UX;
- `security-reviewer` quando exposição, autenticação ou autorização mudar;
- `test-reviewer` para lacunas de cobertura do contrato.

O agente principal integra e edita a solução.

## Validação

Execute testes backend relevantes, testes e build frontend. Quando o ambiente permitir, valide um fluxo ponta a ponta sem expor credenciais. Não declare compatibilidade sem verificar os consumidores encontrados.

## Entrega

Informe contrato antes/depois, compatibilidade, estratégia de migração quando aplicável, consumidores e documentação atualizados, testes executados e riscos restantes.
