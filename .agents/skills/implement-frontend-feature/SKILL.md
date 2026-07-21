---
name: implement-frontend-feature
description: Implemente features, correções ou refatorações focadas no frontend Angular/TypeScript do Evento Celebrativo. Use quando a tarefa altera rotas, componentes, serviços HTTP, models, autenticação, formulários, CSS ou testes frontend; não use para mudanças apenas backend.
---

# Implementar alteração frontend

## Preparação

1. Leia o AGENTS da raiz e o AGENTS do frontend.
2. Confirme o requisito e o estado atual no código.
3. Consulte o backend ou OpenAPI para confirmar request, response, roles, paginação e erros.
4. Localize rota, página, componente, serviço, model, guard/interceptor e testes.
5. Quando a tarefa for ampla, use `codebase_explorer` para mapear a funcionalidade sem editar.

## Implementação

1. Preserve standalone components e TypeScript estrito.
2. Reutilize padrões e componentes existentes antes de criar novos.
3. Separe apresentação, integração HTTP e estado quando houver responsabilidade real.
4. Use signals para estado local simples e RxJS para fluxos assíncronos.
5. Modele requests e responses sem `any`.
6. Trate loading, vazio, erro, permissão e sessão conforme necessário.
7. Preserve guards, interceptor e autorização do backend.
8. Garanta HTML semântico, teclado, foco, labels, contraste e responsividade básica.
9. Não adicione dependências ou frameworks visuais sem necessidade aprovada.

## Testes

Atualize ou crie testes para serviços, componentes, formulários, guards, interceptors e regressões relevantes.

Execute:

```powershell
npm test -- --watch=false
npm run build
```

Use comandos mais específicos antes da suíte completa quando disponível.

## Revisão

Para mudança visual ou comportamental relevante, use `frontend_reviewer`. Para auth, token, HTML externo ou permissões, inclua `security_reviewer`.

## Entrega

Reporte arquivos, comportamento, validações, limitações visuais e riscos restantes.
