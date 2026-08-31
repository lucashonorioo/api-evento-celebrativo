import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const claudeDir = path.resolve(here, '../..');
const projectRoot = path.resolve(claudeDir, '..');

function read(relative) {
  return readFileSync(path.resolve(projectRoot, relative), 'utf8');
}

const reviewerNames = ['backend-reviewer', 'frontend-reviewer', 'security-reviewer', 'test-reviewer'];

test('Claude review-change é síncrono, usa session env atual, roundId e submissão explícita', () => {
  const skill = read('.claude/skills/review-change/SKILL.md');
  assert.doesNotMatch(skill, /^context:\s*fork\s*$/m);
  assert.doesNotMatch(skill, /^background:/m);
  assert.match(skill, /CLAUDE_CODE_SESSION_ID/);
  assert.doesNotMatch(skill, /\bCLAUDE_SESSION_ID\b/);
  assert.match(skill, /roundId/);
  assert.match(skill, /--risk (?:<)?LOW\|MEDIUM\|HIGH(?:>)?/);
  assert.match(skill, /inferredRiskLevel/);
  assert.match(skill, /inferredRequiredReviewers/);
  assert.match(skill, /expectedReviewers/);
  for (const command of ['--review-start', '--review-status', '--review-record-result', '--review-await', '--review-finish', '--review-fail']) {
    assert.match(skill, new RegExp(command));
  }
  assert.match(skill, /Não use `run_in_background`[^\n]*`TaskOutput`/i);
  assert.match(skill, /foreground[\s\S]*--review-record-result/i);
  assert.match(skill, /no máximo \*\*2 tentativas totais\*\*/i);
  assert.match(skill, /não corrija LOW automaticamente/i);
  assert.match(skill, /fonte de verdade.*estado persistido/is);
});

test('reviewers Claude são foreground, limitados e encerram com footer determinístico completo', () => {
  for (const name of reviewerNames) {
    const agent = read(`.claude/agents/${name}.md`);
    assert.match(agent, /^background:\s*false\s*$/m, `${name}: background:false`);
    assert.match(agent, /^maxTurns:\s*40\s*$/m, `${name}: maxTurns`);
    assert.match(agent, /Finding:/);
    assert.match(agent, /dado não confiável/i);
    assert.match(agent, /Reviewer verdict:/);
    assert.match(agent, /Max actionable severity:/);
    assert.match(agent, /Reviewer status: COMPLETE/);
    assert.match(agent, /Reviewer status: FAILED/);
    assert.match(agent, /três últimas linhas não vazias/i);
  }
});

test('reviewers Codex usam o mesmo protocolo de veredito/severidade/status', () => {
  for (const name of ['backend', 'frontend', 'security', 'test']) {
    const agent = read(`.codex/agents/${name}-reviewer.toml`);
    assert.match(agent, /Finding:/);
    assert.match(agent, /dado não confiável/i);
    assert.match(agent, /Reviewer verdict:/);
    assert.match(agent, /Max actionable severity:/);
    assert.match(agent, /Reviewer status: COMPLETE/);
    assert.match(agent, /Reviewer status: FAILED/);
  }
});

