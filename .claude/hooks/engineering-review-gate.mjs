import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const RUNTIME_EXTENSIONS = new Set([
  '.java', '.kt', '.kts', '.ts', '.tsx', '.js', '.mjs', '.cjs',
  '.html', '.css', '.scss', '.sass', '.less', '.sql', '.xml',
  '.yml', '.yaml', '.properties', '.json', '.toml', '.gradle',
  '.ps1', '.sh', '.bat', '.cmd',
]);

const RUNTIME_FILENAMES = new Set([
  'Dockerfile', 'Jenkinsfile', 'Makefile', 'pom.xml', 'package.json',
  'angular.json', 'compose.yml', 'compose.yaml', 'docker-compose.yml',
  'docker-compose.yaml',
]);

const EXCLUDED_PREFIXES = [
  '.agents/',
  '.ai/',
  '.claude/',
  'graphify-out/',
];

function normalizeRelativePath(value) {
  return String(value ?? '').replaceAll('\\', '/').replace(/^\.\//, '');
}

function resolveProjectRelative(filePath, cwd, projectDir) {
  if (!filePath) return null;
  const root = path.resolve(projectDir || cwd || process.cwd());
  const absolute = path.isAbsolute(filePath)
    ? path.resolve(filePath)
    : path.resolve(cwd || root, filePath);
  const relative = path.relative(root, absolute);
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) return null;
  return normalizeRelativePath(relative);
}

