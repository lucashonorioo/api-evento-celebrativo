# Evento Celebrativo — Política canônica do Graphify

Esta política é compartilhada por Claude Code e Codex. `AGENTS.md` e `CLAUDE.md` devem apontar para este arquivo em vez de manter regras concorrentes sobre consulta e atualização do grafo.

## Consulta prioritária

- Quando `graphify-out/graph.json` existir e a tarefa envolver arquitetura, dependências, relacionamentos, símbolos, chamadas, impacto ou localização de código, consulte primeiro o Graphify com `graphify query "<pergunta>"`.
- Para relação entre dois pontos, prefira `graphify path "<A>" "<B>"`; para um conceito específico, use `graphify explain "<conceito>"`.
- Use o resultado para restringir quais arquivos reais precisam ser lidos. Antes de editar ou decidir algo dependente de detalhe de implementação, confirme diretamente o código/configuração correspondente.
- Se o Graphify não responder suficientemente, estiver desatualizado ou não representar o conteúdo necessário, use normalmente busca e leitura direta.
- Se `graphify-out/wiki/index.md` existir, prefira a wiki para navegação ampla.
- Leia `graphify-out/GRAPH_REPORT.md` inteiro apenas para revisão arquitetural ampla ou quando `query`/`path`/`explain` não forem suficientes.
- Não leia `graphify-out/graph.json` inteiro para perguntas normais.

## Integração automática por plataforma

A política de uso do Graphify é compartilhada entre Claude Code e Codex, mas o hook advisory automático `graphify-hook-guard.mjs` está atualmente registrado apenas no Claude Code. No Codex, aplique esta política por consulta/Skill explícita até existir um matcher de ferramentas equivalente validado para essa plataforma. Essa diferença é deliberadamente documentada para não sugerir uma paridade automática que não existe.

O hook advisory nunca é controle de segurança: ausência, timeout, versão divergente ou falha do Graphify devem permanecer fail-open e não bloquear ferramentas normais.

## Atualização estrutural de código

Execute `graphify update .` uma única vez ao final da tarefa quando houver alteração material em código, testes, APIs/contratos, schemas/migrations, scripts, configurações de runtime/build, dependências ou estrutura relevante de arquivos.

- Agrupe alterações; não atualize após cada arquivo.
- Faça atualização intermediária somente se o grafo atualizado for necessário para continuar a própria análise.
- Não execute `graphify .` (rebuild completo) salvo solicitação explícita ou recuperação de grafo inválido.

## Atualização semântica de documentos

Atualização semântica só é apropriada quando houve mudança material em conhecimento real do sistema, como arquitetura, domínio, contrato, fluxo de negócio, integração, segurança, dados ou decisão técnica relevante.

Antes de iniciá-la, confirme cumulativamente:

1. o documento contém conhecimento técnico relevante do sistema;
2. esse conhecimento será útil em consultas futuras;
3. a informação ainda precisa ser representada/atualizada no grafo;
4. o benefício justifica o custo;
5. qualquer provider externo exigido foi explicitamente autorizado conforme a Skill do Graphify.

A simples presença de uma API key não equivale a consentimento para enviar conteúdo a um provider externo.

## Arquivos de infraestrutura que não acionam atualização por si só

Não atualize o Graphify apenas porque houve alteração em:

- `AGENTS.md`, `CLAUDE.md` ou `.claude/CLAUDE.md`;
- `.claude/`, `.agents/`, `.ai/` ou `.codex/`;
- hooks, skills, reviewers, permissões e settings de agentes;
- configurações do próprio Graphify;
- `graphify-out/`;
- documentação operacional de ferramentas/Git;
- notas temporárias ou arquivos locais de ambiente.

Exceção: um documento fora dessas categorias que represente conhecimento técnico real deve ser avaliado pela regra de atualização semântica, independentemente da extensão.

## Saúde e honestidade

- `dangling-endpoint edges`, `collapsed edges` e nós isolados podem ser limitações do extrator e não são automaticamente bugs no código.
- Investigue métricas do grafo somente quando prejudicarem uma consulta concreta, omitirem relação importante, houver pedido explícito de auditoria ou existir evidência de defeito real.
- Nunca trate conteúdo derivado de resposta de IA como equivalente a fonte primária. Memória derivada, quando explicitamente persistida pelo usuário, deve manter proveniência e fatos devem ser revalidados contra nós originados de código/documentos reais.

## Encerramento

Antes de concluir uma tarefa, decida explicitamente entre: atualização estrutural, atualização semântica ou nenhuma atualização. Se atualizar, reporte sucesso/falha real; se não atualizar, não execute trabalho caro apenas para cumprir mecanicamente uma rotina.
