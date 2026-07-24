---
name: implement-frontend-feature
description: Implementa feature, correção ou refatoração focada no frontend Angular/TypeScript do Evento Celebrativo. Use quando a tarefa alterar rotas, componentes, serviços HTTP, models, autenticação, formulários, CSS ou testes frontend; não use para mudanças somente backend.
---

# Implementar alteração frontend

## Preparação

1. Leia o `CLAUDE.md` da raiz e o do frontend.
2. Confirme requisito, critérios de aceite e comportamento atual no código.
3. Consulte backend, OpenAPI ou testes para confirmar requests, responses, roles, paginação e erros.
4. Localize rota, página, componente, serviço, model, formulário, guard, interceptor, estilos e testes relacionados.
5. Para tarefa ampla, use `codebase-explorer` para mapear a funcionalidade sem editar.

## Implementação

- Preserve standalone components e TypeScript estrito.
- Reutilize padrões e componentes existentes antes de criar novos.
- Separe apresentação, integração HTTP, formulário e estado quando houver responsabilidade real.
- Use signals para estado local simples e RxJS para fluxos assíncronos.
- Modele requests e responses sem `any`.
- Trate loading, vazio, erro, permissão e sessão conforme necessário.
- Preserve guards, interceptor e autorização do backend.
- Garanta HTML semântico, teclado, foco, labels, contraste e responsividade básica.
- Evite subscriptions sem gerenciamento de ciclo de vida e trabalho custoso no template.
- Não adicione dependências, frameworks visuais ou estado global sem necessidade aprovada.

## Testes e validação

Atualize ou crie testes para serviços, componentes, formulários, guards, interceptors e regressões relevantes.

Execute:

```powershell
npm test -- --watch=false
npm run build
```

Use comandos mais específicos antes da suíte completa quando disponíveis. Para mudanças visuais ou de interação, valide no navegador quando o ambiente permitir e registre a limitação quando isso não for possível.

## Revisão e entrega

Use `frontend-reviewer` em mudança visual ou comportamental relevante. Inclua `security-reviewer` para autenticação, token, HTML externo ou permissões e `test-reviewer` quando houver vários estados ou contratos afetados.

Reporte arquivos, comportamento, validações, acessibilidade/responsividade verificadas, limitações visuais e riscos restantes.
