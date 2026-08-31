# Lifecycle de processos iniciados por agentes

Carregue este arquivo apenas quando a tarefa realmente exigir servidor/processo de longa duração.

## Regra

O agente deve encerrar todo processo de backend/frontend que ele próprio iniciar e nunca deve matar processos preexistentes do usuário por nome global.

Prefira comandos finitos (`test`, `build`, chamada HTTP contra serviço já existente). Só inicie servidor quando isso comprovar comportamento que não pode ser validado de forma mais barata.

## Windows / PowerShell

Inicie o processo com `Start-Process -PassThru` e preserve o PID do processo criado pelo agente.

Backend, exemplo:

```powershell
$p = Start-Process -FilePath ".\mvnw.cmd" -ArgumentList "spring-boot:run" -PassThru
$agentBackendPid = $p.Id
```

Frontend, exemplo:

```powershell
$p = Start-Process -FilePath "npm.cmd" -ArgumentList "start" -PassThru
$agentFrontendPid = $p.Id
```

Ao terminar a validação, encerre somente a árvore que nasceu desse PID:

```powershell
if ($agentBackendPid)  { taskkill /PID $agentBackendPid  /T /F 2>$null | Out-Null }
if ($agentFrontendPid) { taskkill /PID $agentFrontendPid /T /F 2>$null | Out-Null }
```

Depois confirme que o PID não permanece ativo. Se o comando de start falhar antes de criar PID, não faça limpeza ampla.

**Proibido:** `taskkill /IM java.exe`, `taskkill /IM node.exe`, `Stop-Process -Name java/node` ou equivalente global.

## Bash

Quando aplicável, capture `$!` do processo iniciado pelo agente e encerre esse PID/process group no bloco de cleanup. Nunca use `pkill java`, `pkill node` ou equivalente amplo.

## Disciplina

- trate start/cleanup como `try/finally`: falha de teste, interrupção ou review não elimina a obrigação de cleanup;
- não deixe watcher, servidor, tail ou processo em background para uma etapa futura;
- se a plataforma/tool encerrar automaticamente o processo, verifique isso antes de concluir;
- reporte apenas falha real de cleanup; não mate processo cuja origem não possa ser atribuída ao agente.
