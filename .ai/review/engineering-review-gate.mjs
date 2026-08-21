import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const RUNTIME_EXTENSIONS = new Set([
  '.java', '.kt', '.kts', '.ts', '.tsx', '.js', '.mjs', '.cjs',
  '.html', '.css', '.scss', '.sass', '.less', '.sql', '.xml',
  '.yml', '.yaml', '.properties', '.json', '.toml', '.gradle',
  '.ps1', '.sh', '.bat', '.cmd', '.proto', '.graphql', '.gql',
  '.tf', '.tfvars', '.ini', '.conf',
]);

const RUNTIME_FILENAMES = new Set([
  'Dockerfile', 'Jenkinsfile', 'Makefile', 'pom.xml', 'package.json',
  'angular.json', 'compose.yml', 'compose.yaml', 'docker-compose.yml',
  'docker-compose.yaml', 'mvnw', 'mvnw.cmd', 'gradlew', 'gradlew.bat',
  '.npmrc',
]);

const EXCLUDED_PREFIXES = [
  'graphify-out/',
];

const AI_CONTROL_PREFIXES = [
  '.agents/',
  '.ai/',
  '.claude/',
  '.codex/',
];

const AI_CONTROL_ROOT_FILES = new Set([
  'AGENTS.md',
  'CLAUDE.md',
]);

const AI_RUNTIME_EPHEMERAL_PATHS = new Set([
  '.claude/scheduled_tasks.lock',
]);

export const REVIEW_STATUS = Object.freeze({
  NEEDS_REVIEW: 'NEEDS_REVIEW',
  REVIEW_RUNNING: 'REVIEW_RUNNING',
  REVIEW_FAILED: 'REVIEW_FAILED',
  REVIEW_VALID: 'REVIEW_VALID',
});

const CANONICAL_REVIEWER_TYPES = Object.freeze([
  'backend-reviewer',
  'frontend-reviewer',
  'security-reviewer',
  'test-reviewer',
]);

export const REVIEWER_TYPES = new Set(CANONICAL_REVIEWER_TYPES);

const REVIEWER_ALIASES = new Map([
  ...CANONICAL_REVIEWER_TYPES.map((name) => [name, name]),
  ['backend_reviewer', 'backend-reviewer'],
  ['frontend_reviewer', 'frontend-reviewer'],
  ['security_reviewer', 'security-reviewer'],
  ['test_reviewer', 'test-reviewer'],
]);

const REVIEWER_VERDICTS = new Set(['PASS', 'PASS WITH NOTES', 'CHANGES_REQUIRED']);
const REVIEWER_SEVERITIES = new Set(['NONE', 'LOW', 'MEDIUM', 'HIGH', 'BLOCKER']);
const MAX_REVIEWER_ATTEMPTS = 2;
const DEFAULT_REVIEW_AWAIT_MS = 5000;
const DEFAULT_LOCK_STALE_MS = 30000;
const RESULT_PREVIEW_LIMIT = 1600;
const STATE_VERSION = 4;
const REVIEW_RISK_LEVELS = new Set(['LOW', 'MEDIUM', 'HIGH']);
const REVIEW_RISK_RANK = Object.freeze({ LOW: 1, MEDIUM: 2, HIGH: 3 });

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
  if (AI_RUNTIME_EPHEMERAL_PATHS.has(lower)) return false;

  // Arquivos que controlam o comportamento dos agentes fazem parte da mesma
  // superfície de engenharia que eles protegem. Excluí-los permitiria alterar
  // o próprio gate/guard/reviewer sem tornar a revisão stale.
  if (AI_CONTROL_PREFIXES.some((prefix) => lower.startsWith(prefix))) return true;
  if (AI_CONTROL_ROOT_FILES.has(relative) || /(?:^|\/)(?:AGENTS|CLAUDE)\.md$/i.test(relative)) return true;

  const basename = path.basename(relative);
  if (RUNTIME_FILENAMES.has(basename)) return true;
  if (/^Dockerfile\..+/i.test(basename)) return true;
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

function lockPath(sessionId, tempRoot = tmpdir()) {
  return `${statePath(sessionId, tempRoot)}.lock`;
}

function sleepSync(milliseconds) {
  const buffer = new SharedArrayBuffer(4);
  Atomics.wait(new Int32Array(buffer), 0, 0, milliseconds);
}

function readState(sessionId, tempRoot = tmpdir()) {
  if (!sessionId) return null;
  const file = statePath(sessionId, tempRoot);
  if (!existsSync(file)) return null;
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch (error) {
    throw new Error(`estado de engineering review corrompido em ${file}: ${error.message}`);
  }
}

function stateHasBaseline(state) {
  return Object.prototype.hasOwnProperty.call(state ?? {}, 'baselineFingerprint');
}

function writeState(sessionId, state, tempRoot = tmpdir()) {
  const dir = stateDirectory(tempRoot);
  mkdirSync(dir, { recursive: true });
  const target = statePath(sessionId, tempRoot);
  const temporary = `${target}.${process.pid}.${randomUUID()}.tmp`;
  writeFileSync(temporary, `${JSON.stringify({ stateVersion: STATE_VERSION, ...state })}\n`, 'utf8');
  renameSync(temporary, target);
}

function isProcessAlive(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error?.code === 'EPERM';
  }
}

function recoverStaleLock(lock, {
  now = Date.now(),
  staleLockMs = DEFAULT_LOCK_STALE_MS,
} = {}) {
  if (!existsSync(lock)) return false;
  const ownerPath = path.join(lock, 'owner.json');

  try {
    if (existsSync(ownerPath)) {
      const owner = JSON.parse(readFileSync(ownerPath, 'utf8'));
      const pid = Number(owner?.pid);
      if (Number.isInteger(pid) && pid > 0) {
        if (isProcessAlive(pid)) return false;
        rmSync(lock, { recursive: true, force: true });
        return true;
      }
    }
  } catch {
    // A metadata corrompida cai para a política por idade abaixo.
  }

  try {
    const ageMs = now - statSync(lock).mtimeMs;
    if (ageMs >= staleLockMs) {
      rmSync(lock, { recursive: true, force: true });
      return true;
    }
  } catch {
    return !existsSync(lock);
  }

  return false;
}

function updateState(sessionId, updater, {
  tempRoot = tmpdir(),
  lockAttempts = 80,
  lockDelayMs = 25,
  staleLockMs = DEFAULT_LOCK_STALE_MS,
  now = Date.now(),
  recoverCorruptState = false,
} = {}) {
  if (!sessionId) throw new Error('session_id é obrigatório');
  const lock = lockPath(sessionId, tempRoot);
  mkdirSync(stateDirectory(tempRoot), { recursive: true });

  let acquired = false;
  for (let attempt = 0; attempt < lockAttempts; attempt += 1) {
    try {
      mkdirSync(lock);
      writeFileSync(path.join(lock, 'owner.json'), `${JSON.stringify({ pid: process.pid, acquiredAt: now })}\n`, 'utf8');
      acquired = true;
      break;
    } catch (error) {
      if (error?.code !== 'EEXIST') throw error;
      if (recoverStaleLock(lock, { now: Date.now(), staleLockMs })) continue;
      sleepSync(lockDelayMs);
    }
  }

  if (!acquired) throw new Error('timeout ao adquirir lock do estado de engineering review');

  try {
    let current;
    try {
      current = readState(sessionId, tempRoot) ?? {};
    } catch (error) {
      if (!recoverCorruptState) throw error;
      current = { __corruptReviewStateError: error.message };
    }
    const working = structuredClone(current);
    const beforeUpdate = JSON.stringify(working);
    const next = updater(working);
    if (next === working && JSON.stringify(next) === beforeUpdate) return next;
    if (next === null) {
      rmSync(statePath(sessionId, tempRoot), { force: true });
      return null;
    }
    writeState(sessionId, next, tempRoot);
    return next;
  } finally {
    rmSync(lock, { recursive: true, force: true });
  }
}

