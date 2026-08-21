import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const GRAPHIFY_CANDIDATES = process.platform === 'win32'
  ? ['graphify.exe', 'graphify.cmd', 'graphify.bat', 'graphify']
  : ['graphify'];
const VERSION_TIMEOUT_MS = 500;
const GUARD_TIMEOUT_MS = 2500;
const TOTAL_BUDGET_MS = 5000;
const ADVISORY_OUTPUT_LIMIT = 12000;

async function readStdin() {
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return input;
}

function payloadCwd(input) {
  try {
    return JSON.parse(input || '{}')?.cwd || process.cwd();
  } catch {
    return process.cwd();
  }
}

function resolveProjectRoot(input) {
  if (process.env.CLAUDE_PROJECT_DIR) return path.resolve(process.env.CLAUDE_PROJECT_DIR);
  const cwd = payloadCwd(input);
  try {
    return execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], {
      encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 1500,
    }).trim();
  } catch {
    return path.resolve(cwd);
  }
}

function pinnedVersion(projectRoot) {
  const versionFile = path.join(projectRoot, '.claude', 'skills', 'graphify', '.graphify_version');
  if (!existsSync(versionFile)) return null;
  const version = readFileSync(versionFile, 'utf8').trim();
  return /^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/.test(version) ? version : null;
}

export function buildWindowsBatchCommand(bin, args) {
  const safeParts = [bin, ...args].map((value) => {
    const text = String(value);
    if (!/^[A-Za-z0-9_.\/-]+$/.test(text)) throw new Error('argumento inesperado ao invocar graphify batch');
    return text;
  });
  return safeParts.join(' ');
}

// No Windows, `spawnSync` só consegue sinalizar término ao processo filho
// direto (cmd.exe, quando o binário é .cmd/.bat). Se esse filho tiver
// spawnado um neto (o `graphify.exe` real, ou qualquer processo que o
// binário travado tenha iniciado), matar apenas o filho deixa o neto órfão
// rodando indefinidamente — o timeout do `options.timeout` deixa de ser um
// limite real para a árvore inteira. `taskkill /T /F` mata a árvore de
// processos pelo PID, não depende do wrapper cmd.exe cooperar com o sinal.
// É chamado sempre (não só após timeout): custo desprezível quando não há
// nada a matar, e garante que nenhuma chamada — mesmo bem-sucedida — deixe
// um processo abandonado para trás.
function killProcessTree(pid) {
  if (process.platform !== 'win32' || !Number.isInteger(pid) || pid <= 0) return;
  try {
    execFileSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore', timeout: 3000 });
  } catch {
    // PID já encerrado ou taskkill indisponível: não é fatal — Graphify é
    // fail-open, e a limpeza de processo é hardening, não uma garantia bloqueante.
  }
}

function spawnGraphify(bin, args, options = {}) {
  const result = process.platform === 'win32' && /\.(?:cmd|bat)$/i.test(bin)
    // `%1` em batch preserva aspas. Como nomes/argumentos são constantes e
    // validados acima, passe-os sem quoting artificial para compatibilidade com
    // wrappers que comparam `%1` diretamente com `--version`/`hook-guard`.
    ? spawnSync(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', buildWindowsBatchCommand(bin, args)], options)
    : spawnSync(bin, args, options);
  killProcessTree(result.pid);
  return result;
}

function installedVersion(bin) {
  const result = spawnGraphify(bin, ['--version'], {
    encoding: 'utf8',
    timeout: VERSION_TIMEOUT_MS,
    killSignal: 'SIGTERM',
    windowsHide: true,
  });
  if (result.error || result.status !== 0) return null;
  const text = `${result.stdout || ''}\n${result.stderr || ''}`;
  return text.match(/\b(\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?)\b/)?.[1] ?? null;
}

function runHookGuard(mode, input, expectedVersion) {
  const startedAt = Date.now();
  for (const bin of GRAPHIFY_CANDIDATES) {
    if (Date.now() - startedAt >= TOTAL_BUDGET_MS) return null;
    const actualVersion = installedVersion(bin);
    if (!actualVersion) continue;
    if (expectedVersion && actualVersion !== expectedVersion) {
      process.stderr.write(`graphify: versão ${actualVersion} no PATH diverge da versão pinada ${expectedVersion}; hook-guard ignorado (fail-open).\n`);
      return null;
    }

    const remaining = Math.max(1, TOTAL_BUDGET_MS - (Date.now() - startedAt));
    const result = spawnGraphify(bin, ['hook-guard', mode], {
      input,
      encoding: 'utf8',
      timeout: Math.min(GUARD_TIMEOUT_MS, remaining),
      killSignal: 'SIGTERM',
      windowsHide: true,
    });
    // Um binário encontrado que falhou não justifica repetir aliases do mesmo
    // pacote. Graphify é auxiliar: falhe aberto dentro do orçamento total.
    return result.error ? null : result;
  }
  return null;
}

async function main() {
  const mode = process.argv[2];
  const input = await readStdin();
  if (mode !== 'read' && mode !== 'search') return;

  const projectRoot = resolveProjectRoot(input);
  const expectedVersion = pinnedVersion(projectRoot);
  const result = runHookGuard(mode, input, expectedVersion);

  // Fail-open: Graphify é auxiliar de contexto, não controle de segurança.
  // A política canônica do projeto prevalece sobre linguagem imperativa que o
  // binário externo eventualmente imprima.
  if (!result || result.status !== 0) {
    process.stderr.write('graphify: indisponível, incompatível ou falhou; hook-guard ignorado nesta chamada (fail-open).\n');
    return;
  }

  if (result.stdout) {
    process.stdout.write(
      `Graphify advisory (não bloqueante; siga .ai/graphify/GRAPHIFY_POLICY.md):\n${String(result.stdout).slice(0, ADVISORY_OUTPUT_LIMIT)}`,
    );
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
