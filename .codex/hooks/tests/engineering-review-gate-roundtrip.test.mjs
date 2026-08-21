import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { clearReviewState } from '../engineering-review-gate.mjs';

const hooksDir = path.dirname(fileURLToPath(new URL('../engineering-review-gate.mjs', import.meta.url)));
const gate = path.join(hooksDir, 'engineering-review-gate.mjs');
const postEdit = path.join(hooksDir, 'post-edit-check.mjs');

function fixture() {
  const repo = mkdtempSync(path.join(tmpdir(), 'codex-review-roundtrip-'));
  execFileSync('git', ['init', '-q', repo]);
  execFileSync('git', ['-C', repo, 'config', 'user.email', 'test@example.com']);
  execFileSync('git', ['-C', repo, 'config', 'user.name', 'Codex Gate Test']);
  const dir = path.join(repo, 'backend/src/main/java');
  mkdirSync(dir, { recursive: true });
  writeFileSync(path.join(dir, 'App.java'), 'class App {}\n');
  execFileSync('git', ['-C', repo, 'add', '.']);
  execFileSync('git', ['-C', repo, 'commit', '-q', '-m', 'initial']);
  return repo;
}

function run(script, args, cwd, payload, options = {}) {
  const result = spawnSync(process.execPath, [script, ...args], {
    cwd,
    input: payload === undefined
      ? undefined
      : typeof payload === 'string'
        ? payload
        : JSON.stringify(payload),
    encoding: 'utf8',
    env: { ...process.env, ...(options.env ?? {}) },
  });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout ? JSON.parse(result.stdout) : {};
}

function reviewerMessage({ verdict = 'PASS', severity = 'NONE', body = 'review ok' } = {}) {
  const finding = severity === 'NONE'
    ? 'Finding: NONE'
    : `Finding: ${severity} | backend/src/main/java/App.java | observação do reviewer`;
  return `${body}\n${finding}\nReviewer verdict: ${verdict}\nMax actionable severity: ${severity}\nReviewer status: COMPLETE\n`;
}

function recordResult(repo, session, started, reviewer, message = reviewerMessage(), options = {}) {
  if (options.resultFile) {
    const resultPath = path.join(repo, `review-result-${reviewer}-${Date.now()}-${Math.random()}.txt`);
    writeFileSync(resultPath, message);
    return run(gate, [
      '--review-record-result', session, started.roundId, reviewer,
      '--agent-id', options.agentId ?? `foreground-${reviewer}`,
      '--result-file', resultPath,
    ], repo);
  }
  return run(gate, [
    '--review-record-result', session, started.roundId, reviewer,
    '--agent-id', options.agentId ?? `foreground-${reviewer}`,
  ], repo, message);
}

function completeReviewersExplicit(repo, session, started, options = {}) {
  let index = 0;
  for (const reviewer of started.expectedReviewers) {
    recordResult(
      repo,
      session,
      started,
      reviewer,
      options.messages?.[reviewer] ?? reviewerMessage(),
      { agentId: `${options.prefix ?? 'explicit'}-${reviewer}-${index}` },
    );
    index += 1;
  }
}

function completeReviewers(repo, session, started, options = {}) {
  completeReviewersExplicit(repo, session, started, options);
}

test('Codex executa round-trip real SessionStart -> edit -> reviewer -> PASS -> Stop', () => {
  const repo = fixture();
  const session = `codex-roundtrip-${Date.now()}-${Math.random()}`;
  run(gate, ['--session-start'], repo, { session_id: session, cwd: repo, source: 'startup' });

  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int changed; }\n');
  run(postEdit, [], repo, { session_id: session, cwd: repo, tool_name: 'apply_patch', tool_input: {} });

  const started = run(gate, ['--review-start', session], repo);
  assert.equal(started.riskLevel, 'MEDIUM');
  assert.deepEqual(started.expectedReviewers, ['backend-reviewer']);

  run(gate, ['--subagent-start'], repo, {
    session_id: session, cwd: repo, agent_id: 'codex-backend-1', agent_type: 'backend_reviewer',
  });
  run(gate, ['--subagent-stop'], repo, {
    session_id: session, cwd: repo, agent_id: 'codex-backend-1', agent_type: 'backend_reviewer',
    last_assistant_message: 'Finding: NONE\nReviewer verdict: PASS\nMax actionable severity: NONE\nReviewer status: COMPLETE\n',
  });

  const finished = run(gate, ['--review-finish', session, started.roundId, 'PASS'], repo);
  assert.equal(finished.status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: session, cwd: repo }), {});
  clearReviewState(session);
});


