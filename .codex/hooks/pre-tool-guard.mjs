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
  evaluatePatch,
  evaluateShellCommand,
  gitProjectRoot,
} from '../../.ai/guards/safety-policy.mjs';

export function evaluateHookPayload(payload, options = {}) {
  const toolName = String(payload?.tool_name ?? '');
  const cwd = payload?.cwd ?? process.cwd();
  const projectDir = options.projectDir ?? gitProjectRoot(cwd) ?? cwd;
  const toolInput = payload?.tool_input ?? {};

  if (toolName === 'Bash' || toolName === 'PowerShell') {
    return evaluateShellCommand(toolInput.command, { cwd, projectDir, isTracked: options.isTracked });
  }

  if (toolName === 'apply_patch') {
    const patchText = toolInput.patch ?? toolInput.command ?? toolInput.input ?? '';
    return evaluatePatch(patchText, { cwd, projectDir, isTracked: options.isTracked });
  }

  if (toolName === 'Edit' || toolName === 'Write') {
    if (toolInput.file_path) {
      return evaluateFileWrite({
        filePath: toolInput.file_path,
        cwd,
        projectDir,
        isTracked: options.isTracked,
      });
    }
    const patchText = toolInput.patch ?? toolInput.command ?? toolInput.input ?? '';
    return evaluatePatch(patchText, { cwd, projectDir, isTracked: options.isTracked });
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
    if (!input.trim()) throw new Error('payload vazio');
    const payload = JSON.parse(input);
    if (!payload || typeof payload !== 'object' || Array.isArray(payload) || !String(payload.tool_name ?? '').trim()) {
      throw new Error('payload sem tool_name válido');
    }
    const reason = evaluateHookPayload(payload);
    if (reason) deny(reason);
  } catch (error) {
    deny(`Hook de segurança falhou ao analisar a chamada; operação bloqueada por segurança: ${error.message}`);
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
