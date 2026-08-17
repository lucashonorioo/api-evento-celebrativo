# Evento Celebrativo — Política de revisão de engenharia

## Objetivo

Esta política é a fonte comum de critérios para revisão de código no Codex e no Claude Code.

Toda alteração que modifique comportamento executável, contrato, persistência, segurança, testes ou configuração operacional deve passar por uma revisão de engenharia independente depois da última implementação relevante e antes de a tarefa ser declarada concluída.

A revisão existe para encontrar defeitos, regressões, decisões estruturais inadequadas e riscos concretos. Ela não existe para impor preferências pessoais, aumentar abstração sem necessidade ou produzir refatorações cosméticas.

## Princípios

- Trabalhe a partir de evidências do código, testes, configuração, migrations e diff reais.
- Revise o comportamento alterado e também os consumidores e dependências diretamente afetados quando isso for necessário para comprovar o impacto.
- Preserve a arquitetura e os padrões válidos do projeto; proponha mudança estrutural somente quando houver benefício concreto para correção, coesão, segurança, manutenção ou evolução.
- Aplique princípios de engenharia de software de forma pragmática. SOLID, separação de responsabilidades, baixo acoplamento, alta coesão e reutilização são meios, não objetivos isolados.
- Não trate preferência de nomenclatura, formatação ou estilo como achado de engenharia quando lint, formatter ou convenções do projeto já resolvem a questão.
- O reviewer é somente leitura. A correção pertence ao agente implementador.
- Não invente achados para preencher a revisão.

## Quando a revisão é obrigatória

Execute `review-change` após a última alteração relevante quando a tarefa modificar, entre outros:

- código backend ou frontend;
- testes que representem comportamento da aplicação;
- controllers, DTOs, services, repositories, entidades, mappers ou contratos;
- componentes, rotas, guards, interceptors, serviços HTTP, estado ou formulários;
- autenticação, autorização, roles, JWT, CORS ou exposição de dados;
- migrations, schema, queries ou regras de persistência;
- dependências ou configurações que alterem build, runtime, segurança ou deploy;
- comportamento compartilhado entre backend e frontend.

Mudanças apenas em instruções de agentes, documentação operacional ou arquivos gerados pelo Graphify não exigem esse gate, salvo solicitação explícita de revisão.

## Processo obrigatório

1. Determine a base correta da comparação e identifique arquivos staged, unstaged e untracked relacionados à tarefa.
2. Leia as instruções da raiz e das áreas afetadas.
3. Leia o diff inteiro da tarefa antes de concluir sobre arquivos isolados.
4. Trace o fluxo alterado o suficiente para entender entrada, regra, persistência, saída e consumidores relevantes.
5. Classifique o risco da mudança.
6. Avalie todas as dimensões aplicáveis desta política.
7. Considere resultados reais de testes, build e validações; não substitua revisão por compilação bem-sucedida.
8. Registre somente achados sustentados por cenário e evidência concreta.
9. Se houver `CHANGES_REQUIRED`, devolva os achados ao implementador.
10. Depois de qualquer correção de código motivada pela revisão, execute novamente as validações afetadas e uma revisão final do novo diff.

## Classificação de risco

### Baixo

Mudança local, pequena e de comportamento simples, sem alteração de contrato, persistência, autorização, concorrência ou fluxo entre módulos.

A revisão continua obrigatória, mas pode ser feita diretamente por `review-change` sem especialistas adicionais.

### Médio

Mudança que atravessa mais de uma classe/componente, altera regra de negócio, estado de UI, tratamento de erro, query, transação, contrato compatível ou exige testes em mais de uma camada.

Revise a área afetada com profundidade e use especialistas disponíveis quando eles melhorarem a qualidade da análise.

### Alto

Inclui, entre outros:

- autenticação, autorização, roles, JWT, CORS ou endpoint público;
- migration destrutiva ou alteração relevante de schema/dados;
- breaking change de API;
- concorrência, idempotência, consistência transacional ou risco de perda/corrupção de dados;
- mudança estrutural entre módulos ou camadas;
- fluxo full stack crítico;
- processamento de dados sensíveis;
- alteração com grande superfície de regressão.