test('Codex executa round-trip primario sem lifecycle automatico de subagents', () => {
  const repo = fixture();
  const session = `codex-explicit-${Date.now()}-${Math.random()}`;
  run(gate, ['--session-start'], repo, { session_id: session, cwd: repo, source: 'startup' });

  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int explicit; }\n');
  run(postEdit, [], repo, { session_id: session, cwd: repo, tool_name: 'apply_patch', tool_input: {} });

  const started = run(gate, ['--review-start', session, '--risk', 'HIGH', 'backend_reviewer', 'security_reviewer'], repo);
  assert.ok(started.expectedReviewers.includes('backend-reviewer'));
  assert.ok(started.expectedReviewers.includes('security-reviewer'));

  let recorded = recordResult(repo, session, started, 'backend_reviewer', reviewerMessage(), {
    agentId: 'codex-real-backend',
  });
  assert.equal(recorded.ok, true);
  assert.equal(recorded.reviewer, 'backend-reviewer');
  assert.ok(recorded.pendingReviewers.includes('security-reviewer'));

  recorded = recordResult(repo, session, started, 'security-reviewer', reviewerMessage(), {
    agentId: 'codex-real-security',
    resultFile: true,
  });
  assert.equal(recorded.ok, true);

  for (const reviewer of recorded.pendingReviewers) {
    recordResult(repo, session, started, reviewer, reviewerMessage(), {
      agentId: `codex-real-${reviewer}`,
    });
  }

  const status = run(gate, ['--review-status', session, started.roundId], repo);
  assert.deepEqual(status.pendingReviewers, []);
  assert.equal(run(gate, ['--review-finish', session, started.roundId, 'PASS'], repo).status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: session, cwd: repo }), {});
  clearReviewState(session);
});
test('Codex E2E principal: PASS WITH NOTES fica stale após LOW corrigido e segunda rodada PASS libera Stop', () => {
  const repo = fixture();
  const session = `codex-e2e-second-${Date.now()}-${Math.random()}`;
  run(gate, ['--session-start'], repo, { session_id: session, cwd: repo, source: 'startup' });

  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int low; }\n');
  run(postEdit, [], repo, { session_id: session, cwd: repo, tool_name: 'apply_patch', tool_input: {} });
  const first = run(gate, ['--review-start', session, '--risk', 'MEDIUM', 'backend_reviewer'], repo);
  completeReviewers(repo, session, first, {
    prefix: 'r1',
    messages: {
      'backend-reviewer': reviewerMessage({
        body: 'Código morto não bloqueante.',
        verdict: 'PASS WITH NOTES',
        severity: 'LOW',
      }),
    },
  });
  assert.equal(run(gate, ['--review-finish', session, first.roundId, 'PASS_WITH_NOTES'], repo).status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: session, cwd: repo }), {});

  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int lowFixed; }\n');
  run(postEdit, [], repo, { session_id: session, cwd: repo, tool_name: 'apply_patch', tool_input: {} });
  assert.match(run(gate, [], repo, { session_id: session, cwd: repo }).reason ?? '', /review-change|alteração executável/i);

  const second = run(gate, ['--review-start', session, '--risk', 'MEDIUM', 'backend_reviewer'], repo);
  assert.notEqual(second.roundId, first.roundId);
  completeReviewers(repo, session, second, { prefix: 'r2' });
  assert.equal(run(gate, ['--review-finish', session, second.roundId, 'PASS'], repo).status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: session, cwd: repo }), {});
  clearReviewState(session);
});

