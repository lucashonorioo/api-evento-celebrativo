import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  evaluateStopGate,
  hasEngineeringReviewPass,
  isEngineeringChangePath,
  recordEngineeringEdit,
  recordSessionBaseline,
} from '../engineering-review-gate.mjs';

function createRepository() {
  const directory = mkdtempSync(path.join(tmpdir(), 'engineering-review-repo-'));
  execFileSync('git', ['init', '-q', directory]);
  execFileSync('git', ['-C', directory, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', directory, 'config', 'user.name', 'Review Gate Test']);
  const sourceDir = path.join(directory, 'backend/src/main/java');
  mkdirSync(sourceDir, { recursive: true });
  writeFileSync(path.join(sourceDir, 'App.java'), 'class App {}\n');
  execFileSync('git', ['-C', directory, 'add', '.']);
  execFileSync('git', ['-C', directory, 'commit', '-q', '-m', 'initial']);
  return directory;
}

test('classifica somente alterações de engenharia relevantes', () => {
  const projectDir = path.resolve('/repo');
  assert.equal(isEngineeringChangePath('/repo/backend/src/App.java', { cwd: projectDir, projectDir }), true);
  assert.equal(isEngineeringChangePath('/repo/frontend-web/app.component.ts', { cwd: projectDir, projectDir }), true);
  assert.equal(isEngineeringChangePath('/repo/.github/workflows/ci.yml', { cwd: projectDir, projectDir }), true);
  assert.equal(isEngineeringChangePath('/repo/AGENTS.md', { cwd: projectDir, projectDir }), false);
  assert.equal(isEngineeringChangePath('/repo/.claude/settings.json', { cwd: projectDir, projectDir }), false);
  assert.equal(isEngineeringChangePath('/repo/.agents/skills/review-change/SKILL.md', { cwd: projectDir, projectDir }), false);
  assert.equal(isEngineeringChangePath('/repo/.ai/review/ENGINEERING_REVIEW.md', { cwd: projectDir, projectDir }), false);
  assert.equal(isEngineeringChangePath('/repo/graphify-out/graph.json', { cwd: projectDir, projectDir }), false);
});

test('reconhece somente o marcador técnico de aprovação', () => {
  assert.equal(hasEngineeringReviewPass('Engineering review: PASS'), true);
  assert.equal(hasEngineeringReviewPass('**Engineering review: PASS WITH NOTES**'), true);
  assert.equal(hasEngineeringReviewPass('Review executado e tudo parece bom.'), false);
  assert.equal(hasEngineeringReviewPass('Engineering review: CHANGES_REQUIRED'), false);
});

test('bloqueia conclusão após edição executável até existir PASS explícito', () => {
  const tempRoot = mkdtempSync(path.join(tmpdir(), 'engineering-review-gate-'));
  const projectDir = path.resolve('/repo');
  const editPayload = {
    session_id: 'session-a',
    cwd: projectDir,
    tool_input: { file_path: '/repo/backend/src/main/java/App.java' },
  };

  assert.equal(recordEngineeringEdit(editPayload, { now: 100, tempRoot, projectDir }), true);

  const blocked = evaluateStopGate({ session_id: 'session-a', cwd: projectDir, last_assistant_message: 'Concluído.' }, { now: 200, tempRoot, projectDir });
  assert.equal(blocked?.decision, 'block');
  assert.match(blocked?.reason ?? '', /review-change/);

  const allowed = evaluateStopGate({
    session_id: 'session-a',
    cwd: projectDir,
    last_assistant_message: 'Resumo final.\nEngineering review: PASS\n',
  }, { now: 300, tempRoot, projectDir });
  assert.equal(allowed, null);
});

test('baseline da sessão detecta alteração feita fora das ferramentas Edit/Write', () => {
  const tempRoot = mkdtempSync(path.join(tmpdir(), 'engineering-review-gate-'));
  const projectDir = createRepository();
  const payload = { session_id: 'session-shell', cwd: projectDir };

  assert.equal(recordSessionBaseline(payload, { tempRoot, projectDir }), true);
  assert.equal(evaluateStopGate({ ...payload, last_assistant_message: 'sem alteração' }, { tempRoot, projectDir }), null);

  writeFileSync(path.join(projectDir, 'backend/src/main/java/App.java'), 'class App { int value; }\n');

  const blocked = evaluateStopGate({ ...payload, last_assistant_message: 'alterado via shell' }, { now: 200, tempRoot, projectDir });
  assert.equal(blocked?.decision, 'block');

  const allowed = evaluateStopGate({
    ...payload,
    last_assistant_message: 'Engineering review: PASS',
  }, { now: 300, tempRoot, projectDir });
  assert.equal(allowed, null);

  assert.equal(evaluateStopGate({ ...payload, last_assistant_message: 'novo turno sem mudança' }, { now: 400, tempRoot, projectDir }), null);
});

test('nova alteração depois do primeiro bloqueio exige nova revisão final', () => {
  const tempRoot = mkdtempSync(path.join(tmpdir(), 'engineering-review-gate-'));
  const projectDir = createRepository();
  const payload = { session_id: 'session-b', cwd: projectDir };

  recordSessionBaseline(payload, { tempRoot, projectDir });
  writeFileSync(path.join(projectDir, 'backend/src/main/java/App.java'), 'class App { int first; }\n');
  assert.equal(evaluateStopGate({ ...payload, last_assistant_message: 'fim' }, { now: 200, tempRoot, projectDir })?.decision, 'block');

  writeFileSync(path.join(projectDir, 'backend/src/main/java/App.java'), 'class App { int second; }\n');
  const blockedAgain = evaluateStopGate({ ...payload, last_assistant_message: 'corrigido' }, { now: 400, tempRoot, projectDir });
  assert.equal(blockedAgain?.decision, 'block');

  assert.equal(evaluateStopGate({
    ...payload,
    last_assistant_message: 'Engineering review: PASS WITH NOTES',
  }, { now: 500, tempRoot, projectDir }), null);
});

test('edição apenas de instruções não arma o gate', () => {
  const tempRoot = mkdtempSync(path.join(tmpdir(), 'engineering-review-gate-'));
  const projectDir = path.resolve('/repo');
  const recorded = recordEngineeringEdit({
    session_id: 'session-c',
    cwd: projectDir,
    tool_input: { file_path: '/repo/.claude/CLAUDE.md' },
  }, { now: 100, tempRoot, projectDir });

  assert.equal(recorded, false);
  assert.equal(evaluateStopGate({ session_id: 'session-c', cwd: projectDir, last_assistant_message: 'fim' }, { now: 200, tempRoot, projectDir }), null);
});