Exige revisão especializada das dimensões afetadas e evidência de validação proporcional ao risco.

## Dimensões da revisão

### 1. Correção funcional e domínio

Verifique se:

- o requisito e os critérios de aceite são atendidos;
- invariantes do domínio permanecem verdadeiras;
- casos de borda, estados inválidos, `null`, vazio, duplicidade e transições são tratados quando aplicáveis;
- a implementação não cria comportamento silenciosamente diferente em fluxos existentes;
- data/hora, timezone, ordenação, paginação e identificadores preservam a semântica esperada;
- erros não são ocultados por fallback artificial ou sucesso falso.

### 2. Arquitetura e desenho

Verifique se:

- cada camada mantém responsabilidade coerente com a arquitetura existente;
- regras de negócio não vazam para controller, mapper, template ou repository sem motivo;
- integração HTTP, estado e apresentação não ficam acoplados desnecessariamente;
- dependências apontam na direção esperada e não criam ciclos ou acoplamento forte evitável;
- classes, serviços, componentes e métodos permanecem coesos e com escopo compreensível;
- duplicação relevante foi evitada sem criar abstração prematura;
- uma nova abstração, camada ou pattern resolve um problema real e não apenas preferência teórica;
- mudanças estruturais são compatíveis com o restante do projeto e não deixam dois padrões concorrentes sem justificativa.

### 3. Contratos e compatibilidade

Verifique se:

- request, response, path, status HTTP, roles e paginação permanecem coerentes;
- consumers foram atualizados quando o contrato mudou;
- serialização, validação e tratamento de erro correspondem ao contrato real;
- breaking changes são intencionais e possuem atualização coordenada ou estratégia de transição;
- nenhuma entidade de persistência é exposta acidentalmente como contrato público.

### 4. Persistência, dados e transações

Verifique se:

- constraints e relacionamentos preservam integridade;
- limites transacionais correspondem ao caso de uso;
- operações parciais não deixam estado inconsistente;
- migrations são incrementais e migrations já versionadas não foram reescritas;
- mudanças de schema consideram dados existentes e compatibilidade de rollout quando aplicável;
- JPA lazy/eager, cascades, paginação, N+1 e queries customizadas não introduzem risco evidente;
- concorrência, unicidade e idempotência são tratadas onde o domínio exige.

### 5. Segurança e privacidade

Verifique se:

- autorização é aplicada server-side e no nível correto do recurso;
- endpoints novos não ficaram públicos acidentalmente;
- autenticação, roles, claims e tokens preservam as regras existentes;
- entradas não confiáveis são validadas no limite adequado;
- secrets, senhas, tokens, dados pessoais ou detalhes internos não aparecem em código, logs ou respostas;
- CORS, HTML externo e URLs/origens não ampliam superfície de ataque sem intenção;
- mensagens de erro não revelam stack trace, SQL ou informação sensível.

### 6. Erros, resiliência e observabilidade

Verifique se:

- falhas conhecidas são convertidas para erros de domínio/HTTP apropriados;
- exceções genéricas não escondem a causa nem transformam erro em sucesso;
- retry, timeout e idempotência são considerados quando houver integração ou operação suscetível a repetição;
- logs são úteis para diagnóstico, proporcionais ao evento e não expõem dados sensíveis;
- não restaram logs temporários, debug ou tratamento provisório.

### 7. Desempenho e escalabilidade

Verifique apenas riscos concretos, como:

- `findAll()` ou carga sem paginação em coleção potencialmente grande;
- N+1 ou consultas repetidas em loops;
- processamento quadrático evitável em caminho frequente;
- renderização ou função custosa repetida em template;
- subscriptions ou listeners acumulados;
- chamadas HTTP redundantes ou estado global desnecessário.

Não proponha otimização sem evidência ou benefício plausível.

### 8. Manutenibilidade

Verifique se:

