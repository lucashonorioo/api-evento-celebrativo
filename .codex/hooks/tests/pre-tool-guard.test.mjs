import test from 'node:test';
import assert from 'node:assert/strict';
import {
  evaluateHookPayload,
  evaluatePatch,
  evaluateShellCommand,
  extractPatchEntries,
} from '../pre-tool-guard.mjs';

const repoOptions = { cwd: '/repo', projectDir: '/repo' };

test('allows normal read-only git commands', () => {
  assert.equal(evaluateShellCommand('git status --short'), null);
  assert.equal(evaluateShellCommand('git -C . diff --check'), null);
});

test('blocks destructive git commands including global-option variants', () => {
  for (const command of [
    'git reset --hard HEAD~1',
    'git -C . reset --hard HEAD',
    'git -c core.autocrlf=false reset --hard HEAD',
    'git.exe -C . clean -fdx',
    '"C:/Program Files/Git/bin/git.exe" -C . reset --hard HEAD',
    'git push --force origin main',
  ]) {
    assert.notEqual(evaluateShellCommand(command), null, command);
  }
});

test('extracts patch paths', () => {
  const entries = extractPatchEntries(`*** Begin Patch\n*** Update File: src/app/test.ts\n*** Add File: src/app/new.ts\n*** End Patch`);
  assert.deepEqual(entries, [
    { operation: 'update', path: 'src/app/test.ts' },
    { operation: 'add', path: 'src/app/new.ts' },
  ]);
});

test('blocks sensitive files and .git but allows safe examples', () => {
  assert.match(evaluatePatch('*** Begin Patch\n*** Update File: .env.local\n*** End Patch', { ...repoOptions, isTracked: () => false }), /\.env/i);
  assert.equal(evaluatePatch('*** Begin Patch\n*** Add File: .env.example\n*** End Patch', { ...repoOptions, isTracked: () => false }), null);
  assert.match(evaluatePatch('*** Begin Patch\n*** Add File: certs/private.key\n*** End Patch', { ...repoOptions, isTracked: () => false }), /chave/i);
  assert.match(evaluatePatch('*** Begin Patch\n*** Update File: .git/config\n*** End Patch', { ...repoOptions, isTracked: () => false }), /\.git/i);
});

test('Flyway: tracked bloqueia; nova/untracked pode receber Add e Update subsequente', () => {
  const updateOld = '*** Begin Patch\n*** Update File: src/main/resources/db/migration/V2__create_table.sql\n*** End Patch';
  const addNew = '*** Begin Patch\n*** Add File: src/main/resources/db/migration/V99__new_change.sql\n*** End Patch';
  const updateNew = '*** Begin Patch\n*** Update File: src/main/resources/db/migration/V99__new_change.sql\n*** End Patch';

  assert.match(evaluatePatch(updateOld, { ...repoOptions, isTracked: () => true }), /Migration Flyway/i);
  assert.equal(evaluatePatch(addNew, { ...repoOptions, isTracked: () => false }), null);
  assert.equal(evaluatePatch(updateNew, { ...repoOptions, isTracked: () => false }), null);
  assert.match(evaluatePatch(updateNew, { ...repoOptions, isTracked: () => true }), /Migration Flyway/i);
});

test('shell mutation obeys the same .env/.git/Flyway policy in Codex', () => {
  const tracked = { ...repoOptions, isTracked: () => true };
  assert.match(evaluateShellCommand('echo SECRET=1 > .env', tracked), /ambiente real/);
  assert.match(evaluateShellCommand('echo x > .git/config', tracked), /\.git/);
  assert.match(evaluateShellCommand('sed -i s/a/b/ src/main/resources/db/migration/V2__create_table.sql', tracked), /Migration Flyway/);
  assert.equal(evaluateShellCommand('cat .env', tracked), null);
});

