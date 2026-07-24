import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export function runDiffCheck(cwd) {
  try {
    execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 5000,
    });
  } catch {
    return null;
  }

  try {
    execFileSync('git', ['-C', cwd, 'diff', '--check'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 10000,
    });
    return null;
  } catch (error) {
    const stdout = String(error.stdout ?? '').trim();
    const stderr = String(error.stderr ?? '').trim();
    return (stdout || stderr || 'git diff --check retornou erro').slice(0, 1800);
  }
}

export function buildHookOutput(issue) {
  if (!issue) return null;
  return {
    hookSpecificOutput: {
      hookEventName: 'PostToolUse',
      additionalContext: `O hook encontrou problemas de whitespace no diff. Corrija antes de concluir:\n${issue}`,
    },
  };
}

async function main() {
  try {
    let input = '';
    for await (const chunk of process.stdin) input += chunk;
    const payload = JSON.parse(input || '{}');
    const cwd = String(payload.cwd || process.cwd());
    const output = buildHookOutput(runDiffCheck(cwd));
    if (output) process.stdout.write(JSON.stringify(output));
  } catch (error) {
    process.stderr.write(`Hook pós-edição não conseguiu executar a verificação: ${error.message}\n`);
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
