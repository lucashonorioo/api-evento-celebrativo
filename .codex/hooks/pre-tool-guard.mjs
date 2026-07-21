import { pathToFileURL } from 'node:url';

const destructiveCommandRules = [
  {
    pattern: /\bgit\s+reset\s+--hard\b/i,
    reason: 'git reset --hard pode descartar alterações locais.',
  },
  {
    pattern: /\bgit\s+clean\s+[^\r\n]*(?:--force|-[a-z]*f[a-z]*)/i,
    reason: 'git clean com force pode remover arquivos não rastreados.',
  },
  {
    pattern: /\bgit\s+push\s+[^\r\n]*(?:--force(?:-with-lease)?|-f)(?:\s|$)/i,
    reason: 'Force push está bloqueado pela política do projeto.',
  },
  {
    pattern: /\bgit\s+branch\s+-D\b/i,
    reason: 'Exclusão forçada de branch está bloqueada.',
  },
  {
    pattern: /\bgit\s+checkout\s+--\s+(?:\.|\*|:\/)/i,
    reason: 'Esse checkout pode descartar alterações locais em massa.',
  },
  {
    pattern: /\bgit\s+restore\s+[^\r\n]*(?:--worktree|--source)[^\r\n]*(?:\s\.|\s\*)/i,
    reason: 'Esse restore pode descartar alterações locais em massa.',
  },
  {
    pattern: /\brm\s+-[^\r\n]*r[^\r\n]*f[^\r\n]*\s+(?:\/|~|\.|\.\/|\*)\s*$/i,
    reason: 'Remoção recursiva de raiz, home ou diretório atual está bloqueada.',
  },
  {
    pattern: /\bRemove-Item\b[^\r\n]*(?:-Recurse[^\r\n]*-Force|-Force[^\r\n]*-Recurse)[^\r\n]*(?:\s\.\s*$|\s\*\s*$|[A-Z]:\\\s*$)/i,
    reason: 'Remoção recursiva forçada em massa está bloqueada.',
  },
  {
    pattern: /\b(?:diskpart|format\s+[A-Z]:|shutdown\b|Stop-Computer\b|Restart-Computer\b)/i,
    reason: 'Comando de sistema destrutivo ou de desligamento está bloqueado.',
  },
];

const sensitivePathRules = [
  {
    pattern: /(^|\/)\.env(?:\.(?!example$|sample$)[^/]+)?$/i,
    reason: 'Arquivos .env reais não devem ser alterados pelo agente.',
  },
  {
    pattern: /(^|\/)(?:id_rsa|id_ed25519|credentials\.json|service-account[^/]*\.json)$/i,
    reason: 'Arquivo de credencial ou identidade sensível está protegido.',
  },
  {
    pattern: /\.(?:pem|p12|pfx|key|keystore|jks)$/i,
    reason: 'Arquivo de chave ou certificado privado está protegido.',
  },
];

const flywayMigrationPattern = /(^|\/)src\/main\/resources\/db\/migration\/V[^/]+\.sql$/i;

export function normalizePath(value) {
  return String(value ?? '').trim().replaceAll('\\', '/').replace(/^['"]|['"]$/g, '');
}

export function extractPatchEntries(patchText) {
  const entries = [];
  const regex = /^\*\*\* (Add|Update|Delete) File:\s*(.+)$/gm;
  for (const match of String(patchText ?? '').matchAll(regex)) {
    entries.push({ operation: match[1].toLowerCase(), path: normalizePath(match[2]) });
  }
  return entries;
}

export function evaluateShellCommand(command) {
  const text = String(command ?? '');
  for (const rule of destructiveCommandRules) {
    if (rule.pattern.test(text)) {
      return rule.reason;
    }
  }
  return null;
}

export function evaluatePatch(patchText) {
  for (const entry of extractPatchEntries(patchText)) {
    for (const rule of sensitivePathRules) {
      if (rule.pattern.test(entry.path)) {
        return `${rule.reason} Caminho: ${entry.path}`;
      }
    }

    if ((entry.operation === 'update' || entry.operation === 'delete') && flywayMigrationPattern.test(entry.path)) {
      return `Migration Flyway versionada existente não deve ser ${entry.operation === 'update' ? 'alterada' : 'excluída'}: ${entry.path}. Crie uma nova migration incremental.`;
    }
  }
  return null;
}

export function evaluateHookPayload(payload) {
  const toolName = String(payload?.tool_name ?? '');
  const command = payload?.tool_input?.command ?? '';

  if (toolName === 'Bash') {
    return evaluateShellCommand(command);
  }

  if (toolName === 'apply_patch' || toolName === 'Edit' || toolName === 'Write') {
    return evaluatePatch(command);
  }

  return null;
}

function deny(reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'deny',
      permissionDecisionReason: reason,
    },
  }));
}

async function main() {
  try {
    let input = '';
    for await (const chunk of process.stdin) input += chunk;
    const payload = JSON.parse(input || '{}');
    const reason = evaluateHookPayload(payload);
    if (reason) deny(reason);
  } catch (error) {
    process.stdout.write(JSON.stringify({
      systemMessage: `Hook de segurança não conseguiu analisar a chamada e não a bloqueou: ${error.message}`,
    }));
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