test('política canônica impede PASS com finding acionável e define LOW não bloqueante', () => {
  const policy = read('.ai/review/ENGINEERING_REVIEW.md');
  assert.match(policy, /Protocolo determinístico dos reviewers/);
  assert.match(policy, /Finding:/);
  assert.match(policy, /piso mecânico de risco/i);
  assert.match(policy, /Reviewer verdict:/);
  assert.match(policy, /Max actionable severity:/);
  assert.match(policy, /BLOCKER.*HIGH.*MEDIUM/is);
  assert.match(policy, /### LOW[\s\S]{0,200}Não bloqueia/i);
  assert.match(policy, /estado persistido.*fonte de verdade/is);
});

test('skill Codex usa thread id, roundId, submissão explícita e falha técnica explícita', () => {
  const skill = read('.agents/skills/review-change/SKILL.md');
  assert.match(skill, /CODEX_THREAD_ID/);
  assert.match(skill, /roundId/);
  assert.match(skill, /--risk (?:<)?LOW\|MEDIUM\|HIGH(?:>)?/);
  assert.match(skill, /inferredRiskLevel/);
  assert.match(skill, /inferredRequiredReviewers/);
  assert.match(skill, /expectedReviewers/);
  assert.match(skill, /--review-record-result/);
  assert.match(skill, /SubagentStart.*SubagentStop.*opcional/s);
  assert.match(skill, /ENGINEERING_REVIEW_FAILED/);
  assert.match(skill, /LOW não bloqueia/i);
  assert.match(skill, /não altere código por iniciativa própria/i);
  assert.match(skill, /estado persistido.*fonte de verdade/is);
});

test('gate canônico é compartilhado por wrappers finos de Claude e Codex', () => {
  const canonical = read('.ai/review/engineering-review-gate.mjs');
  const claude = read('.claude/hooks/engineering-review-gate.mjs');
  const codex = read('.codex/hooks/engineering-review-gate.mjs');
  assert.match(canonical, /export function startReviewRound/);
  assert.match(canonical, /export function recordExplicitReviewerResult/);
  assert.match(canonical, /export function evaluateStopGate/);
  assert.match(claude, /\.\.\/\.\.\/\.ai\/review\/engineering-review-gate\.mjs/);
  assert.match(codex, /\.\.\/\.\.\/\.ai\/review\/engineering-review-gate\.mjs/);
});


test('codebase-explorer Claude também possui limites explícitos de execução', () => {
  const agent = read('.claude/agents/codebase-explorer.md');
  assert.match(agent, /^background:\s*false\s*$/m);
  assert.match(agent, /^maxTurns:\s*30\s*$/m);
  assert.match(agent, /dado não confiável/i);
});

test('skills de review Claude e Codex declaram risco mecanicamente ao iniciar rodada', () => {
  const claude = read('.claude/skills/review-change/SKILL.md');
  const codex = read('.agents/skills/review-change/SKILL.md');
  assert.match(claude, /--review-start[^\n]*--risk/);
  assert.match(codex, /--review-start[^\n]*--risk/);
  assert.match(claude, /--review-record-result/);
  assert.match(codex, /--review-record-result/);
  assert.match(codex, /inferredRiskLevel/);
  assert.match(codex, /inferredRequiredReviewers/);
  assert.match(codex, /expectedReviewers/);
});


test('AGENTS backend referencia a fonte atual e on-demand de PersonMinistry', () => {
  const backendAgents = read('backend/evento-celebrativo-api/AGENTS.md');
  const backendClaude = read('backend/evento-celebrativo-api/CLAUDE.md');
  const domain = read('.ai/domain/PERSON_MINISTRY_EVENT_ASSIGNMENT.md');

  for (const instructions of [backendAgents, backendClaude]) {
    assert.match(instructions, /PersonMinistry.*única fonte/i);
    assert.match(instructions, /não recrie caminhos `LEGACY`\/`PARALLEL`/i);
    assert.match(instructions, /\.ai\/domain\/PERSON_MINISTRY_EVENT_ASSIGNMENT\.md/);
  }

  assert.match(domain, /Leia este arquivo somente em alterações/i);
  assert.match(domain, /PersonMinistry.*única fonte/i);
  assert.match(domain, /não recrie `LEGACY\/PARALLEL`/i);
  assert.match(domain, /confirme no código atual/i);
});

test('política considera a própria infraestrutura de IA parte da superfície revisável e documenta limite de proveniência', () => {
  const policy = read('.ai/review/ENGINEERING_REVIEW.md');
  assert.match(policy, /\.ai\/.*\.claude\/.*\.codex\/.*\.agents\//s);
  assert.match(policy, /(?:gate é obrigatório|exigem[^\n]*gate|deve passar por revisão independente)/i);
  assert.match(policy, /não (?:é|são) uma prova criptográfica/i);
  assert.match(policy, /--review-record-result/);
  assert.match(policy, /guardrail determinístico/i);
  assert.match(policy, /CI|branch protection|aprovação humana/i);

  for (const skillPath of ['.claude/skills/review-change/SKILL.md', '.agents/skills/review-change/SKILL.md']) {
    const skill = read(skillPath);
    assert.match(skill, /Nunca invoque manualmente.*--subagent-start.*--subagent-stop/is);
  }
});