export function getReviewState(sessionId, tempRoot = tmpdir()) {
  return readState(sessionId, tempRoot);
}

export function clearReviewState(sessionId, tempRoot = tmpdir()) {
  if (!sessionId) return;
  rmSync(statePath(sessionId, tempRoot), { force: true });
  rmSync(lockPath(sessionId, tempRoot), { recursive: true, force: true });
}

function git(cwd, args, { input } = {}) {
  return execFileSync('git', ['-C', cwd, ...args], {
    encoding: 'utf8',
    input,
    stdio: ['pipe', 'pipe', 'pipe'],
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

function requireRepositoryRoot(cwd) {
  const root = repositoryRoot(cwd);
  if (!root) throw new Error(`não foi possível resolver a raiz Git a partir de ${cwd || process.cwd()}`);
  return root;
}

function effectiveProjectDir(cwd, explicitProjectDir) {
  return path.resolve(
    explicitProjectDir
    || process.env.CLAUDE_PROJECT_DIR
    || requireRepositoryRoot(cwd || process.cwd()),
  );
}

function splitNull(value) {
  return String(value ?? '').split('\0').filter(Boolean);
}

function changedPaths(repoRoot) {
  const tracked = splitNull(git(repoRoot, ['diff', '--name-only', '-z', 'HEAD']));
  const untracked = splitNull(git(repoRoot, ['ls-files', '--others', '--exclude-standard', '-z']));
  return [...new Set([...tracked, ...untracked])].sort();
}

function currentHead(repoRoot) {
  try {
    return git(repoRoot, ['rev-parse', 'HEAD']);
  } catch {
    return null;
  }
}

function reviewScopePaths(repoRoot, baselineHead) {
  const paths = new Set(changedPaths(repoRoot));
  const head = currentHead(repoRoot);
  if (baselineHead && head && baselineHead !== head) {
    try {
      for (const relativePath of splitNull(git(repoRoot, ['diff', '--name-only', '-z', baselineHead, head]))) {
        paths.add(relativePath);
      }
    } catch {
      // Se a baseline não puder mais ser resolvida (rebase/gc), o fallback é o
      // working tree atual; o Stop Gate continua fail-closed pelo state.
    }
  }
  return [...paths].sort();
}

function hasEngineeringWorkingTreeChanges(cwd, projectDir) {
  const effectiveCwd = cwd || process.cwd();
  const repoRoot = requireRepositoryRoot(effectiveCwd);
  const projectRoot = effectiveProjectDir(effectiveCwd, projectDir);
  return changedPaths(repoRoot).some((relativePath) => isEngineeringChangePath(
    path.join(repoRoot, relativePath),
    { cwd: repoRoot, projectDir: projectRoot },
  ));
}

function engineeringPaths(repoRoot, projectDir) {
  const paths = new Set([
    ...splitNull(git(repoRoot, ['ls-files', '-z'])),
    ...splitNull(git(repoRoot, ['ls-files', '--others', '--exclude-standard', '-z'])),
  ]);
  return [...paths]
    .filter((relativePath) => isEngineeringChangePath(
      path.join(repoRoot, relativePath),
      { cwd: repoRoot, projectDir },
    ))
    .sort();
}

export function engineeringWorkingTreeFingerprint({
  cwd = process.cwd(),
  projectDir,
} = {}) {
  const repoRoot = requireRepositoryRoot(cwd);
  const projectRoot = effectiveProjectDir(cwd, projectDir);

  // O fingerprint representa o conteúdo executável observado no filesystem,
  // não a posição Git (unstaged/staged/commitado). Dessa forma, `git add` e
  // `git commit` dos mesmos bytes não invalidam uma revisão já concluída, mas
  // edição, criação, exclusão ou mudança do bit executável continuam mudando a
  // assinatura. O caminho faz parte do hash para que rename também invalide.
  const hash = createHash('sha256');
  for (const relativePath of engineeringPaths(repoRoot, projectRoot)) {
    const absolute = path.join(repoRoot, relativePath);
    if (!existsSync(absolute)) continue; // filesystem atual é a fonte; staging não muda o fingerprint.
    hash.update(relativePath);
    hash.update('\0');
    try {
      const executable = (statSync(absolute).mode & 0o111) !== 0 ? 'X' : '-';
      hash.update(executable);
      hash.update('\0');
      hash.update(readFileSync(absolute));
    } catch {
      hash.update('UNREADABLE');
    }
    hash.update('\0');
  }
  return hash.digest('hex');
}

function currentFingerprint(payload, projectDir) {
  return engineeringWorkingTreeFingerprint({
    cwd: payload?.cwd || process.cwd(),
    projectDir: effectiveProjectDir(payload?.cwd || process.cwd(), projectDir),
  });
}

function staleStateForFingerprint(fingerprint, reason, now = Date.now()) {
  return {
    status: REVIEW_STATUS.NEEDS_REVIEW,
    staleAt: now,
    staleFingerprint: fingerprint,
    staleReason: reason,
  };
}

function conservativeUnreviewedState(payload, {
  now = Date.now(),
  projectDir,
  reason,
  corruptStateError,
} = {}) {
  const projectRoot = effectiveProjectDir(payload?.cwd || process.cwd(), projectDir);
  const fingerprint = currentFingerprint(payload, projectRoot);
  const repoRoot = requireRepositoryRoot(payload?.cwd || process.cwd());
  const baselineHead = currentHead(repoRoot);

  return {
    baselineFingerprint: fingerprint,
    baselineHead,
    baselineTrust: 'UNKNOWN_UNREVIEWED',
    recoveredAt: now,
    recoveryReason: reason,
    ...(corruptStateError ? { corruptStateError: String(corruptStateError).slice(0, 1000) } : {}),
    review: {
      ...staleStateForFingerprint(fingerprint, reason, now),
      scopeUnknown: true,
    },
    stopBlocks: 0,
  };
}

export function recordSessionBaseline(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) return false;

  const projectRoot = effectiveProjectDir(payload?.cwd || process.cwd(), projectDir);
  const fingerprint = currentFingerprint(payload, projectRoot);
  const repoRoot = requireRepositoryRoot(payload?.cwd || process.cwd());
  const baselineHead = currentHead(repoRoot);
  const source = String(payload?.source ?? 'startup').trim().toLowerCase();

  updateState(sessionId, (previous) => {
    // SessionStart pode ocorrer mais de uma vez para a mesma sessão. Nunca
    // substitua uma baseline/revisão já persistida, inclusive em startup.
    if (stateHasBaseline(previous)) return previous;

    // Resume/compact/clear/fork pressupõem continuidade. Se o state sumiu,
    // não existe evidência suficiente para reconstruir uma aprovação, mesmo com
    // working tree limpo (a mudança pode ter sido commitada antes da perda).
    const continuationWithoutState = source !== 'startup';
    // Uma nova sessão startup sobre working tree executável já sujo também não
    // pode adotar silenciosamente esses bytes como baseline aprovada.
    if (continuationWithoutState || hasEngineeringWorkingTreeChanges(payload?.cwd, projectRoot)) {
      return conservativeUnreviewedState(payload, {
        now,
        projectDir: projectRoot,
        reason: `${source || 'unknown'}_without_persisted_state`,
      });
    }

    return {
      baselineFingerprint: fingerprint,
      baselineHead,
      baselineTrust: 'SESSION_START_CLEAN',
      sessionSource: source || 'startup',
      stopBlocks: 0,
    };
  }, { tempRoot, now });
  return true;
}

export function recordEngineeringEdit(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  const filePath = payload?.tool_input?.file_path;
  const projectRoot = effectiveProjectDir(payload?.cwd || process.cwd(), projectDir);
  if (!sessionId || !isEngineeringChangePath(filePath, { cwd: payload?.cwd, projectDir: projectRoot })) return false;

  const fingerprint = currentFingerprint(payload, projectRoot);
  updateState(sessionId, (previous) => {
    const invalidatedRound = previous.review?.status === REVIEW_STATUS.REVIEW_RUNNING
      ? {
          invalidatedRoundId: previous.review.roundId,
          invalidatedAt: now,
          invalidatedReason: 'edit_during_review',
          invalidatedReviewerSummaries: Object.fromEntries(
            (previous.review.expectedReviewers ?? []).map((name) => [name, reviewerSummary(previous.review.reviewers?.[name])]),
          ),
        }
      : {};
    return {
      ...previous,
      baselineFingerprint: stateHasBaseline(previous)
        ? previous.baselineFingerprint
        : fingerprint,
      lastEngineeringEditAt: now,
      lastEngineeringPath: resolveProjectRelative(filePath, payload?.cwd, projectRoot),
      review: { ...staleStateForFingerprint(fingerprint, 'executable_edit', now), ...invalidatedRound },
      stopBlocks: 0,
    };
  }, { tempRoot, now });
  return true;
}

export function recordEngineeringMutation(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) return false;
  const projectRoot = effectiveProjectDir(payload?.cwd || process.cwd(), projectDir);
  const fingerprint = currentFingerprint(payload, projectRoot);
  if (!fingerprint) return false;

  let changed = false;
  updateState(sessionId, (previous) => {
    const baselineKnown = stateHasBaseline(previous);
    const validForCurrent = previous.review?.status === REVIEW_STATUS.REVIEW_VALID
      && previous.review?.reviewedFingerprint === fingerprint;
    if (validForCurrent) return previous;

    const differsFromBaseline = baselineKnown && previous.baselineFingerprint !== fingerprint;
    const missingBaselineWithDirtyTree = !baselineKnown && hasEngineeringWorkingTreeChanges(payload?.cwd, projectRoot);
    if (!differsFromBaseline && !missingBaselineWithDirtyTree) return previous;

    changed = true;
    return {
      ...previous,
      baselineFingerprint: baselineKnown ? previous.baselineFingerprint : fingerprint,
      review: staleStateForFingerprint(fingerprint, 'executable_mutation', now),
      stopBlocks: 0,
    };
  }, { tempRoot, now });
  return changed;
}

function normalizeReviewerType(value) {
  return REVIEWER_ALIASES.get(String(value ?? '').trim()) ?? null;
}

function normalizeExpectedReviewers(reviewers) {
  const normalized = [];
  for (const value of reviewers ?? []) {
    const raw = String(value ?? '').trim();
    if (!raw) continue;
    const canonical = normalizeReviewerType(raw);
    if (!canonical) throw new Error(`reviewer desconhecido: ${raw}`);
    normalized.push(canonical);
  }
  return [...new Set(normalized)].sort();
}

function sameStringSet(a = [], b = []) {
  const left = [...a].sort();
  const right = [...b].sort();
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function normalizeRiskLevel(value, fallback = 'LOW') {
  const normalized = String(value ?? fallback).trim().toUpperCase();
  if (!REVIEW_RISK_LEVELS.has(normalized)) throw new Error(`nível de risco inválido: ${value}`);
  return normalized;
}

function maxRisk(left, right) {
  return REVIEW_RISK_RANK[left] >= REVIEW_RISK_RANK[right] ? left : right;
}

function isTestPath(relativePath) {
  const lower = normalizeRelativePath(relativePath).toLowerCase();
  return lower.includes('/src/test/') || /(?:^|\/)(?:test|tests|__tests__)(?:\/|$)/.test(lower)
    || /(?:\.spec|\.test)\.[^.]+$/.test(lower);
}

function inferReviewRequirements(payload, state, projectDir) {
  const effectiveCwd = payload?.cwd || process.cwd();
  const repoRoot = requireRepositoryRoot(effectiveCwd);
  const projectRoot = effectiveProjectDir(effectiveCwd, projectDir);
  let paths = reviewScopePaths(repoRoot, state?.baselineHead)
    .filter((relativePath) => isEngineeringChangePath(path.join(repoRoot, relativePath), { cwd: repoRoot, projectDir: projectRoot }));

  if (!paths.length && state?.lastEngineeringPath) paths = [state.lastEngineeringPath];

  const required = new Set();
  let inferredRisk = paths.length ? 'MEDIUM' : 'LOW';
  let securitySensitive = false;

  // Se uma continuação perdeu o state anterior, não há base confiável para
  // reconstruir quais bytes/commits pertenciam à tarefa. A recuperação é
  // propositalmente conservadora: HIGH + todos os reviewers.
  if (state?.review?.scopeUnknown) {
    return {
      paths,
      inferredRisk: 'HIGH',
      requiredReviewers: [...CANONICAL_REVIEWER_TYPES],
      scopeUnknown: true,
    };
  }

  for (const relativePath of paths) {
    const lower = normalizeRelativePath(relativePath).toLowerCase();
    const base = path.basename(lower);
    const migration = /(?:^|\/)src\/main\/resources\/db\/migration\/v[^/]+\.sql$/.test(lower);
    const aiControl = AI_CONTROL_PREFIXES.some((prefix) => lower.startsWith(prefix))
      || /(?:^|\/)(?:agents|claude)\.md$/i.test(relativePath);
    const aiSecurityControl = /^(?:\.ai\/(?:guards|review)\/|\.claude\/(?:hooks|agents)\/|\.codex\/(?:hooks|agents)\/)/.test(lower)
      || /(?:^|\/)(?:settings(?:\.local)?\.json|hooks\.json|config\.toml|review-change\/skill\.md)$/.test(lower);
    const sensitive = /(?:^|\/)(?:security|auth|oauth|jwt|permission|permissions|role|roles|credential|credentials|token|password|cors)(?:\/|[._-]|$)/.test(lower)
      || /(?:security|auth|oauth|jwt|permission|role|credential|token|password|cors)/.test(base)
      || /application(?:-[^.]+)?\.(?:properties|ya?ml)$/.test(base)
      || aiSecurityControl;
    const infrastructure = aiControl || lower.startsWith('.github/') || /(?:dockerfile|compose|jenkinsfile|\.tf(?:vars)?$)/.test(lower);

    if (lower.startsWith('backend/') || /\.(?:java|kt|kts|sql|gradle)$/.test(lower) || base === 'pom.xml') required.add('backend-reviewer');
    if (lower.startsWith('frontend-web/') || /\.(?:ts|tsx|html|css|scss|sass|less)$/.test(lower) || ['package.json', 'angular.json'].includes(base)) required.add('frontend-reviewer');
    if (isTestPath(lower)) required.add('test-reviewer');
    if (migration) {
      required.add('backend-reviewer');
      required.add('test-reviewer');
      inferredRisk = 'HIGH';
    }
    if (sensitive) {
      required.add('security-reviewer');
      securitySensitive = true;
      inferredRisk = 'HIGH';
    }
    if (infrastructure) {
      required.add('test-reviewer');
      inferredRisk = 'HIGH';
    }
  }

  if (paths.length && required.size === 0) required.add('test-reviewer');
  if (inferredRisk === 'HIGH' && securitySensitive) required.add('security-reviewer');

  return { paths, inferredRisk, requiredReviewers: [...required].sort() };
}

export function startReviewRound(payload, expectedReviewers = [], {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
  riskLevel,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) throw new Error('session_id é obrigatório para iniciar review');

  const requested = normalizeExpectedReviewers(expectedReviewers);
  let existingState = null;
  let existingStateError = null;
  try {
    existingState = readState(sessionId, tempRoot);
  } catch (error) {
    existingStateError = error;
  }
  const needsRecovery = !stateHasBaseline(existingState);
  const recoveryReason = existingStateError
    ? 'review_start_corrupt_state_recovered'
    : existingState
      ? 'review_start_without_baseline'
      : 'review_start_without_persisted_state';
  const stateForInference = needsRecovery
    ? conservativeUnreviewedState(payload, {
        now,
        projectDir,
        reason: recoveryReason,
        corruptStateError: existingStateError?.message,
      })
    : existingState;
  const inferred = inferReviewRequirements(payload, stateForInference, projectDir);
  const requestedRisk = riskLevel == null ? inferred.inferredRisk : normalizeRiskLevel(riskLevel, inferred.inferredRisk);
  const risk = maxRisk(inferred.inferredRisk, requestedRisk);
  const expected = [...new Set([...requested, ...inferred.requiredReviewers])].sort();
  if (inferred.paths.length && expected.length === 0) {
    throw new Error('review de alteração executável exige ao menos um reviewer inferido mecanicamente');
  }
  const fingerprint = currentFingerprint(payload, projectDir);
  if (!fingerprint) throw new Error('não foi possível calcular fingerprint do repositório');
  const roundId = randomUUID();
  let reused = false;

  const state = updateState(sessionId, (previous) => {
    const workingPrevious = stateHasBaseline(previous)
      ? previous
      : conservativeUnreviewedState(payload, {
          now,
          projectDir,
          reason: previous.__corruptReviewStateError
            ? 'review_start_corrupt_state_recovered'
            : recoveryReason,
          corruptStateError: previous.__corruptReviewStateError ?? existingStateError?.message,
        });
    const current = workingPrevious.review;
    if (current?.status === REVIEW_STATUS.REVIEW_RUNNING && current.fingerprint === fingerprint) {
      if (current.riskLevel !== risk || !sameStringSet(current.expectedReviewers ?? [], expected)) {
        throw new Error(
          `rodada ${current.roundId} já está em andamento para o mesmo fingerprint com risco/reviewers diferentes; `
          + 'não reutilize nem amplie a rodada silenciosamente',
        );
      }
      reused = true;
      return workingPrevious;
    }

    const reviewers = Object.fromEntries(expected.map((reviewer) => [reviewer, {
      completed: false,
      attempts: [],
      lastError: null,
      verdict: null,
      maxActionableSeverity: null,
      resultDigest: null,
      resultPreview: null,
    }]));

    return {
      ...workingPrevious,
      baselineFingerprint: stateHasBaseline(workingPrevious)
        ? workingPrevious.baselineFingerprint
        : fingerprint,
      review: {
        status: REVIEW_STATUS.REVIEW_RUNNING,
        roundId,
        fingerprint,
        startedAt: now,
        riskLevel: risk,
        inferredRiskLevel: inferred.inferredRisk,
        inferredRequiredReviewers: inferred.requiredReviewers,
        reviewScopePaths: inferred.paths,
        scopeUnknown: Boolean(inferred.scopeUnknown),
        expectedReviewers: expected,
        reviewers,
        verdict: null,
        failureReason: null,
      },
      stopBlocks: 0,
    };
  }, { tempRoot, now, recoverCorruptState: true });

  return {
    ok: true,
    reused,
    roundId: state.review.roundId,
    fingerprint: state.review.fingerprint,
    riskLevel: state.review.riskLevel,
    inferredRiskLevel: state.review.inferredRiskLevel,
    inferredRequiredReviewers: state.review.inferredRequiredReviewers,
    scopeUnknown: Boolean(state.review.scopeUnknown),
    expectedReviewers: state.review.expectedReviewers,
  };
}

export function recordReviewerStart(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  const agentId = String(payload?.agent_id ?? '').trim();
  const agentType = normalizeReviewerType(payload?.agent_type);
  if (!sessionId || !agentId || !agentType) return false;

  let recorded = false;
  updateState(sessionId, (previous) => {
    const review = previous.review;
    if (review?.status !== REVIEW_STATUS.REVIEW_RUNNING) return previous;
    if (!review.expectedReviewers?.includes(agentType)) return previous;

    const reviewer = review.reviewers?.[agentType];
    if (!reviewer || reviewer.completed) return previous;
    if (reviewer.attempts.some((attempt) => attempt.agentId === agentId)) return previous;

    if (reviewer.attempts.length >= MAX_REVIEWER_ATTEMPTS) {
      return {
        ...previous,
        review: {
          ...review,
          status: REVIEW_STATUS.REVIEW_FAILED,
          failedAt: now,
          failureReason: `reviewer ${agentType} excedeu ${MAX_REVIEWER_ATTEMPTS} tentativas`,
        },
      };
    }

    reviewer.attempts.push({
      agentId,
      status: 'RUNNING',
      startedAt: now,
      completedAt: null,
      error: null,
    });
    recorded = true;
    return previous;
  }, { tempRoot, now });
  return recorded;
}

function normalizeReviewerVerdict(value) {
  const normalized = String(value ?? '').trim().toUpperCase().replaceAll('_', ' ');
  if (normalized === 'CHANGES REQUIRED') return 'CHANGES_REQUIRED';
  return REVIEWER_VERDICTS.has(normalized) ? normalized : null;
}

export function parseReviewerResult(message) {
  const lines = String(message ?? '').replaceAll('\r\n', '\n').split('\n');
  while (lines.length && !lines.at(-1).trim()) lines.pop();
  if (!lines.length) return null;

  const statusMatch = /^Reviewer status:\s*(COMPLETE|FAILED)$/i.exec(lines.at(-1).trim());
  if (!statusMatch) return null;
  const status = statusMatch[1].toUpperCase();
  if (status === 'FAILED') return { status: 'FAILED' };
  if (lines.length < 4) return null;

  const severityMatch = /^Max actionable severity:\s*(NONE|LOW|MEDIUM|HIGH|BLOCKER)$/i.exec(lines.at(-2).trim());
  const verdictMatch = /^Reviewer verdict:\s*(PASS|PASS WITH NOTES|CHANGES_REQUIRED|CHANGES REQUIRED)$/i.exec(lines.at(-3).trim());
  if (!severityMatch || !verdictMatch) return null;

  const bodyLines = lines.slice(0, -3);
  // O footer é reservado às três linhas finais. Repeti-lo no corpo pode ser
  // eco de prompt injection vindo do diff e invalida o resultado.
  if (bodyLines.some((line) => /^(?:Reviewer verdict|Max actionable severity|Reviewer status):/i.test(line.trim()))) return null;

  const findingLines = bodyLines
    .map((line) => line.trim())
    .filter((line) => /^Finding:\s*/i.test(line));
  if (!findingLines.length) return null;

  const severities = [];
  let hasNone = false;
  for (const line of findingLines) {
    const match = /^Finding:\s*(NONE|LOW|MEDIUM|HIGH|BLOCKER)(?:\s*\|\s*.+)?$/i.exec(line);
    if (!match) return null;
    const severity = match[1].toUpperCase();
    if (severity === 'NONE' && !/^Finding:\s*NONE$/i.test(line)) return null;
    if (severity === 'NONE') hasNone = true;
    else severities.push(severity);
  }
  if (hasNone && severities.length) return null;
  if (hasNone && findingLines.length !== 1) return null;

  // Severidades acionáveis escritas fora do formato estruturado não podem ser
  // escondidas por um footer PASS. Isso também reduz o risco de o reviewer
  // repetir instruções maliciosas encontradas no repositório.
  const unstructuredBlocking = bodyLines.some((line) => {
    if (/^Finding:\s*/i.test(line.trim())) return false;
    return /\b(?:BLOCKER|HIGH|MEDIUM)\b/i.test(line);
  });
  if (unstructuredBlocking) return null;

  const rank = { NONE: 0, LOW: 1, MEDIUM: 2, HIGH: 3, BLOCKER: 4 };
  const derivedSeverity = severities.length
    ? severities.reduce((highest, severity) => rank[severity] > rank[highest] ? severity : highest, 'NONE')
    : 'NONE';
  const maxActionableSeverity = severityMatch[1].toUpperCase();
  if (maxActionableSeverity !== derivedSeverity) return null;

  const verdict = normalizeReviewerVerdict(verdictMatch[1]);
  if (!REVIEWER_SEVERITIES.has(maxActionableSeverity) || !verdict) return null;
  const consistent = (
    (maxActionableSeverity === 'NONE' && (verdict === 'PASS' || verdict === 'PASS WITH NOTES'))
    || (maxActionableSeverity === 'LOW' && verdict === 'PASS WITH NOTES')
    || (['MEDIUM', 'HIGH', 'BLOCKER'].includes(maxActionableSeverity) && verdict === 'CHANGES_REQUIRED')
  );
  if (!consistent) return null;

  return { status: 'COMPLETE', verdict, maxActionableSeverity, findingCount: findingLines.length };
}

export function isReviewerResultUsable(message) {
  return parseReviewerResult(message)?.status === 'COMPLETE';
}

function reviewerResultDigest(message) {
  return createHash('sha256').update(message).digest('hex');
}

function reviewerAttemptFailure(parsed) {
  return parsed?.status === 'FAILED'
    ? 'reviewer declarou falha técnica'
    : 'reviewer terminou sem o footer determinístico obrigatório ou com footer inconsistente';
}

function completeReviewerAttempt(reviewer, attempt, parsed, message, now) {
  attempt.status = 'COMPLETED';
  attempt.completedAt = now;
  attempt.error = null;
  attempt.resultDigest = reviewerResultDigest(message);
  reviewer.completed = true;
  reviewer.verdict = parsed.verdict;
  reviewer.maxActionableSeverity = parsed.maxActionableSeverity;
  reviewer.resultDigest = attempt.resultDigest;
  reviewer.resultPreview = message.slice(-RESULT_PREVIEW_LIMIT);
  reviewer.lastError = null;
}

function failReviewerAttempt(review, reviewerName, reviewer, attempt, parsed, now) {
  attempt.status = 'FAILED';
  attempt.completedAt = now;
  attempt.error = reviewerAttemptFailure(parsed);
  reviewer.lastError = attempt.error;

  if (reviewer.attempts.length >= MAX_REVIEWER_ATTEMPTS) {
    review.status = REVIEW_STATUS.REVIEW_FAILED;
    review.failedAt = now;
    review.failureReason = `${reviewerName}: ${attempt.error}; limite de tentativas atingido`;
    review.failureReasonSource = 'reviewer_result';
  }
}

function appendReviewerAttempt(reviewer, {
  agentId,
  source,
  now,
}) {
  const attempt = {
    agentId,
    source,
    status: 'RUNNING',
    startedAt: now,
    completedAt: null,
    error: null,
  };
  reviewer.attempts.push(attempt);
  return attempt;
}

function persistReviewerResultInReview(review, {
  reviewerName,
  message,
  now,
  source,
  agentId,
  requireRunningAttempt,
  throwOnConflict,
}) {
  if (review?.status !== REVIEW_STATUS.REVIEW_RUNNING) {
    return { recorded: false, reason: 'review_not_running' };
  }
  if (!review.expectedReviewers?.includes(reviewerName)) {
    return { recorded: false, reason: 'reviewer_not_expected' };
  }

  const reviewer = review.reviewers?.[reviewerName];
  if (!reviewer) return { recorded: false, reason: 'reviewer_missing' };

  const digest = reviewerResultDigest(message);
  if (reviewer.completed) {
    if (reviewer.resultDigest === digest) {
      return { recorded: true, idempotent: true, completed: true, verdict: reviewer.verdict };
    }
    const reason = `resultado conflitante para ${reviewerName}: já existe digest ${reviewer.resultDigest}, recebido ${digest}`;
    review.resultConflicts = (review.resultConflicts ?? 0) + 1;
    if (throwOnConflict) throw new Error(reason);
    return { recorded: false, reason: 'result_conflict' };
  }

  let attempt;
  if (requireRunningAttempt) {
    attempt = reviewer.attempts?.find((item) => item.agentId === agentId);
    if (!attempt) {
      review.ignoredReviewerStops = (review.ignoredReviewerStops ?? 0) + 1;
      return { recorded: false, reason: 'missing_start' };
    }
    if (attempt.status !== 'RUNNING') {
      return { recorded: false, reason: 'attempt_not_running' };
    }
  } else {
    if (reviewer.attempts.length >= MAX_REVIEWER_ATTEMPTS) {
      review.status = REVIEW_STATUS.REVIEW_FAILED;
      review.failedAt = now;
      review.failureReason = `reviewer ${reviewerName} excedeu ${MAX_REVIEWER_ATTEMPTS} tentativas`;
      review.failureReasonSource = 'reviewer_result';
      return { recorded: false, technicalFailure: true, reason: 'attempt_limit_exceeded' };
    }

    attempt = appendReviewerAttempt(reviewer, {
      agentId: String(agentId ?? '').trim() || `explicit-${randomUUID()}`,
      source,
      now,
    });
  }

  const parsed = parseReviewerResult(message);
  if (parsed?.status === 'COMPLETE') {
    completeReviewerAttempt(reviewer, attempt, parsed, message, now);
    return {
      recorded: true,
      completed: true,
      verdict: parsed.verdict,
      maxActionableSeverity: parsed.maxActionableSeverity,
    };
  }

  failReviewerAttempt(review, reviewerName, reviewer, attempt, parsed, now);
  return {
    recorded: true,
    completed: false,
    technicalFailure: review.status === REVIEW_STATUS.REVIEW_FAILED,
    reason: attempt.error,
  };
}

export function recordReviewerStop(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  const agentId = String(payload?.agent_id ?? '').trim();
  const agentType = normalizeReviewerType(payload?.agent_type);
  const message = String(payload?.last_assistant_message ?? '');
  if (!sessionId || !agentId || !agentType) return false;

  let lifecycleRecorded = false;
  updateState(sessionId, (previous) => {
    const outcome = persistReviewerResultInReview(previous.review, {
      reviewerName: agentType,
      message,
      now,
      source: 'lifecycle',
      agentId,
      requireRunningAttempt: true,
      throwOnConflict: false,
    });
    lifecycleRecorded = Boolean(outcome.recorded && !outcome.idempotent);
    return previous;
  }, { tempRoot, now });
  return lifecycleRecorded;
}

export function recordExplicitReviewerResult(payload, {
  roundId,
  reviewer,
  result,
  agentId,
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) throw new Error('session_id é obrigatório para registrar resultado de reviewer');
  const reviewerName = normalizeReviewerType(reviewer);
  if (!reviewerName) throw new Error(`reviewer desconhecido: ${reviewer}`);
  const message = String(result ?? '');
  if (!message.trim()) throw new Error('resultado do reviewer é obrigatório');
  const fingerprint = currentFingerprint(payload, projectDir);
  let outcome = null;

  const state = updateState(sessionId, (previous) => {
    const review = previous.review;
    assertRoundId(previous, roundId);
    if (review?.status !== REVIEW_STATUS.REVIEW_RUNNING) {
      throw new Error(`não há review em andamento; estado atual: ${review?.status ?? 'ausente'}`);
    }
    if (!review.expectedReviewers?.includes(reviewerName)) {
      throw new Error(`reviewer ${reviewerName} não é esperado pela rodada ${roundId}`);
    }
    if (review.fingerprint !== fingerprint) {
      outcome = { ok: false, stale: true };
      return {
        ...previous,
        review: {
          ...review,
          ...staleStateForFingerprint(fingerprint, 'fingerprint_changed_during_review', now),
          roundId: review.roundId,
          verdict: null,
        },
        stopBlocks: 0,
      };
    }

    outcome = persistReviewerResultInReview(review, {
      reviewerName,
      message,
      now,
      source: 'explicit',
      agentId,
      requireRunningAttempt: false,
      throwOnConflict: true,
    });
    return previous;
  }, { tempRoot, now });

  if (outcome?.stale) {
    throw new Error('o working tree mudou durante a revisão; a rodada ficou stale e precisa ser refeita');
  }

  const status = publicReviewStatus(state);
  return {
    ok: Boolean(outcome?.completed || outcome?.idempotent),
    recorded: Boolean(outcome?.recorded),
    idempotent: Boolean(outcome?.idempotent),
    reviewer: reviewerName,
    reviewerCompleted: Boolean(status.reviewerSummaries?.[reviewerName]?.completed),
    reviewerVerdict: status.reviewerSummaries?.[reviewerName]?.verdict ?? null,
    reviewerMaxActionableSeverity: status.reviewerSummaries?.[reviewerName]?.maxActionableSeverity ?? null,
    technicalFailure: Boolean(outcome?.technicalFailure || status.status === REVIEW_STATUS.REVIEW_FAILED),
    reason: outcome?.reason ?? null,
    ...status,
  };
}

function reviewerSummary(reviewer) {
  return {
    completed: Boolean(reviewer?.completed),
    attempts: reviewer?.attempts?.length ?? 0,
    verdict: reviewer?.verdict ?? null,
    maxActionableSeverity: reviewer?.maxActionableSeverity ?? null,
    resultDigest: reviewer?.resultDigest ?? null,
    lastError: reviewer?.lastError ?? null,
  };
}

function publicReviewStatus(state) {
  const review = state?.review;
  if (!review) {
    return {
      status: REVIEW_STATUS.NEEDS_REVIEW,
      roundId: null,
      expectedReviewers: [],
      riskLevel: null,
      pendingReviewers: [],
      failedReviewers: [],
      reviewerSummaries: {},
      failureReason: null,
    };
  }
  const expected = review.expectedReviewers ?? [];
  const pending = expected.filter((reviewer) => !review.reviewers?.[reviewer]?.completed);
  const failed = expected.filter((reviewer) => Boolean(review.reviewers?.[reviewer]?.lastError));
  const reviewerSummaries = Object.fromEntries(expected.map((reviewer) => [
    reviewer,
    reviewerSummary(review.reviewers?.[reviewer]),
  ]));

  return {
    status: review.status,
    roundId: review.roundId ?? null,
    fingerprint: review.fingerprint ?? review.reviewedFingerprint ?? null,
    verdict: review.verdict ?? null,
    expectedReviewers: expected,
    riskLevel: review.riskLevel ?? null,
    inferredRiskLevel: review.inferredRiskLevel ?? null,
    inferredRequiredReviewers: review.inferredRequiredReviewers ?? [],
    scopeUnknown: Boolean(review.scopeUnknown),
    pendingReviewers: pending,
    failedReviewers: failed,
    reviewerSummaries,
    failureReason: review.failureReason ?? null,
  };
}

function assertRoundId(state, roundId) {
  const expected = String(roundId ?? '').trim();
  if (!expected) throw new Error('roundId é obrigatório para operar uma rodada existente');
  const actual = state?.review?.roundId;
  if (!actual) throw new Error('não há roundId de review persistido');
  if (actual !== expected) throw new Error(`roundId stale ou incorreto: esperado ${actual}, recebido ${expected}`);
}

export function reviewRoundStatus(sessionId, {
  tempRoot = tmpdir(),
  roundId,
} = {}) {
  const state = readState(sessionId, tempRoot);
  assertRoundId(state, roundId);
  return publicReviewStatus(state);
}

export function waitForReviewers(sessionId, {
  tempRoot = tmpdir(),
  roundId,
  timeoutMs = DEFAULT_REVIEW_AWAIT_MS,
  intervalMs = 100,
  maxAttempts = Math.max(1, Math.ceil(timeoutMs / intervalMs)),
  now = Date.now(),
} = {}) {
  const startedAt = Date.now();
  let attempts = 0;

  while (attempts < maxAttempts && Date.now() - startedAt <= timeoutMs) {
    attempts += 1;
    const status = reviewRoundStatus(sessionId, { tempRoot, roundId });
    if (status.status === REVIEW_STATUS.REVIEW_FAILED) {
      return { ok: false, technicalFailure: true, timedOut: false, attempts, ...status };
    }
    if (status.status !== REVIEW_STATUS.REVIEW_RUNNING) {
      return { ok: false, technicalFailure: true, timedOut: false, attempts, ...status };
    }
    if (status.pendingReviewers.length === 0) {
      return { ok: true, technicalFailure: false, timedOut: false, attempts, ...status };
    }
    if (attempts < maxAttempts) sleepSync(intervalMs);
  }

  const failedState = updateState(sessionId, (previous) => {
    assertRoundId(previous, roundId);
    const review = previous.review;
    if (review?.status !== REVIEW_STATUS.REVIEW_RUNNING) return previous;

    const pending = (review.expectedReviewers ?? [])
      .filter((reviewer) => !review.reviewers?.[reviewer]?.completed);
    if (!pending.length) return previous;

    const failureReason = `timeout aguardando reviewers pendentes apos ${timeoutMs}ms: ${pending.join(', ')}`;
    for (const reviewerName of pending) {
      const reviewer = review.reviewers?.[reviewerName];
      if (!reviewer) continue;
      reviewer.lastError = failureReason;
      for (const attempt of reviewer.attempts ?? []) {
        if (attempt.status === 'RUNNING') {
          attempt.status = 'TIMEOUT';
          attempt.completedAt = now;
          attempt.error = failureReason;
        }
      }
    }

    return {
      ...previous,
      review: {
        ...review,
        status: REVIEW_STATUS.REVIEW_FAILED,
        failedAt: now,
        failureReason,
      },
      stopBlocks: 0,
    };
  }, { tempRoot, now });

  const status = publicReviewStatus(failedState);
  if (status.status === REVIEW_STATUS.REVIEW_RUNNING && status.pendingReviewers.length === 0) {
    return { ok: true, technicalFailure: false, timedOut: false, attempts, ...status };
  }
  return { ok: false, technicalFailure: true, timedOut: true, attempts, ...status };
}

function normalizeVerdict(verdict) {
  const normalized = String(verdict ?? '').trim().toUpperCase().replaceAll('_', ' ');
  if (normalized === 'CHANGES REQUIRED') return 'CHANGES_REQUIRED';
  if (normalized === 'PASS' || normalized === 'PASS WITH NOTES') return normalized;
  throw new Error(`veredito inválido: ${verdict}`);
}

function hasBlockingReviewerFinding(review) {
  return (review.expectedReviewers ?? []).some((reviewerName) => {
    const reviewer = review.reviewers?.[reviewerName];
    return reviewer?.verdict === 'CHANGES_REQUIRED'
      || ['MEDIUM', 'HIGH', 'BLOCKER'].includes(reviewer?.maxActionableSeverity);
  });
}

export function completeReviewRound(payload, verdict, {
  roundId,
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) throw new Error('session_id é obrigatório para concluir review');
  const normalizedVerdict = normalizeVerdict(verdict);
  const fingerprint = currentFingerprint(payload, projectDir);
  let outcome = null;

  const state = updateState(sessionId, (previous) => {
    const review = previous.review;
    assertRoundId(previous, roundId);
    if (review?.status !== REVIEW_STATUS.REVIEW_RUNNING) {
      throw new Error(`não há review em andamento; estado atual: ${review?.status ?? 'ausente'}`);
    }
    if (review.fingerprint !== fingerprint) {
      outcome = 'stale';
      return {
        ...previous,
        review: {
          ...review,
          ...staleStateForFingerprint(fingerprint, 'fingerprint_changed_during_review', now),
          roundId: review.roundId,
          verdict: null,
        },
        stopBlocks: 0,
      };
    }

    const pending = (review.expectedReviewers ?? [])
      .filter((reviewer) => !review.reviewers?.[reviewer]?.completed);
    if (pending.length) throw new Error(`review não pode concluir com reviewers pendentes: ${pending.join(', ')}`);

    const blockingReviewer = hasBlockingReviewerFinding(review);
    if (blockingReviewer && normalizedVerdict !== 'CHANGES_REQUIRED') {
      throw new Error('reviewer reportou finding acionável MEDIUM/HIGH/BLOCKER; o veredito final não pode ser PASS/PASS WITH NOTES');
    }

    if (normalizedVerdict === 'CHANGES_REQUIRED') {
      outcome = 'blocked';
      return {
        ...previous,
        review: {
          ...review,
          ...staleStateForFingerprint(fingerprint, 'changes_required', now),
          roundId: review.roundId,
          verdict: normalizedVerdict,
          completedAt: now,
          reviewedFingerprint: fingerprint,
        },
        stopBlocks: 0,
      };
    }

    outcome = 'valid';
    return {
      ...previous,
      review: {
        ...review,
        status: REVIEW_STATUS.REVIEW_VALID,
        verdict: normalizedVerdict,
        completedAt: now,
        reviewedFingerprint: fingerprint,
        failureReason: null,
      },
      stopBlocks: 0,
    };
  }, { tempRoot, now });

  if (outcome === 'stale') {
    throw new Error('o working tree mudou durante a revisão; a rodada ficou stale e precisa ser refeita');
  }

  return {
    ok: outcome === 'valid',
    status: state.review.status,
    verdict: state.review.verdict,
    roundId: state.review.roundId,
    fingerprint: state.review.reviewedFingerprint,
  };
}

export function failReviewRound(payload, reason, {
  roundId,
  now = Date.now(),
  tempRoot = tmpdir(),
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) throw new Error('session_id é obrigatório para falhar review');
  const message = String(reason || 'falha técnica não especificada').slice(0, 4000);

  const state = updateState(sessionId, (previous) => {
    assertRoundId(previous, roundId);
    return {
      ...previous,
      review: {
        ...(previous.review ?? {}),
        status: REVIEW_STATUS.REVIEW_FAILED,
        failedAt: now,
        failureReason: message,
      },
      stopBlocks: 0,
    };
  }, { tempRoot, now });

  return publicReviewStatus(state);
}

// Mantido apenas para compatibilidade de testes/relatórios legados. O Stop Gate
// nunca usa texto da resposta do modelo como fonte de aprovação.
export function hasEngineeringReviewPass(message) {
  const normalized = String(message ?? '').replaceAll('*', '');
  return /(?:^|\n)\s*Engineering review:\s*PASS(?: WITH NOTES)?\s*(?:$|\n)/i.test(normalized);
}

function blockReasonFor(state, current, payload) {
  const review = state?.review;
  if (review?.status === REVIEW_STATUS.REVIEW_RUNNING) {
    const background = Array.isArray(payload?.background_tasks)
      ? payload.background_tasks.filter((task) => task?.status === 'running')
      : [];
    const suffix = background.length
      ? ` O runtime reporta ${background.length} background task(s); review-change não deve depender delas.`
      : '';
    return `ENGINEERING_REVIEW_RUNNING: rodada ${review.roundId ?? '(sem id)'} ainda não consolidada.${suffix}`;
  }

  if (review?.status === REVIEW_STATUS.REVIEW_FAILED) {
    return `ENGINEERING_REVIEW_FAILED: ${review.failureReason ?? 'falha técnica sem detalhe'}. Execute review-change novamente em uma nova rodada; não interprete como PASS.`;
  }

  if (review?.verdict === 'CHANGES_REQUIRED' || review?.staleReason === 'changes_required') {
    return 'ENGINEERING_REVIEW_CHANGES_REQUIRED: corrija apenas BLOCKER/HIGH/MEDIUM acionáveis da tarefa, revalide e execute nova rodada.';
  }

  return [
    'Gate de revisão de engenharia: houve alteração executável após a última revisão válida.',
    'Execute a Skill review-change seguindo .ai/review/ENGINEERING_REVIEW.md.',
    'PASS/PASS WITH NOTES só valem quando persistidos pela rodada para o fingerprint atual.',
    'LOW é não bloqueante; se optar por corrigi-lo, a nova edição exige nova rodada.',
    state?.lastEngineeringPath ? `Último arquivo executável editado: ${state.lastEngineeringPath}.` : '',
    current ? `Fingerprint atual: ${current.slice(0, 12)}.` : '',
  ].filter(Boolean).join(' ');
}

function failSafeMissingState(payload, current, reason) {
  if (payload?.stop_hook_active) {
    return {
      continue: false,
      stopReason: `ENGINEERING_REVIEW_GATE_HALTED: ${reason}`,
      systemMessage: 'Engineering Review Gate interrompeu a execução em fail-closed para evitar loop sem transformar falha técnica em aprovação.',
    };
  }
  return { decision: 'block', reason };
}

export function evaluateStopGate(payload, {
  now = Date.now(),
  tempRoot = tmpdir(),
  projectDir,
} = {}) {
  const sessionId = String(payload?.session_id ?? '').trim();
  if (!sessionId) return null;

  const projectRoot = effectiveProjectDir(payload?.cwd || process.cwd(), projectDir);
  const current = currentFingerprint(payload, projectRoot);
  let state;
  try {
    state = readState(sessionId, tempRoot);
  } catch (error) {
    return failSafeMissingState(
      payload,
      current,
      `ENGINEERING_REVIEW_STATE_CORRUPT: ${error.message}. O estado corrompido não foi interpretado como aprovação; execute review-change para iniciar uma rodada conservadora.`,
    );
  }
  if (!state) {
    return failSafeMissingState(
      payload,
      current,
      'ENGINEERING_REVIEW_STATE_MISSING: o estado persistido da sessão não existe. Não é seguro inferir aprovação a partir de um working tree limpo ou sujo; reinicialize a sessão/review antes de concluir.',
    );
  }

  const baselineKnown = stateHasBaseline(state);
  if (!baselineKnown) {
    return failSafeMissingState(
      payload,
      current,
      'ENGINEERING_REVIEW_STATE_MISSING: o estado persistido existe, mas nÃ£o possui baseline confiÃ¡vel. Isso nÃ£o foi interpretado como aprovaÃ§Ã£o; execute review-change para iniciar uma rodada conservadora.',
    );
  }

  const changedFromBaseline = baselineKnown && current !== state.baselineFingerprint;
  const explicitStale = [REVIEW_STATUS.NEEDS_REVIEW, REVIEW_STATUS.REVIEW_FAILED, REVIEW_STATUS.REVIEW_RUNNING]
    .includes(state.review?.status);
  const engineeringChanged = changedFromBaseline || explicitStale;

  if (!engineeringChanged) return null;

  const reviewValid = state.review?.status === REVIEW_STATUS.REVIEW_VALID
    && state.review.reviewedFingerprint === current
    && ['PASS', 'PASS WITH NOTES'].includes(state.review.verdict);

  if (reviewValid) {
    updateState(sessionId, (previous) => ({
      ...previous,
      baselineFingerprint: current,
      stopBlocks: 0,
    }), { tempRoot, now });
    return null;
  }

  const reason = blockReasonFor(state, current, payload);
  if (payload?.stop_hook_active) {
    return {
      continue: false,
      stopReason: `ENGINEERING_REVIEW_GATE_HALTED: o Stop Hook já concedeu uma continuação e o estado ainda não ficou REVIEW_VALID. ${reason}`,
      systemMessage: 'Engineering Review Gate interrompeu a execução em fail-closed; não houve PASS válido.',
    };
  }

  updateState(sessionId, (previous) => ({
    ...previous,
    reviewRequestedAt: now,
    reviewRequestedFingerprint: current,
    stopBlocks: (previous.stopBlocks ?? 0) + 1,
  }), { tempRoot, now });

  return { decision: 'block', reason };
}

async function readPayload() {
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  if (!input.trim()) throw new Error('payload vazio');
  const payload = JSON.parse(input);
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('payload deve ser objeto JSON');
  if (!String(payload.session_id ?? '').trim()) throw new Error('payload sem session_id válido');
  return payload;
}

async function readRawStdin() {
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return input;
}

function commandSessionId(argument) {
  const explicit = String(argument ?? '').trim();
  return String(
    (explicit && explicit !== '-' ? explicit : '')
    || process.env.CLAUDE_CODE_SESSION_ID
    || process.env.CODEX_THREAD_ID
    || '',
  ).trim();
}

function commandPayload(sessionId) {
  return { session_id: sessionId, cwd: process.cwd() };
}

function controlArgs(flag) {
  const index = process.argv.indexOf(flag);
  return index < 0 ? null : process.argv.slice(index + 1);
}

async function readReviewerResultForCommand(args) {
  const resultFileIndex = args.indexOf('--result-file');
  if (resultFileIndex >= 0) {
    const resultFile = args[resultFileIndex + 1];
    if (!resultFile) throw new Error('--result-file exige um caminho de arquivo');
    return readFileSync(path.resolve(process.cwd(), resultFile), 'utf8');
  }
  return readRawStdin();
}

function readAgentIdForCommand(args) {
  const agentIdIndex = args.indexOf('--agent-id');
  if (agentIdIndex < 0) return null;
  const agentId = args[agentIdIndex + 1];
  if (!agentId) throw new Error('--agent-id exige um valor');
  return agentId;
}

export async function runEngineeringReviewGateCli() {
  let stopPayload = null;
  try {
    if (process.argv.includes('--session-start')) {
      recordSessionBaseline(await readPayload());
      process.stdout.write('{}');
      return;
    }

    if (process.argv.includes('--subagent-start')) {
      recordReviewerStart(await readPayload());
      process.stdout.write('{}');
      return;
    }

    if (process.argv.includes('--subagent-stop')) {
      recordReviewerStop(await readPayload());
      process.stdout.write('{}');
      return;
    }

    const startArgs = controlArgs('--review-start');
    if (startArgs) {
      const sessionId = commandSessionId(startArgs[0]);
      const remainder = startArgs.slice(1);
      let riskLevel;
      const riskIndex = remainder.findIndex((arg) => arg === '--risk');
      if (riskIndex >= 0) {
        riskLevel = remainder[riskIndex + 1];
        if (!riskLevel) throw new Error('--risk exige LOW, MEDIUM ou HIGH');
        remainder.splice(riskIndex, 2);
      }
      process.stdout.write(`${JSON.stringify(startReviewRound(commandPayload(sessionId), remainder, { riskLevel }))}\n`);
      return;
    }

    const statusArgs = controlArgs('--review-status');
    if (statusArgs) {
      const sessionId = commandSessionId(statusArgs[0]);
      const roundId = statusArgs[1];
      process.stdout.write(`${JSON.stringify(reviewRoundStatus(sessionId, { roundId }))}\n`);
      return;
    }

    const awaitArgs = controlArgs('--review-await');
    if (awaitArgs) {
      const sessionId = commandSessionId(awaitArgs[0]);
      const roundId = awaitArgs[1];
      const timeoutMs = Number(awaitArgs[2] || DEFAULT_REVIEW_AWAIT_MS);
      const intervalMs = 100;
      const maxAttempts = Math.max(1, Math.ceil(timeoutMs / intervalMs));
      process.stdout.write(`${JSON.stringify(waitForReviewers(sessionId, {
        roundId, timeoutMs, intervalMs, maxAttempts,
      }))}\n`);
      return;
    }

    const recordResultArgs = controlArgs('--review-record-result');
    if (recordResultArgs) {
      const sessionId = commandSessionId(recordResultArgs[0]);
      const roundId = recordResultArgs[1];
      const reviewer = recordResultArgs[2];
      if (!roundId) throw new Error('--review-record-result exige roundId');
      if (!reviewer) throw new Error('--review-record-result exige reviewer');
      const agentId = readAgentIdForCommand(recordResultArgs);
      const result = await readReviewerResultForCommand(recordResultArgs);
      process.stdout.write(`${JSON.stringify(recordExplicitReviewerResult(commandPayload(sessionId), {
        roundId,
        reviewer,
        agentId,
        result,
      }))}\n`);
      return;
    }

    const finishArgs = controlArgs('--review-finish');
    if (finishArgs) {
      const sessionId = commandSessionId(finishArgs[0]);
      const roundId = finishArgs[1];
      const verdict = finishArgs[2];
      process.stdout.write(`${JSON.stringify(completeReviewRound(commandPayload(sessionId), verdict, { roundId }))}\n`);
      return;
    }

    const failArgs = controlArgs('--review-fail');
    if (failArgs) {
      const sessionId = commandSessionId(failArgs[0]);
      const roundId = failArgs[1];
      const reason = failArgs.slice(2).join(' ') || 'falha técnica da rodada';
      process.stdout.write(`${JSON.stringify(failReviewRound(commandPayload(sessionId), reason, { roundId }))}\n`);
      return;
    }

    const payload = await readPayload();
    stopPayload = payload;
    const output = evaluateStopGate(payload);
    process.stdout.write(JSON.stringify(output ?? {}));
  } catch (error) {
    const hasControlFlag = process.argv.some((arg) => [
      '--session-start', '--subagent-start', '--subagent-stop', '--review-start',
      '--review-status', '--review-await', '--review-record-result',
      '--review-finish', '--review-fail',
    ].includes(arg));

    if (!hasControlFlag) {
      if (stopPayload?.stop_hook_active) {
        process.stdout.write(JSON.stringify({
          continue: false,
          stopReason: `ENGINEERING_REVIEW_GATE_HALTED: erro técnico persistente no gate (${error.message}).`,
          systemMessage: 'Engineering Review Gate interrompeu a execução em fail-closed; erro técnico não é aprovação.',
        }));
      } else {
        process.stdout.write(JSON.stringify({
          decision: 'block',
          reason: `ENGINEERING_REVIEW_GATE_ERROR: ${error.message}`,
        }));
      }
      return;
    }

    process.stderr.write(`Gate de revisão de engenharia falhou: ${error.message}\n`);
    process.exitCode = 1;
  }
}
