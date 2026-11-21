// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim static guards — runs identically locally and in CI.
// Diff range: BASE_SHA/HEAD_SHA env (set by the CI workflow), otherwise
// merge-base origin/main..HEAD. Exit non-zero on any violation.
// --static-only skips the build/test/lint commands and the uncommitted-changes check
// (pr-prep.mjs runs it on a freshly formatted, still uncommitted tree).
import { execSync, execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';

const RAW = {"test":"./gradlew check -q","build":"./gradlew compileAllTargets -q","lint":""};
const TEST_CMD = "./gradlew check -q";
const BUILD_CMD = "./gradlew compileAllTargets -q";
const LINT_CMD = "";
const LIMITS = {"maxPrLines":450,"maxPrFiles":12,"maxFileLines":500};
const STRICT_HOOKS = false;
const STATIC_ONLY = process.argv.includes('--static-only');
const SECRET_PATTERNS = [
  { label: "hardcoded credential assignment", re: new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i") },
  { label: "private key header", re: new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "") },
  { label: "AWS access key ID", re: new RegExp("AKIA[0-9A-Z]{16}", "") },
  { label: "GitHub classic token", re: new RegExp("\\bgh[pousr]_[A-Za-z0-9]{36}\\b", "") },
  { label: "GitHub fine-grained token", re: new RegExp("\\bgithub_pat_[A-Za-z0-9_]{22,}\\b", "") },
  { label: "Anthropic API key", re: new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "") }
];
const PROTECTED_PREFIXES = ["auth/","billing/"];
const BLOCKED_PREFIXES = ["src/generated/"];
const APPROVALS_PREFIX = ".fediim/approvals/";
const DEP_FILE_REGEX = new RegExp("(?:package\\.json|package-lock\\.json|requirements\\.txt|Gemfile|Cargo\\.toml|go\\.mod|pyproject\\.toml)$");
function guardFileInScope(file, targetPath) {
  if (!file) return true;
  if (/[\^$*+?()[\]{}|\\]/.test(file)) {
    try { return new RegExp(file).test(targetPath); } catch (e) { return true; }
  }
  const scope = file.replace(/\/+$/, '');
  return targetPath === scope || targetPath.startsWith(scope + '/');
}

