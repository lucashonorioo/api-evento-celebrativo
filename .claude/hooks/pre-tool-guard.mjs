import path from 'node:path';
import { pathToFileURL } from 'node:url';

export {
  evaluateFileWrite,
  evaluatePatch,
  evaluateShellCommand,
  extractPatchEntries,
  gitProjectRoot,
  isPathTrackedByGit,
  isProtectedEnvFile,
  normalizePath,
} from '../../.ai/guards/safety-policy.mjs';

import {
  evaluateFileWrite,
  evaluateShellCommand,
  gitProjectRoot,
} from '../../.ai/guards/safety-policy.mjs';

export function evaluateHookPayload(payload, options = {}) {
  const toolName = String(payload?.tool_name ?? '');
  const cwd = payload?.cwd ?? process.cwd();
  const projectDir = options.projectDir ?? process.env.CLAUDE_PROJECT_DIR ?? gitProjectRoot(cwd) ?? cwd;

  if (toolName === 'Bash' || toolName === 'PowerShell') {
    return evaluateShellCommand(payload?.tool_input?.command, {
      cwd, projectDir, isTracked: options.isTracked,
    });
  }

  if (toolName === 'Edit' || toolName === 'Write') {
    return evaluateFileWrite({
      filePath: payload?.tool_input?.file_path,
      cwd,
      projectDir,
      isTracked: options.isTracked,
    });
  }

  return null;
}

async function main() {
  try {
    let input = '';
    for await (const chunk of process.stdin) input += chunk;
    if (!input.trim()) throw new Error('payload vazio');
    const payload = JSON.parse(input);
    if (!payload || typeof payload !== 'object' || Array.isArray(payload) || !String(payload.tool_name ?? '').trim()) {
      throw new Error('payload sem tool_name válido');
    }
    const reason = evaluateHookPayload(payload);
    if (reason) {
      process.stderr.write(`${reason}\n`);
      process.exitCode = 2;
    }
  } catch (error) {
    process.stderr.write(`Hook de segurança falhou ao analisar a chamada; operação bloqueada por segurança: ${error.message}\n`);
    process.exitCode = 2;
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
