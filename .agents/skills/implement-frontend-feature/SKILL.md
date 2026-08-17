---
name: implement-frontend-feature
description: Implemente features, correções ou refatorações focadas no frontend Angular/TypeScript do Evento Celebrativo. Use quando a tarefa altera rotas, componentes, serviços HTTP, models, autenticação, formulários, CSS ou testes frontend; não use para mudanças apenas backend.
---

# Implementar alteração frontend

## Preparação

1. Leia o `AGENTS.md` da raiz e o `AGENTS.md` do frontend.
2. Confirme requisito, critérios de aceite e comportamento atual no código.
3. Consulte backend, OpenAPI ou testes para confirmar request, response, roles, paginação e erros.
4. Localize rota, página, componente, serviço, model, formulário, guard/interceptor, estilos e testes relacionados.
5. Quando a tarefa for ampla, use exploração somente leitura para mapear a funcionalidade antes da implementação.

## Implementação

1. Preserve standalone components e TypeScript estrito.
2. Reutilize padrões e componentes existentes antes de criar novos.
3. Separe apresentação, integração HTTP, formulário e estado quando houver responsabilidade real.
4. Use signals para estado local simples e RxJS para fluxos assíncronos.
5. Modele requests e responses sem `any`.
6. Trate loading, vazio, erro, permissão e sessão conforme necessário.
7. Preserve guards, interceptor e autorização do backend.
8. Garanta HTML semântico, teclado, foco, labels, contraste e responsividade básica.
9. Evite subscriptions sem ciclo de vida controlado e trabalho custoso no template.
10. Não adicione dependências, frameworks visuais ou estado global sem necessidade comprovada.

## Testes e validação

Atualize ou crie testes para serviços, componentes, formulários, guards, interceptors e regressões relevantes.

Execute comandos específicos quando disponíveis e, conforme o risco:

```powershell
npm test -- --watch=false
npm run build
```

Para mudança visual ou de interação, valide no navegador quando o ambiente permitir e registre a limitação quando não for possível.

## Gate de conclusão obrigatório

Depois da última alteração relevante de código:

1. execute `validate-project`;
2. execute `review-change` como revisão independente de engenharia;
3. se o veredito for `CHANGES_REQUIRED`, corrija somente os achados relacionados à tarefa;
4. repita validações afetadas e `review-change` depois de qualquer correção;
5. conclua apenas com `PASS` ou `PASS WITH NOTES`.

A revisão deve avaliar também arquitetura Angular, contrato real da API, estado de UI, tipagem, RxJS, acessibilidade e responsividade quando aplicáveis.

## Entrega

Reporte comportamento, arquivos relevantes, validações, acessibilidade/responsividade verificadas, veredito da revisão de engenharia, limitações visuais e riscos residuais.