test('evaluates canonical hook payloads', () => {
  const reason = evaluateHookPayload({
    tool_name: 'Bash',
    tool_input: { command: 'git -C . branch -D feature/test' },
    cwd: '/repo',
  });
  assert.match(reason, /branch/i);

  const patchReason = evaluateHookPayload({
    tool_name: 'apply_patch',
    tool_input: { patch: '*** Begin Patch\n*** Update File: .git/config\n*** End Patch' },
    cwd: '/repo',
  }, { projectDir: '/repo', isTracked: () => false });
  assert.match(patchReason, /\.git/i);
});

test('Codex herda bloqueio de wrappers destrutivos e evita falso positivo de leitura', () => {
  assert.match(evaluateShellCommand('powershell -Command "git reset --hard HEAD"'), /descartar/i);
  assert.match(evaluateShellCommand('bash -c "git push --force origin main"'), /Force push/i);
  assert.equal(evaluateShellCommand('grep "foo > \\.env" README.md', { ...repoOptions, isTracked: () => true }), null);
});

test('hook Codex falha fechado se o payload de segurança for inválido', async () => {
  const { spawnSync } = await import('node:child_process');
  const { fileURLToPath } = await import('node:url');
  const script = fileURLToPath(new URL('../pre-tool-guard.mjs', import.meta.url));
  const result = spawnSync(process.execPath, [script], {
    input: '{payload-json-invalido', encoding: 'utf8',
  });
  assert.equal(result.status, 0);
  const output = JSON.parse(result.stdout);
  assert.equal(output.hookSpecificOutput?.permissionDecision, 'deny');
  assert.match(output.hookSpecificOutput?.permissionDecisionReason ?? '', /operação bloqueada por segurança/i);
});

test('Codex herda bloqueio dos novos vetores adversariais do guard compartilhado', () => {
  for (const command of [
    'alias gg=git; gg reset --hard HEAD',
    "$env:G='git'; & $env:G reset --hard HEAD",
    'set G=git && %G% reset --hard HEAD',
    'echo reset --hard HEAD | xargs git',
    'git branch --delete --force feature/test',
    'git branch -d -f feature/test',
    'ssh host "git reset --hard HEAD"',
    'docker exec app git clean -fd',
  ]) assert.notEqual(evaluateShellCommand(command), null, command);

  const tracked = { ...repoOptions, isTracked: () => true };
  assert.match(evaluateShellCommand("python -c \"import shutil; shutil.copy('src','.env')\"", tracked), /ambiente real/);
  assert.match(evaluateShellCommand("powershell -Command \"[System.IO.File]::WriteAllText('.env','x')\"", tracked), /ambiente real/);
  assert.equal(evaluateShellCommand("node -e \"const f=(x)=>x; console.log('.env')\"", tracked), null);
});

test('stdin vazio e payload sem tool_name são negados pelo hook Codex', async () => {
  const { spawnSync } = await import('node:child_process');
  const { fileURLToPath } = await import('node:url');
  const script = fileURLToPath(new URL('../pre-tool-guard.mjs', import.meta.url));
  for (const input of ['', '{}', 'null', '[]']) {
    const result = spawnSync(process.execPath, [script], { input, encoding: 'utf8' });
    assert.equal(result.status, 0);
    const output = JSON.parse(result.stdout);
    assert.equal(output.hookSpecificOutput?.permissionDecision, 'deny');
  }
});

test('Codex herda hardening de alias/EncodedCommand/glob e bloqueio de lifecycle fabricado', () => {
  const commands = [
    'git -c alias.wipe="reset --hard" wipe',
    'powershell -EncodedCommand ZwBpAHQAIAByAGUAcwBlAHQAIAAtAC0AaABhAHIAZAA=',
    'git checkout -- src/*',
    'p1=.e; p2=nv; echo SECRET=1 > "$p1$p2"',
    'node .codex/hooks/engineering-review-gate.mjs --subagent-stop',
  ];
  for (const command of commands) assert.notEqual(evaluateShellCommand(command, { ...repoOptions, isTracked: () => true }), null, command);
});
