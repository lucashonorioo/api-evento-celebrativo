import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const destructiveCommandRules = [
  [/\bgit\s+reset\s+--hard\b/i, 'git reset --hard pode descartar alterações locais.'],
  [/\bgit\s+clean\s+[^\r\n]*(?:--force|-[a-z]*f[a-z]*)/i, 'git clean com force pode remover arquivos não rastreados.'],
  [/\bgit\s+push\s+[^\r\n]*(?:--force(?:-with-lease)?|-f)(?:\s|$)/i, 'Force push está bloqueado pela política do projeto.'],
  [/\bgit\s+branch\s+-D\b/i, 'Exclusão forçada de branch está bloqueada.'],
  [/\bgit\s+checkout\s+(?:\S+\s+)?--\s+(?:\.|\*|:\/)\s*$/i, 'Esse checkout pode descartar alterações locais em massa.'],
  [/\bgit\s+restore\s+(?:\S+\s+)*(?:\.|\*)\s*$/i, 'Esse restore pode descartar alterações locais em massa.'],
  [/\brm\s+-[^\r\n]*r[^\r\n]*f[^\r\n]*\s+(?:\/\*?|~\/?|\.\/?|\*)\s*$/i, 'Remoção recursiva da raiz, home ou diretório atual está bloqueada.'],
  [/\bRemove-Item\b[^\r\n]*(?:-Recurse[^\r\n]*-Force|-Force[^\r\n]*-Recurse)[^\r\n]*(?:\s\.\s*$|\s\*\s*$|[A-Z]:\\\s*$)/i, 'Remoção recursiva forçada em massa está bloqueada.'],
  [/\b(?:diskpart|format\s+[A-Z]:|shutdown\b|Stop-Computer\b|Restart-Computer\b)/i, 'Comando destrutivo de sistema ou desligamento está bloqueado.'],
];

const protectedPathRules = [
  [/(^|\/)\.git(?:\/|$)/i, 'Arquivos internos de .git não devem ser alterados diretamente.'],
  [/(^|\/)(?:id_rsa|id_ed25519|credentials\.json|service-account[^/]*\.json)$/i, 'Arquivo de credencial ou identidade sensível está protegido.'],
  [/\.(?:pem|p12|pfx|key|keystore|jks)$/i, 'Arquivo de chave ou certificado privado está protegido.'],
];

const flywayMigrationPattern = /(^|\/)src\/main\/resources\/db\/migration\/V[^/]+\.sql$/i;

export function normalizePath(value) {
  return String(value ?? '').trim().replaceAll('\\', '/').replace(/^['"]|['"]$/g, '');
}

export function isProtectedEnvFile(normalizedPath) {
  const name = normalizedPath.split('/').at(-1) ?? '';
  if (!/^\.env(?:\..+)?$/i.test(name)) return false;
  return !/^\.env\.(?:example|sample|template)$/i.test(name);
}

export function isPathTrackedByGit(absolutePath, projectDir) {
  try {
    execFileSync('git', ['-C', projectDir, 'ls-files', '--error-unmatch', '--', absolutePath], {
      stdio: ['ignore', 'ignore', 'ignore'],
    });
    return true;
  } catch (error) {
    // ls-files sai com status 1 quando o caminho não está rastreado; qualquer outra
    // falha (git ausente, diretório fora de um repositório etc.) é tratada como
    // rastreada para falhar de forma segura e não liberar a escrita por engano.
    if (error.status === 1) return false;
    return true;
  }
}

export function evaluateShellCommand(command) {
  const text = String(command ?? '');
  for (const [pattern, reason] of destructiveCommandRules) {
    if (pattern.test(text)) return reason;
  }
  return null;
}

export function evaluateFileWrite({
  toolName,
  filePath,
  cwd = process.cwd(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || cwd,
  isTracked = isPathTrackedByGit,
}) {
  const normalized = normalizePath(filePath);
  if (!normalized) return null;

  if (normalized.split('/').includes('..')) {
    return `Caminho com travessia de diretório está bloqueado: ${normalized}`;
  }

  const absolutePath = path.isAbsolute(normalized) ? path.resolve(normalized) : path.resolve(cwd, normalized);
  const absoluteProjectDir = path.resolve(projectDir);
  const relativeToProject = path.relative(absoluteProjectDir, absolutePath);
  if (relativeToProject.startsWith('..') || path.isAbsolute(relativeToProject)) {
    return `Alteração fora do workspace do projeto está bloqueada: ${normalized}`;
  }

  if (isProtectedEnvFile(normalized)) {
    return `Arquivo de ambiente real não deve ser alterado pelo agente: ${normalized}`;
  }

  for (const [pattern, reason] of protectedPathRules) {
    if (pattern.test(normalized)) return `${reason} Caminho: ${normalized}`;
  }

  if (flywayMigrationPattern.test(normalized) && isTracked(absolutePath, absoluteProjectDir)) {
    return `Migration Flyway versionada existente não deve ser alterada: ${normalized}. Crie uma nova migration incremental.`;
  }

  return null;
}

export function evaluateHookPayload(payload, options = {}) {
  const toolName = String(payload?.tool_name ?? '');
  if (toolName === 'Bash' || toolName === 'PowerShell') {
    return evaluateShellCommand(payload?.tool_input?.command);
  }

  if (toolName === 'Edit' || toolName === 'Write') {
    return evaluateFileWrite({
      toolName,
      filePath: payload?.tool_input?.file_path,
      cwd: payload?.cwd,
      projectDir: options.projectDir ?? process.env.CLAUDE_PROJECT_DIR ?? payload?.cwd,
      isTracked: options.isTracked,
    });
  }

  return null;
}

async function main() {
  try {
    let input = '';
    for await (const chunk of process.stdin) input += chunk;
    const payload = JSON.parse(input || '{}');
    const reason = evaluateHookPayload(payload);

    if (reason) {
      process.stderr.write(`${reason}\n`);
      process.exitCode = 2;
    }
  } catch (error) {
    process.stderr.write(`Hook de segurança não conseguiu analisar a chamada e não a bloqueou: ${error.message}\n`);
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