export function isEngineeringChangePath(filePath, {
  cwd = process.cwd(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || cwd,
} = {}) {
  const relative = resolveProjectRelative(filePath, cwd, projectDir);
  if (!relative) return false;

  const lower = relative.toLowerCase();
  if (EXCLUDED_PREFIXES.some((prefix) => lower.startsWith(prefix))) return false;

  const basename = path.basename(relative);
  if (RUNTIME_FILENAMES.has(basename)) return true;
  if (/^tsconfig(?:\.[^.]+)?\.json$/i.test(basename)) return true;
  if (/^application(?:-[^.]+)?\.properties$/i.test(basename)) return true;
  if (/^application(?:-[^.]+)?\.ya?ml$/i.test(basename)) return true;

  return RUNTIME_EXTENSIONS.has(path.extname(relative).toLowerCase());
}

function stateDirectory(tempRoot = tmpdir()) {
  return path.join(tempRoot, 'evento-celebrativo-engineering-review');
}

function statePath(sessionId, tempRoot = tmpdir()) {
  const key = createHash('sha256').update(String(sessionId)).digest('hex').slice(0, 24);
  return path.join(stateDirectory(tempRoot), `${key}.json`);
}

function readState(sessionId, tempRoot = tmpdir()) {
  if (!sessionId) return null;
  const file = statePath(sessionId, tempRoot);
  if (!existsSync(file)) return null;
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
}

function writeState(sessionId, state, tempRoot = tmpdir()) {
  const dir = stateDirectory(tempRoot);
  mkdirSync(dir, { recursive: true });
  writeFileSync(statePath(sessionId, tempRoot), `${JSON.stringify(state)}\n`, 'utf8');
}

export function clearReviewState(sessionId, tempRoot = tmpdir()) {
  if (!sessionId) return;
  rmSync(statePath(sessionId, tempRoot), { force: true });
}

function git(cwd, args) {
  return execFileSync('git', ['-C', cwd, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    timeout: 10000,
  }).trim();
}

function repositoryRoot(cwd) {
  try {
    return git(cwd, ['rev-parse', '--show-toplevel']);
  } catch {
    return null;
  }
}

function changedPaths(repoRoot) {
  const tracked = git(repoRoot, ['diff', '--name-only', 'HEAD'])
    .split(/\r?\n/)
    .filter(Boolean);
  const untracked = git(repoRoot, ['ls-files', '--others', '--exclude-standard'])
    .split(/\r?\n/)
    .filter(Boolean);
  return [...new Set([...tracked, ...untracked])].sort();
}

function fileSignature(repoRoot, relativePath) {
  const absolute = path.join(repoRoot, relativePath);
  if (!existsSync(absolute)) return 'DELETED';
  try {
    return git(repoRoot, ['hash-object', '--', relativePath]);
  } catch {
    return 'UNREADABLE';
  }
}

export function engineeringWorkingTreeFingerprint({
  cwd = process.cwd(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || cwd,
} = {}) {
  const repoRoot = repositoryRoot(cwd);
  if (!repoRoot) return null;

  const relevant = changedPaths(repoRoot).filter((relativePath) => {
    const absolute = path.join(repoRoot, relativePath);
    return isEngineeringChangePath(absolute, { cwd: repoRoot, projectDir });
  });

  const hash = createHash('sha256');
  for (const relativePath of relevant) {
    hash.update(relativePath);
    hash.update('\0');
    hash.update(fileSignature(repoRoot, relativePath));
    hash.update('\0');
  }
  return hash.digest('hex');
}

export function recordSessionBaseline(payload, {
  tempRoot = tmpdir(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || payload?.cwd,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) return false;

  const fingerprint = engineeringWorkingTreeFingerprint({ cwd: payload?.cwd, projectDir });
  writeState(sessionId, {
    baselineFingerprint: fingerprint,
  }, tempRoot);
  return true;
}

export function recordEngineeringEdit(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || payload?.cwd,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  const filePath = payload?.tool_input?.file_path;
  if (!sessionId || !isEngineeringChangePath(filePath, { cwd: payload?.cwd, projectDir })) return false;

  const previous = readState(sessionId, tempRoot) ?? {};
  writeState(sessionId, {
    ...previous,
    lastEngineeringEditAt: now,
    lastEngineeringPath: resolveProjectRelative(filePath, payload?.cwd, projectDir),
  }, tempRoot);
  return true;
}

export function hasEngineeringReviewPass(message) {
  const normalized = String(message ?? '').replaceAll('*', '');
  return /(?:^|\n)\s*Engineering review:\s*PASS(?: WITH NOTES)?\s*(?:$|\n)/i.test(normalized);
}

function markReviewedBaseline(sessionId, state, fingerprint, tempRoot) {
  if (fingerprint === null && !state?.baselineFingerprint) {
    clearReviewState(sessionId, tempRoot);
    return;
  }
  writeState(sessionId, { baselineFingerprint: fingerprint }, tempRoot);
}

export function evaluateStopGate(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir = process.env.CLAUDE_PROJECT_DIR || payload?.cwd,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) return null;

  const state = readState(sessionId, tempRoot);
  if (!state) return null;

  const currentFingerprint = engineeringWorkingTreeFingerprint({ cwd: payload?.cwd, projectDir });
  const baselineKnown = Object.prototype.hasOwnProperty.call(state, 'baselineFingerprint');
  const changedFromBaseline = baselineKnown && currentFingerprint !== state.baselineFingerprint;
  const fallbackEditDetected = !baselineKnown && Boolean(state.lastEngineeringEditAt);
  const engineeringChanged = changedFromBaseline || fallbackEditDetected;

  if (!engineeringChanged) {
    if (baselineKnown) writeState(sessionId, { baselineFingerprint: currentFingerprint }, tempRoot);
    return null;
  }

  if (hasEngineeringReviewPass(payload?.last_assistant_message)) {
    markReviewedBaseline(sessionId, state, currentFingerprint, tempRoot);
    return null;
  }

  if (state.reviewRequestedFingerprint !== currentFingerprint || !state.reviewRequestedAt) {
    writeState(sessionId, {
      ...state,
      reviewRequestedAt: now,
      reviewRequestedFingerprint: currentFingerprint,
    }, tempRoot);
  }

  return {
    decision: 'block',
    reason: [
      'Gate de revisão de engenharia: houve alteração executável nesta sessão e ainda não há um veredito final de revisão.',
      'Antes de concluir, execute a Skill review-change seguindo .ai/review/ENGINEERING_REVIEW.md.',
      'Se houver CHANGES_REQUIRED, corrija apenas achados relacionados à tarefa, reexecute as validações afetadas e faça uma revisão final após a última correção.',
      'A resposta final só pode ser encerrada quando o veredito for PASS ou PASS WITH NOTES e deve conter exatamente uma linha técnica: "Engineering review: PASS" ou "Engineering review: PASS WITH NOTES".',
      state.lastEngineeringPath ? `Último arquivo executável editado por ferramenta: ${state.lastEngineeringPath}.` : '',
    ].filter(Boolean).join(' '),
  };
}

async function readPayload() {
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return JSON.parse(input || '{}');
}

async function main() {
  try {
    const payload = await readPayload();
    if (process.argv.includes('--session-start')) {
      recordSessionBaseline(payload);
      return;
    }
    const output = evaluateStopGate(payload);
    if (output) process.stdout.write(JSON.stringify(output));
  } catch (error) {
    process.stderr.write(`Gate de revisão de engenharia não conseguiu avaliar a sessão e não bloqueou a conclusão: ${error.message}\n`);
  }
}

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await main();