const INVISIBLE_RE = /[\u202A-\u202E\u2066-\u2069\u200E\u200F\u061C\u200B\u200C\uFEFF]|(?<!\p{Extended_Pictographic}[\uFE0F\u{1F3FB}-\u{1F3FF}]*)\u200D|\u200D(?!\p{Extended_Pictographic})/u;
const INVISIBLE_HINT = 'write a \\uXXXX escape instead of the literal invisible character';
function invisibleCodepoint(text) {
  const m = INVISIBLE_RE.exec(String(text));
  return m ? 'U+' + m[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0') : '';
}

function makeStamp(req) {
  const cp = req('child_process');
  const fs = req('fs');
  const path = req('path');
  const isRealCi = () => Boolean(process.env.GITHUB_ACTIONS || (process.env.CI && !process.env.FEDIIM_AUTONOMOUS_LOCAL));
  const git = (cwd, args, env) => cp.execFileSync('git', args, { cwd, env: env ? { ...process.env, ...env } : process.env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  const top = (cwd) => git(cwd, ['rev-parse', '--show-toplevel']);
  const treeHash = (cwd) => {
    let idx = '';
    try {
      const root = top(cwd);
      const real = path.resolve(root, git(root, ['rev-parse', '--git-path', 'index']));
      idx = path.resolve(root, git(root, ['rev-parse', '--git-dir']), 'fediim-idx-' + process.pid);
      if (fs.existsSync(real)) fs.copyFileSync(real, idx);
      const env = { GIT_INDEX_FILE: idx };
      // Our own run state (stamp, build-slot locks, task bindings) must not change the hash.
      git(root, ['add', '-A', '--', '.', ':(exclude).fediim/run', ':(exclude).fediim/team', ':(exclude).fediim/task-sessions'], env);
      return git(root, ['write-tree'], env);
    } catch (e) {
      return '';
    } finally {
      if (idx) { try { fs.unlinkSync(idx); } catch (e) {} }
    }
  };
  const file = (root) => path.join(root, '.fediim', 'run', 'verified.json');
  const read = (root) => { try { return JSON.parse(fs.readFileSync(file(root), 'utf8')); } catch (e) { return null; } };
  // True when every command passed on exactly this tree. Real CI never short-circuits.
  const verifiedFor = (cwd, commands) => {
    if (isRealCi()) return false;
    try {
      const v = read(top(cwd));
      if (!v || !v.treeHash || !Array.isArray(v.commands) || !commands.every((c) => v.commands.includes(c))) return false;
      return treeHash(cwd) === v.treeHash;
    } catch (e) {
      return false;
    }
  };
  // ponytail: stamps the tree as it is after the pass; an edit racing the run is stamped too.
  const record = (cwd, command) => {
    try {
      const root = top(cwd);
      const after = treeHash(cwd);
      if (!after) return;
      const v = read(root);
      const commands = [...new Set([...(v && v.treeHash === after ? v.commands : []), command])].sort();
      fs.mkdirSync(path.dirname(file(root)), { recursive: true });
      const ignore = path.join(root, '.fediim', 'run', '.gitignore');
      if (!fs.existsSync(ignore)) fs.writeFileSync(ignore, '*\n');
      let headSha = '';
      try { headSha = git(root, ['rev-parse', 'HEAD']); } catch (e) {}
      fs.writeFileSync(file(root), JSON.stringify({ treeHash: after, headSha, commands, verifiedAt: new Date().toISOString() }, null, 2) + '\n');
    } catch (e) {}
  };
  return { treeHash, verifiedFor, record, isRealCi };
}
const stamp = makeStamp(createRequire(import.meta.url));
const FILE_EXT_RE = /\.(?:[cm]?[jt]sx?|json|toml|ya?ml|md|rst|adoc|txt|kt|kts|java|py|go|rs|rb|sh|xml|gradle|properties|html?|css|sql)$/i;
const STEP_RE = /^([ST]\d+)\s+\x60?([^\s\x60:]+)(?::\d+(?:-\d+)?)?\x60?(?:\s+\([^)]+\))?\s*(?:::|[—–-]\s)/;
const STEP_LIKE_RE = /^(?:[-*]\s+)?\**[ST]\d+\b/;
const PLAN_FILE_RE = /^\.fediim\/plans\/(?:archive\/)?[^/]+\.md$/;
const stepMatch = (line) => line.replace(/^[-*]\s+/, '').replace(/^\*\*([ST]\d+)\*\*/, '$1').match(STEP_RE);
function planFormatErrors(text) {
  const errors = [];
  let inSection = false;
  for (const rawLine of String(text).split(/\r?\n/)) {
    const line = rawLine.trim();
    if (/^##\s+(Steps|Tests)\b/.test(line)) { inSection = true; continue; }
    if (/^##\s+/.test(line)) { inSection = false; continue; }
    if (/^AC:/.test(line)) {
      for (const entry of line.slice(3).split(/[·;]/).map((e) => e.trim()).filter(Boolean)) {
        const m = entry.match(/^(AC\d+)\s*(?:→|->)\s*(.+)$/);
        const ids = m ? m[2].split(/[\s,]+/).filter(Boolean) : [];
        const assumptions = ids.filter((id) => /^L\d+$/.test(id));
        if (!m) errors.push('AC entry not in \x60AC<n>→S<n>,T<n>\x60 form: ' + entry);
        else if (assumptions.length) errors.push(m[1] + ' maps to assumption ' + assumptions.join(',') + ', but an L<n> line never stands in for an acceptance criterion: ' + entry);
        else if (!ids.every((id) => /^[ST]\d+$/.test(id))) errors.push(m[1] + ' must map to S<n>/T<n> ids only: ' + entry);
      }
      continue;
    }
    if (inSection && STEP_LIKE_RE.test(line) && !stepMatch(line)) {
      errors.push('line not in planner format (\x60S<n> <path> — <change>\x60, one path per line): ' + line);
    } else if (inSection && STEP_LIKE_RE.test(line) && /\bonly if\b/i.test(line)) {
      errors.push('conditional step: guards Check E fails when the condition does not happen; move the file into an L<n> line or the verification step: ' + line);
    }
  }
  return errors;
}

const PLAN_IDENT_RE = /^[A-Za-z_$][\w$.]{2,}$/;
const PLAN_IDENT_SKIP = new Set(['node', 'npm', 'npx', 'git', 'true', 'false', 'null', 'undefined']);
let planTracked = null;
function trackedFiles() {
  if (planTracked === null) {
    try { planTracked = execFileSync('git', ['ls-files'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).split('\n').filter(Boolean); } catch { planTracked = []; }
  }
  return planTracked;
}
// One git grep for all identifiers (the gate has a 150ms budget); null = cannot tell, so no warnings.
function treeMatches(idents) {
  if (idents.length === 0) return [];
  try {
    return execFileSync('git', ['grep', '-F', '-o', '-h', '-I', ...idents.flatMap((i) => ['-e', i]), '--', '.', ':(exclude).fediim/plans'], { encoding: 'utf8', maxBuffer: 1 << 26, stdio: ['ignore', 'pipe', 'ignore'] }).split('\n');
  } catch (e) { return e.status === 1 ? [] : null; }
}
function checkPlanFile(planPath) {
  if (!existsSync(planPath)) return ['plan file not found: ' + planPath];
  const text = readFileSync(planPath, 'utf8');
  const lines = text.replace(/\n$/, '').split(/\r?\n/);
  const errors = [];
  if (lines.length > 150) errors.push('plan is ' + lines.length + ' lines; the limit is 150 (split the issue)');
  errors.push(...planFormatErrors(text));
  const defined = new Set();
  const mapped = [];
  let hasAc = false;
  let section = '';
  const idents = [];
  for (const raw of lines) {
    const line = raw.trim();
    const heading = /^##\s+(\w+)/.exec(line);
    if (heading) { section = heading[1]; continue; }
    if (/^AC:/.test(line)) {
      hasAc = true;
      for (const entry of line.slice(3).split(/[·;]/).map((e) => e.trim()).filter(Boolean)) {
        const m = entry.match(/^(AC\d+)\s*(?:→|->)\s*(.+)$/);
        if (m) mapped.push(...m[2].split(/[\s,]+/).filter(Boolean).map((id) => [m[1], id]));
      }
      continue;
    }
    if (section !== 'Steps' && section !== 'Tests') continue;
    const m = stepMatch(line);
    if (!m) continue;
    defined.add(m[1]);
    const target = m[2].split('#')[0];
    // Only the change text after the path counts: src/new.ts must not read as "creates".
    const creates = /\b(create|add|new|introduce)\b/i.test(line.slice(line.indexOf(m[2]) + m[2].length));
    for (const ident of new Set([...line.matchAll(/\x60([^\x60\s]+)\x60/g)].map((t) => t[1]))) {
      if (!PLAN_IDENT_RE.test(ident) || PLAN_IDENT_SKIP.has(ident) || ident === m[2] || FILE_EXT_RE.test(ident)) continue;
      if (!/[a-z][A-Z]|_|\.|^[A-Z0-9$]{3,}$/.test(ident)) continue;
      if (!creates) idents.push([m[1], ident]);
    }
    if (!(target.includes('/') || FILE_EXT_RE.test(target))) continue;
    const exists = existsSync(join(process.cwd(), target));
    // A bare name is a root path; it is ambiguous only when the root file is absent.
    if (!target.includes('/') && !exists && trackedFiles().filter((f) => f.endsWith('/' + target)).length > 1) {
      errors.push(m[1] + ' targets ambiguous bare file name ' + target + '; use the full repo path');
      continue;
    }
    if (!exists && !creates) errors.push(m[1] + ' targets ' + target + ', which does not exist and is not created by the plan');
  }
  const found = treeMatches([...new Set(idents.map((x) => x[1]))]);
  // -o prints the longest match, so fooBar inside fooBarBaz shows up only as a substring.
  if (found) for (const [id, ident] of idents) if (!found.some((f) => f.includes(ident))) console.error('warning: ' + id + ' names ' + ident + ', not found in the tree');
  if (!hasAc || mapped.length === 0) errors.push('plan has no AC: line mapping every acceptance criterion to S<n>/T<n> ids');
  for (const [ac, id] of mapped) {
    if (/^[ST]\d+$/.test(id) && !defined.has(id)) errors.push(ac + ' maps to ' + id + ', which has no line in ## Steps or ## Tests');
  }
  return errors;
}

const DOC_RE = /[.](md|txt|rst|adoc)$/i;
const TEST_PATH_RE = /(^|\/)(tests?|__tests__)\/|[.](test|spec)[.]/i;
const TEST_NAME_RE = /(^|\/)test_[^/]*[.]py$|_test[.](py|go)$|Tests?[.]kt$/;
const LOCK_RE = /(^|\/)(package-lock[.]json|npm-shrinkwrap[.]json|pnpm-lock[.]yaml|yarn[.]lock|Cargo[.]lock|fediim[.]lock|poetry[.]lock|go[.]sum|Gemfile[.]lock|composer[.]lock)$|[.]snap$|^tests\/fixtures\/expected\//;
const GENERATED_RE = /(^|\/)(dist|build|out|coverage|node_modules|generated|__generated__)\/|[.](min[.](js|css)|generated[.]\w+)$|^[.](claude|agents|fediim)\/|^[.]github\/workflows\/fediim-|^(AGENTS|CLAUDE|GEMINI)[.]md$/;
const SOURCE_EXT_RE = /[.](?:[cm]?[jt]sx?|vue|svelte|kts?|java|py|go|rs|rb|swift|c|cc|cpp|h|hpp|cs|php|sh)$/;
const isTestFile = (file) => TEST_PATH_RE.test(file) || TEST_NAME_RE.test(file);
const isLockFile = (file) => LOCK_RE.test(file);
const isGeneratedFile = (file) => GENERATED_RE.test(file);
const isSourceFile = (file) => SOURCE_EXT_RE.test(file) && !isTestFile(file) && !isLockFile(file) && !isGeneratedFile(file);
const SECURITY_PATH_RE = /(^|\/)(auth|authn|authz|authentication|authorization|oauth2?|rbac|tenan\w+|billing|payments?|secrets?|credentials?|migrations?)(\/|[.\-_]|$)|^[.]github\/(workflows|actions)\/|(^|\/)(jenkinsfile|[.]gitlab-ci[.]ya?ml)$|^[.](husky|githooks)\/|^[.](claude|agents|fediim)\/(hooks|settings)/i;
// Generic "hooks" dirs (React src/hooks) are not security paths; only git and agent hook dirs are.
const SECURITY_LABELS = ['security', 'safety-critical', 'needs-human-approval'];
const MODULE_ROOTS = ['src', 'lib', 'app', 'apps', 'packages', 'modules', 'internal', 'pkg', 'sources', 'services'];
const moduleOf = (file) => {
  const parts = file.split('/');
  if (parts.length === 1) return '.';
  return MODULE_ROOTS.includes(parts[0].toLowerCase()) && parts.length > 2 ? parts[0] + '/' + parts[1] : parts[0];
};
const isPlanFile = (file) => /^[.]fediim\/plans\//.test(file);
const isDocFile = (file) => DOC_RE.test(file) || isPlanFile(file);
const touchesProtected = (file) => {
  const lower = file.toLowerCase();
  return PROTECTED_PREFIXES.some((p) => lower.startsWith(p)) || BLOCKED_PREFIXES.some((p) => lower.startsWith(p));
};
function classify(rows, labels) {
  const security = [];
  const counted = new Map();
  for (const { file, lines } of rows) {
    if (touchesProtected(file) || (!isDocFile(file) && !TEST_PATH_RE.test(file) && SECURITY_PATH_RE.test(file))) security.push(file);
    if (isDocFile(file) || TEST_PATH_RE.test(file)) continue;
    counted.set(file, (counted.get(file) || 0) + lines);
  }
  const labelHit = (labels || []).some((l) => SECURITY_LABELS.includes(String(l).toLowerCase()));
  const nonTestFiles = counted.size;
  const nonTestLines = [...counted.values()].reduce((a, b) => a + b, 0);
  const modules = [...new Set([...counted.keys()].map(moduleOf))].sort();
  let tier;
  if (security.length > 0 || labelHit) tier = 4;
  else if (rows.length > 0 && nonTestFiles === 0 && rows.every((r) => isDocFile(r.file))) tier = 0;
  else if (nonTestFiles > 5 || modules.length > 1) tier = 3;
  else if (nonTestFiles <= 2 && nonTestLines <= 40) tier = 1;
  else tier = 2;
  return { tier, nonTestFiles, nonTestLines, modules, security: [...new Set(security)].sort() };
}
function planScope(text, implicit) {
  const named = new Set();
  const justified = [];
  let section = '';
  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.trim();
    const heading = /^##\s+(\w+)/.exec(line);
    if (heading) { section = heading[1]; continue; }
    if (/^(?:[-*]\s+)?\**L\d+\b/.test(line)) { justified.push(line); continue; }
    if (section !== 'Steps' && section !== 'Tests') continue;
    const m = stepMatch(line);
    if (m) named.add(m[2].replace(/\\/g, '/').replace(/^\.\//, ''));
  }
  const implied = (implicit || []).filter((r) => [...named].some((p) => r.when.test(p))).flatMap((r) => r.implies);
  return { paths: [...named, ...implied.map(String)], justified };
}
const inPlanScope = (scope, file) => scope.paths.some((p) => file === p || file.startsWith(p.replace(/\/+$/, '') + '/')) || scope.justified.some((l) => l.includes(file));
// The plan of a branch named after its issue number: .fediim/plans/<n>.md or archive/<n>.md.
const branchPlans = (branch, exists) => {
  const n = /(?:^|[\/-])#?(\d+)(?=-|$)/.exec(branch || '');
  return n ? ['.fediim/plans/' + n[1] + '.md', '.fediim/plans/archive/' + n[1] + '.md'].filter(exists) : [];
};
function resolveDefaultBranch(runGit) {
  let def = '';
  try { def = (runGit(['symbolic-ref', '--short', 'refs/remotes/origin/HEAD']) || '').trim(); } catch (e) {}
  return def;
}
function resolveBaseRef(runGit, headRef) {
  const def = resolveDefaultBranch(runGit);
  const candidates = [...new Set([def, 'origin/main', 'main', 'origin/master', 'master'].filter(Boolean))];
  for (const ref of candidates) {
    try {
      const mb = (runGit(['merge-base', headRef || 'HEAD', ref]) || '').trim();
      if (mb) return mb;
    } catch (e) {}
  }
  return '';
}

// M85: mechanical plan gate (Tier 2): no git, no LLM.
if (process.argv.includes('--check-plan')) {
  const planPath = process.argv[process.argv.indexOf('--check-plan') + 1];
  if (!planPath) { console.error('[fediim-guards] --check-plan needs a plan path'); process.exit(2); }
  const planErrors = checkPlanFile(planPath);
  for (const e of planErrors) console.error('[fediim-guards] FAIL: plan ' + planPath + ': ' + e);
  if (planErrors.length === 0) console.log('[fediim-guards] plan ' + planPath + ' passed the mechanical plan gate.');
  process.exit(planErrors.length > 0 ? 1 : 0);
}
// Self-test runner
if (process.argv.includes('--test')) {
  const testScript = join(process.cwd(), '.fediim', 'ci', 'guards-test.mjs');
  if (existsSync(testScript)) {
    try {
      execSync('node ' + testScript, { stdio: 'inherit' });
      process.exit(0);
    } catch {
      process.exit(1);
    }
  }
}

const failures = [];
const log = (msg) => console.log('[fediim-guards] ' + msg);
const fail = (msg) => { failures.push(msg); console.error('[fediim-guards] FAIL: ' + msg); };
const sh = (cmd) => execSync(cmd, { encoding: 'utf8' }).trim();

const runGit = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });

let base = process.env.BASE_SHA || '';
const head = process.env.HEAD_SHA || 'HEAD';
if (!base) {
  base = resolveBaseRef(runGit, head);
  if (!base) {
    fail('cannot resolve diff base: set BASE_SHA/HEAD_SHA or ensure the default branch (origin/main, main, origin/master, master) exists.');
    process.exit(1);
  }
}
const workingTree = STATIC_ONLY && !process.env.HEAD_SHA;
log('diff range ' + base.slice(0, 12) + '..' + (workingTree ? 'WORKTREE' : head === 'HEAD' ? 'HEAD' : head.slice(0, 12)));

// Local run (no HEAD_SHA): the diff only sees commits, so uncommitted code
// would pass unchecked. Docs-only dirt is harmless.
if (!process.env.HEAD_SHA && !STATIC_ONLY) {
  const dirty = execSync('git status --porcelain --untracked-files=all', { encoding: 'utf8' })
    .split('\n').filter(Boolean)
    .map((l) => l.slice(3).split(' -> ').pop().replace(/^"|"$/g, ''))
    .filter((p) => !/[.](md|txt|rst|adoc)$/i.test(p));
  if (dirty.length > 0) {
    const shown = dirty.slice(0, 10).join(', ') + (dirty.length > 10 ? ', +' + (dirty.length - 10) + ' more' : '');
    fail('uncommitted non-docs changes (' + shown + '): commit first, or the guards check nothing');
  }
}

const trackedRange = workingTree ? [base] : [base, head];
const diffNames = execFileSync('git', ['diff', '--name-only', '--no-renames', ...trackedRange], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  .split('\n').filter(Boolean);
const untracked = workingTree
  ? execFileSync('git', ['ls-files', '--others', '--exclude-standard'], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
      .split('\n').filter((f) => f && existsSync(f))
  : [];
const changedFiles = workingTree ? [...new Set([...diffNames, ...untracked])].sort() : diffNames;

// Added lines per file from a zero-context diff (plus untracked working-tree files in --static-only).
const addedLines = new Map();
{
  const diff = execFileSync('git', ['diff', '-U0', ...trackedRange], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  let current = '';
  for (const line of diff.split('\n')) {
    if (line.startsWith('+++ b/')) { current = line.slice(6); continue; }
    if (line.startsWith('+++')) { current = ''; continue; }
    if (current && line.startsWith('+')) {
      if (!addedLines.has(current)) addedLines.set(current, []);
      addedLines.get(current).push(line.slice(1));
    }
  }
  for (const f of untracked) {
    try {
      const buf = readFileSync(f);
      if (buf.includes(0)) continue;
      const lines = buf.toString('utf8').replace(/\r?\n$/, '');
      addedLines.set(f, lines ? lines.split(/\r?\n/) : []);
    } catch {}
  }
}
const diffRows = execFileSync('git', ['diff', '--numstat', '--no-renames', ...trackedRange], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  .split('\n').filter(Boolean)
  .map((row) => { const [add, del, ...rest] = row.split('\t'); return { file: rest.join('\t'), lines: (Number(add) || 0) + (Number(del) || 0) }; });
for (const f of untracked) {
  const lines = addedLines.get(f);
  diffRows.push({ file: f, lines: lines ? lines.length : 0 });
}

// (A) Secret scan over added lines.
for (const [file, lines] of addedLines) {
  for (const line of lines) {
    for (const pattern of SECRET_PATTERNS) {
      if (pattern.re.test(line)) fail('secret pattern in added lines of ' + file + ' (' + pattern.label + ')');
    }
  }
}

// (A2) Invisible characters over added lines (#96).
for (const [file, lines] of addedLines) {
  for (const line of lines) {
    const cp = invisibleCodepoint(line);
    if (cp) { fail('invisible character ' + cp + ' in added lines of ' + file + ': ' + INVISIBLE_HINT); break; }
  }
}

// (B) guards.json forbidden deps/imports on changed files.
const guardsPath = join(process.cwd(), '.fediim', 'guards.json');
if (existsSync(guardsPath)) {
  try {
    const guards = JSON.parse(readFileSync(guardsPath, 'utf8'));
    const forbiddenDeps = guards.forbiddenDeps || [];
    const forbiddenImports = guards.forbiddenImports || [];
    for (const [file, lines] of addedLines) {
      const patterns = DEP_FILE_REGEX.test(file) ? forbiddenDeps : forbiddenImports;
      for (const rule of patterns) {
        const patternRegex = new RegExp(typeof rule === 'object' ? rule.pattern : rule);
        if (typeof rule === 'object' && !guardFileInScope(rule.file, file)) continue;

        for (const line of lines) {
          if (patternRegex.test(line)) {
            fail('forbidden ' + (DEP_FILE_REGEX.test(file) ? 'dependency' : 'import') + ' pattern in ' + file + ': ' + line);
          }
        }
      }
    }
  } catch (e) {
    fail('.fediim/guards.json is not valid JSON: ' + e.message);
  }
}

// (C) Protected/blocked path touches.
let branch = process.env.GITHUB_HEAD_REF || '';
if (!branch) { try { branch = sh('git branch --show-current'); } catch {} }
const approvalPath = join(process.cwd(), '.fediim', 'approvals', 'security-' + branch + '.ok');
for (const file of changedFiles) {
  const lower = file.toLowerCase();
  if (lower === APPROVALS_PREFIX.slice(0, -1) || lower.startsWith(APPROVALS_PREFIX) || BLOCKED_PREFIXES.some((p) => lower.startsWith(p))) {
    fail('write to blocked path ' + file);
  }
  if (PROTECTED_PREFIXES.some((p) => lower.startsWith(p)) && !existsSync(approvalPath)) {
    fail('protected path ' + file + ' touched without approval marker ' + approvalPath);
  }
}

// (D0) M88: plan scope, PR budget and file-size ratchet. One classifier (isSourceFile)
// decides what counts: tests, lockfiles/goldens, generated files and docs never do.
const gitText = (ref, file) => { try { return execFileSync('git', ['show', ref + ':' + file], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] }); } catch { return null; } };
const countLines = (text) => (text === '' ? 0 : text.replace(/\n$/, '').split('\n').length);
{
  const sourceRows = diffRows.filter((r) => isSourceFile(r.file));
  const planFiles = [...new Set([...changedFiles.filter((f) => PLAN_FILE_RE.test(f) && existsSync(join(process.cwd(), f))), ...branchPlans(branch, (f) => existsSync(join(process.cwd(), f)))])].sort();
  const planTexts = planFiles.map((f) => readFileSync(join(process.cwd(), f), 'utf8'));
  if (planTexts.length > 0) {
    let implicit = [];
    try { implicit = (JSON.parse(readFileSync(guardsPath, 'utf8')).implicitSharedFiles || []).map((r) => ({ when: new RegExp(r.when), implies: r.implies })); } catch {}
    const scopes = planTexts.map((t) => planScope(t, implicit));
    for (const { file } of sourceRows) {
      if (!scopes.some((s) => inPlanScope(s, file))) {
        const msg = 'diff is not within the plan: ' + file + ' is named by no S<n>/T<n> line of ' + planFiles.join(', ') + ' (add an S<n> line, justify it in an L<n> line, or revert it)';
        if (STRICT_HOOKS) fail(msg);
        else log('warning: ' + msg);
      }
    }
  }
  const prLines = sourceRows.reduce((n, r) => n + r.lines, 0);
  const prFiles = sourceRows.length;
  const summary = prLines + ' non-test source lines in ' + prFiles + ' files';
  if (prLines > LIMITS.maxPrLines || prFiles > LIMITS.maxPrFiles) {
    if (planTexts.some((t) => /^##\s+Split Waiver\b/m.test(t))) log('PR budget exceeded (' + summary + ') but the plan carries ## Split Waiver');
    else fail('PR budget exceeded: ' + summary + ' (limits ' + LIMITS.maxPrLines + ' lines, ' + LIMITS.maxPrFiles + ' files): split the issue, or add a "## Split Waiver" section to the plan naming why it cannot split');
  } else if (prLines > 250 || prFiles > 8) {
    log('warning: large PR (' + summary + '); consider splitting before it reaches ' + LIMITS.maxPrLines + ' lines or ' + LIMITS.maxPrFiles + ' files');
  }
  for (const { file } of sourceRows) {
    const now = workingTree ? (existsSync(file) ? readFileSync(file, 'utf8') : null) : gitText(head, file);
    if (now === null) continue;
    const nowLines = countLines(now);
    if (nowLines <= LIMITS.maxFileLines) continue;
    const before = gitText(base, file);
    if (before === null) fail('new source file ' + file + ' has ' + nowLines + ' lines (limit ' + LIMITS.maxFileLines + '): split it into smaller modules');
    else if (nowLines > countLines(before)) fail(file + ' is over ' + LIMITS.maxFileLines + ' lines (' + countLines(before) + ' -> ' + nowLines + ') and grew: move code out instead of adding to it');
  }
}

// (D) Project verification commands (baked from fediim.toml [commands]).
// M53: a docs-only diff (memory, lessons, plans, READMEs) skips them.
const docsOnly = changedFiles.length > 0 && changedFiles.every((f) => /[.](md|txt|rst|adoc)$/i.test(f));
for (const [name, cmd] of [['test', TEST_CMD], ['build', BUILD_CMD], ['lint', LINT_CMD]]) {
  if (!cmd) { log('skip ' + name + ' (no command configured)'); continue; }
  if (docsOnly) { log('skip ' + name + ' (docs-only diff)'); continue; }
  if (STATIC_ONLY) { log('skip ' + name + ' (--static-only)'); continue; }
  // M87: run.mjs / pr-prep.mjs stamped this exact tree as passing (never when CI is set).
  if (stamp.verifiedFor(process.cwd(), [RAW[name]])) { log('skip ' + name + ' (tree verified)'); continue; }
  log('run ' + name + ': ' + cmd);
  try {
    execSync(cmd, { stdio: 'inherit' });
  } catch {
    fail(name + ' command failed: ' + cmd);
  }
}

// (D2) .fediim/assumptions.toml: a merge that drops a [[assumption]] header
// leaves duplicate keys in one table; ids must be unique across tables.
const assumptionsPath = join(process.cwd(), '.fediim', 'assumptions.toml');
if (existsSync(assumptionsPath)) {
  const seenIds = new Map();
  let keys = new Set();
  let inString = '';
  readFileSync(assumptionsPath, 'utf8').split('\n').forEach((raw, i) => {
    const n = i + 1;
    const line = raw.trim();
    if (inString) { if (line.includes(inString)) inString = ''; return; }
    if (!line || line.startsWith('#')) return;
    if (line.startsWith('[')) { keys = new Set(); return; }
    const kv = /^([A-Za-z0-9_-]+|"[^"]*"|'[^']*')\s*=\s*(.*)$/.exec(line);
    if (!kv) return;
    const [, key, value] = kv;
    const open = /^("""|''')/.exec(value);
    if (open && !value.slice(3).includes(open[1])) inString = open[1];
    if (keys.has(key)) { fail('.fediim/assumptions.toml:' + n + ' duplicate key ' + key + ' in one table (a merge likely lost a [[assumption]] header)'); return; }
    keys.add(key);
    if (key === 'id') {
      const id = value.replace(/^["']|["']\s*(#.*)?$/g, '');
      if (seenIds.has(id)) fail('.fediim/assumptions.toml:' + n + ' duplicate id ' + id + ' (first at line ' + seenIds.get(id) + ')');
      else seenIds.set(id, n);
    }
  });
}

// (E) Plans ADDED in this diff (an edit to an older plan is not re-checked).
// Format runs on every diff, including a docs-only plan commit (#113): a
// malformed S<n>/T<n> line or an AC mapped to an L<n> assumption fails here,
// not at the merge gate. Plan-to-diff coverage (every path an S<n>/T<n> line
// names is in changedFiles) waits for the code, so a docs-only diff skips it.
const headSlug = (h) => h.trim().toLowerCase().replace(/[^\w\- ]/g, '').replace(/ /g, '-');
function anchorResolves(path, id) {
  let doc;
  try {
    doc = workingTree ? readFileSync(join(process.cwd(), path), 'utf8') : execFileSync('git', ['show', head + ':' + path], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
  } catch { return false; }
  return doc.split('\n').some((l) => {
    const h = /^#{1,6}\s+(.*?)\s*#*\s*$/.exec(l);
    return (h && headSlug(h[1]) === id) || (l.startsWith(id) && !/\w/.test(l.charAt(id.length)));
  });
}
function checkTarget(planFile, lineId, target) {
  const hash = target.indexOf('#');
  const targetPath = hash < 0 ? target : target.slice(0, hash);
  const anchor = hash < 0 ? '' : target.slice(hash + 1);
  const isFilePath = targetPath.includes('/') || FILE_EXT_RE.test(targetPath);
  if (!isFilePath) return;
  if (!changedFiles.includes(targetPath)) {
    fail('plan ' + planFile + ' ' + lineId + ' targets ' + targetPath + ' but the diff does not touch it');
  } else if (anchor && !anchorResolves(targetPath, anchor)) {
    fail('plan ' + planFile + ' ' + lineId + ' anchor #' + anchor + ' does not resolve in ' + targetPath + ' at the head version');
  }
}
const trackedAdded = execFileSync('git', ['diff', '--name-only', '--no-renames', '--diff-filter=A', ...trackedRange], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  .split('\n');
const addedPlans = [...new Set([...trackedAdded, ...untracked])].filter((f) => PLAN_FILE_RE.test(f));
for (const planFile of addedPlans) {
  const absPlan = join(process.cwd(), planFile);
  if (!existsSync(absPlan)) continue;
  const text = readFileSync(absPlan, 'utf8');
  for (const err of planFormatErrors(text)) fail('plan ' + planFile + ' ' + err);
  if (text.replace(/\n$/, '').split('\n').length > 150) log('warning: plan ' + planFile + ' is over 150 lines; keep plans lean');
  if (docsOnly) continue;
  let section = '';
  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim();
    const heading = /^##\s+(Steps|Tests|Interfaces)\b/.exec(line);
    if (heading) { section = heading[1]; continue; }
    if (/^##\s+/.test(line)) { section = ''; continue; }
    if (!section) continue;
    if (section === 'Interfaces') {
      const seam = /^(?:[-*]\s+)?\**(I\d+)\**\s+`([^`]*)`/.exec(line);
      if (seam && seam[2].includes('#')) checkTarget(planFile, seam[1], seam[2]);
      continue;
    }
    const m = stepMatch(line);
    if (!m) continue;
    checkTarget(planFile, m[1], m[2]);
  }
}

// (F) Closing keywords in commits of a Refs #<n> PR (#158).
let prBody = process.env.PR_BODY || '';
if (!prBody && process.env.GITHUB_EVENT_PATH && existsSync(process.env.GITHUB_EVENT_PATH)) {
  try {
    prBody = String(JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8')).pull_request?.body || '');
  } catch {}
}
if (prBody) {
  const CLOSING_RE = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+#(\d+)\b/gi;
  const REFS_RE = /\brefs?\s+#(\d+)\b/gi;
  const closingNums = new Set([...prBody.matchAll(CLOSING_RE)].map((m) => m[1]));
  const refsOnlyNums = new Set([...prBody.matchAll(REFS_RE)].map((m) => m[1]).filter((n) => !closingNums.has(n)));
  if (refsOnlyNums.size > 0) {
    const logText = sh('git log --format=%H%x09%B%x00 ' + base + '..' + head);
    for (const entry of logText.split('\0').filter(Boolean)) {
      const tab = entry.indexOf('\t');
      const sha = entry.slice(0, tab).slice(0, 12);
      const msg = entry.slice(tab + 1);
      for (const m of msg.matchAll(CLOSING_RE)) {
        if (refsOnlyNums.has(m[1])) {
          fail('commit ' + sha + ' has closing keyword "' + m[0] + '" in a Refs #' + m[1] + ' PR (use fix(#' + m[1] + '): ...)');
        }
      }
    }
  }
}

// (G) Tier enforcement (M85). The PR declares "Tier: <0-4>" ("Fast-path: trivial"
// is an alias for Tier 1). A declared Tier 0/1 whose diff classifies higher
// needs a plan in the diff (promotion to Tier 2); a security or protected-path
// touch (Tier 4) needs security-reviewer evidence: the PR-body line
// "Security review: VERDICT: pass" (CI cannot read PR comments) or the
// approval marker. The classifier is the one triage-task.mjs uses.
const declaredMatch = /^\s*Tier:\s*([0-4])\b/im.exec(prBody);
const declaredTier = declaredMatch ? Number(declaredMatch[1]) : (/^\s*Fast-path:\s*trivial\b/im.test(prBody) ? 1 : null);
{
  const actual = classify(diffRows, []);
  if (declaredTier !== null && declaredTier < 2 && actual.tier >= 2 && !changedFiles.some((f) => PLAN_FILE_RE.test(f))) {
    fail('Tier ' + declaredTier + ' declared but the diff classifies as Tier ' + actual.tier + ' (' + actual.nonTestFiles + ' non-test files, ' + actual.nonTestLines + ' non-test lines): commit .fediim/plans/<n>.md to promote it to Tier 2, or split the change');
  }
  if (actual.security.length > 0 && !/^\s*Security review:\s*VERDICT:\s*pass\b/im.test(prBody) && !existsSync(approvalPath)) {
    const msg = 'Tier 4: ' + actual.security.slice(0, 3).join(', ') + ' touched without security-reviewer approval (PR body line "Security review: VERDICT: pass", or ' + approvalPath + ')';
    if (STATIC_ONLY && !prBody) log('note: ' + msg);
    else fail(msg);
  }
}

if (failures.length > 0) {
  console.error('[fediim-guards] ' + failures.length + ' violation(s).');
  process.exit(1);
}
log('all static guards passed.');