- nomes e limites de responsabilidade tornam o comportamento compreensível;
- complexidade adicional é justificada;
- código morto, caminhos duplicados e compatibilidade temporária têm propósito claro;
- comentários explicam decisões não óbvias, não repetem o código;
- novas dependências são realmente necessárias;
- a mudança respeita convenções locais em vez de introduzir uma segunda forma equivalente de fazer a mesma coisa.

### 9. Testes e verificabilidade

Verifique se:

- o comportamento novo ou corrigido possui proteção proporcional ao risco;
- cenários de sucesso e falhas relevantes estão cobertos;
- autorização, conflito, inexistência, limites e regressões são testados quando aplicáveis;
- mocks representam contratos reais e assertions comprovam efeitos observáveis;
- testes não foram enfraquecidos, desabilitados ou ajustados apenas para aceitar comportamento incorreto;
- a camada de teste escolhida é a mais econômica capaz de provar o comportamento.

### 10. Frontend, UX e acessibilidade

Quando houver frontend, verifique se:

- loading, vazio, erro, acesso negado e sessão expirada são coerentes com o fluxo;
- formulários impedem estados inválidos e múltiplos submits quando necessário;
- rotas, guards e links continuam válidos;
- HTML semântico, teclado, foco, labels e contraste não regrediram;
- responsividade básica foi preservada;
- TypeScript continua estrito e não usa `any` ou non-null assertion para esconder incerteza;
- RxJS/signals e ciclo de vida não produzem leaks ou estados concorrentes evitáveis.

## Severidade

### BLOCKER

Risco de perda/corrupção de dados, falha crítica de segurança, impossibilidade de executar/compilar o fluxo principal ou violação grave de contrato que impede uso seguro. Deve ser corrigido antes da conclusão.

### HIGH

Bug funcional provável, regressão importante, autorização incorreta, inconsistência transacional, breaking change não tratado ou falha estrutural com impacto relevante. Deve ser corrigido antes da conclusão.

### MEDIUM

Problema concreto de arquitetura, manutenção, tratamento de erro, desempenho ou teste que aumenta de forma material a chance de defeito ou custo de evolução. Se foi introduzido pela tarefa e a correção é compatível com o escopo, deve ser corrigido. Se for preexistente ou claramente fora do escopo, documente sem ampliar a tarefa indevidamente.

### LOW

Melhoria objetiva e localizada, sem risco imediato. Não bloqueia conclusão. Não use `LOW` para gosto pessoal, formatação ou refatoração especulativa.

## Veredito

Use exatamente um dos seguintes:

- `PASS`: nenhum achado bloqueante ou ação pendente dentro do escopo.
- `PASS WITH NOTES`: somente observações não bloqueantes, limitações de ambiente ou dívida preexistente claramente separada da tarefa.
- `CHANGES_REQUIRED`: existe `BLOCKER`, `HIGH` ou `MEDIUM` introduzido pela tarefa que deve ser resolvido antes da conclusão.

Nunca declare `PASS` apenas porque testes passaram. Nunca declare `CHANGES_REQUIRED` sem evidência concreta.

## Formato mínimo da revisão

A saída deve conter:

1. **Escopo revisado** — base/diff e áreas afetadas.
2. **Risco** — baixo, médio ou alto, com justificativa curta.
3. **Veredito** — um dos três valores definidos acima.
4. **Achados** — ordenados por severidade, cada um com arquivo/símbolo, evidência, cenário, impacto e correção mínima.
5. **Validações consideradas** — testes/build/verificações realmente executados ou resultados fornecidos pelo implementador.
6. **Riscos residuais** — somente o que não pôde ser comprovado.

Quando não houver achados, diga explicitamente que nenhum defeito acionável foi encontrado no escopo revisado.

Quando a revisão fizer parte do encerramento de uma implementação, a resposta final do agente implementador deve registrar uma linha técnica exatamente como `Engineering review: PASS` ou `Engineering review: PASS WITH NOTES`. `CHANGES_REQUIRED` nunca representa conclusão.
