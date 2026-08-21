import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { recordEngineeringMutation } from './engineering-review-gate.mjs';

export function runDiffCheck(cwd) {
  try {
    execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], {
      encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 5000,
    });
  } catch {
    return null;
  }

  try {
    execFileSync('git', ['-C', cwd, 'diff', '--check'], {
      encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 10000,
    });
    return null;
  } catch (error) {
    const stdout = String(error.stdout ?? '').trim();
    const stderr = String(error.stderr ?? '').trim();
    return (stdout || stderr || 'git diff --check retornou erro').slice(0, 1800);
  }
}

async function main() {
  try {
    let input = '';
    for await (const chunk of process.stdin) input += chunk;
    const payload = JSON.parse(input || '{}');
    const cwd = String(payload.cwd || process.cwd());
    recordEngineeringMutation(payload);
    const issue = runDiffCheck(cwd);

    if (issue) {
      process.stdout.write(JSON.stringify({
        systemMessage: 'A verificação leve do diff encontrou problemas de whitespace.',
        hookSpecificOutput: {
          hookEventName: 'PostToolUse',
          additionalContext: `Corrija os problemas indicados por git diff --check antes de concluir:\n${issue}`,
        },
      }));
    }
  } catch (error) {
    process.stdout.write(JSON.stringify({
      systemMessage: `Hook pós-edição não conseguiu executar a verificação: ${error.message}`,
    }));
    process.exitCode = 1;
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
