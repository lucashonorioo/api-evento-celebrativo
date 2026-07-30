import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// Resolvidos via PATH em tempo de execução (nunca um caminho absoluto fixo),
// para funcionar em qualquer máquina, com ou sem a extensão .exe no PATHEXT.
const GRAPHIFY_CANDIDATES = ['graphify', 'graphify.exe'];

async function readStdin() {
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return input;
}

function runHookGuard(mode, input) {
  for (const bin of GRAPHIFY_CANDIDATES) {
    // shell: true delega a resolução do executável ao shell do SO (cmd.exe no
    // Windows, /bin/sh nos demais), que já sabe procurar no PATH e aplicar
    // extensões executáveis — sem depender de nenhum caminho fixo por usuário.
    const result = spawnSync(bin, ['hook-guard', mode], {
      shell: true,
      input,
      encoding: 'utf8',
    });
    if (!result.error) return result;
  }
  return null;
}

async function main() {
  const mode = process.argv[2];
  const input = await readStdin();
  if (mode !== 'read' && mode !== 'search') return;

  const result = runHookGuard(mode, input);

  // Fail-open: graphify é um auxiliar de contexto, não um controle de segurança.
  // Se estiver ausente ou falhar, a ferramenta (Read/Grep/Bash) deve prosseguir
  // normalmente — no máximo um aviso curto, sem travar a sessão.
  if (!result || result.status !== 0) {
    process.stderr.write('graphify: indisponível ou falhou; hook-guard ignorado nesta chamada (fail-open).\n');
    return;
  }

  if (result.stdout) process.stdout.write(result.stdout);
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