test('Codex E2E nova sessão: CODEX_THREAD_ID sem state recupera conservadoramente e permite review legítimo', () => {
  const repo = fixture();
  const sessionA = `codex-session-a-${Date.now()}-${Math.random()}`;
  run(gate, ['--session-start'], repo, { session_id: sessionA, cwd: repo, source: 'startup' });
  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int featureAlreadyHere; }\n');
  run(postEdit, [], repo, { session_id: sessionA, cwd: repo, tool_name: 'apply_patch', tool_input: {} });

  const sessionB = `codex-session-b-${Date.now()}-${Math.random()}`;
  const started = run(gate, ['--review-start', '-', '--risk', 'LOW'], repo, undefined, {
    env: { CODEX_THREAD_ID: sessionB },
  });
  assert.equal(started.scopeUnknown, true);
  assert.equal(started.riskLevel, 'HIGH');
  assert.deepEqual(started.expectedReviewers, [
    'backend-reviewer', 'frontend-reviewer', 'security-reviewer', 'test-reviewer',
  ]);

  completeReviewers(repo, sessionB, started, { prefix: 'session-b' });
  assert.equal(run(gate, ['--review-finish', sessionB, started.roundId, 'PASS'], repo).status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: sessionB, cwd: repo }), {});
  clearReviewState(sessionA);
  clearReviewState(sessionB);
});

test('Codex E2E falha técnica: reviewer desaparece, rodada falha, nova tentativa PASS libera Stop', () => {
  const repo = fixture();
  const session = `codex-e2e-failure-${Date.now()}-${Math.random()}`;
  run(gate, ['--session-start'], repo, { session_id: session, cwd: repo, source: 'startup' });
  writeFileSync(path.join(repo, 'backend/src/main/java/App.java'), 'class App { int failureFlow; }\n');
  run(postEdit, [], repo, { session_id: session, cwd: repo, tool_name: 'apply_patch', tool_input: {} });

  const first = run(gate, ['--review-start', session, '--risk', 'HIGH', 'backend_reviewer', 'test_reviewer', 'security_reviewer'], repo);
  run(gate, ['--subagent-start'], repo, { session_id: session, cwd: repo, agent_id: 'backend-ok', agent_type: 'backend_reviewer' });
  run(gate, ['--subagent-start'], repo, { session_id: session, cwd: repo, agent_id: 'test-lost', agent_type: 'test_reviewer' });
  run(gate, ['--subagent-start'], repo, { session_id: session, cwd: repo, agent_id: 'security-ok', agent_type: 'security_reviewer' });
  run(gate, ['--subagent-stop'], repo, {
    session_id: session, cwd: repo, agent_id: 'backend-ok', agent_type: 'backend_reviewer',
    last_assistant_message: reviewerMessage(),
  });
  run(gate, ['--subagent-stop'], repo, {
    session_id: session, cwd: repo, agent_id: 'security-ok', agent_type: 'security_reviewer',
    last_assistant_message: reviewerMessage(),
  });

  const failed = run(gate, ['--review-await', session, first.roundId, '5'], repo);
  assert.equal(failed.status, 'REVIEW_FAILED');
  assert.equal(failed.timedOut, true);
  assert.match(failed.failureReason ?? '', /timeout aguardando reviewers pendentes/);
  assert.match(run(gate, [], repo, { session_id: session, cwd: repo }).reason ?? '', /ENGINEERING_REVIEW_FAILED/);

  const second = run(gate, ['--review-start', session, '--risk', 'HIGH', 'backend_reviewer', 'test_reviewer', 'security_reviewer'], repo);
  assert.notEqual(second.roundId, first.roundId);
  completeReviewers(repo, session, second, { prefix: 'retry' });
  assert.equal(run(gate, ['--review-finish', session, second.roundId, 'PASS'], repo).status, 'REVIEW_VALID');
  assert.deepEqual(run(gate, [], repo, { session_id: session, cwd: repo }), {});
  clearReviewState(session);
});
