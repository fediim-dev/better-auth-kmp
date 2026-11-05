// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim static guards — runs identically locally and in CI.
// Diff range: BASE_SHA/HEAD_SHA env (set by the CI workflow), otherwise
// merge-base origin/main..HEAD. Exit non-zero on any violation.
import { execSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const TEST_CMD = "./gradlew check -q";
const BUILD_CMD = "./gradlew compileAllTargets -q";
const LINT_CMD = "";
const SECRET_PATTERNS = [
  new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i"),
  new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", ""),
  new RegExp("AKIA[0-9A-Z]{16}", ""),
  new RegExp("\\bghp_[A-Za-z0-9]{36}\\b", ""),
  new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "")
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

let base = process.env.BASE_SHA || '';
const head = process.env.HEAD_SHA || 'HEAD';
if (!base) {
  try {
    base = sh('git merge-base origin/main HEAD');
  } catch {
    fail('cannot resolve diff base: set BASE_SHA/HEAD_SHA or ensure origin/main exists.');
    process.exit(1);
  }
}
log('diff range ' + base.slice(0, 12) + '..' + (head === 'HEAD' ? 'HEAD' : head.slice(0, 12)));

// Local run (no HEAD_SHA): the diff only sees commits, so uncommitted code
// would pass unchecked. Docs-only dirt is harmless.
if (!process.env.HEAD_SHA) {
  const dirty = execSync('git status --porcelain --untracked-files=all', { encoding: 'utf8' })
    .split('\n').filter(Boolean)
    .map((l) => l.slice(3).split(' -> ').pop().replace(/^"|"$/g, ''))
    .filter((p) => !/[.](md|txt|rst|adoc)$/i.test(p));
  if (dirty.length > 0) {
    const shown = dirty.slice(0, 10).join(', ') + (dirty.length > 10 ? ', +' + (dirty.length - 10) + ' more' : '');
    fail('uncommitted non-docs changes (' + shown + '): commit first, or the guards check nothing');
  }
}

const changedFiles = sh('git diff --name-only --no-renames ' + base + ' ' + head)
  .split('\n').filter(Boolean);

// Added lines per file from a zero-context diff.
const addedLines = new Map();
{
  const diff = execSync('git diff -U0 ' + base + ' ' + head, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  let current = '';
  for (const line of diff.split('\n')) {
    if (line.startsWith('+++ b/')) { current = line.slice(6); continue; }
    if (line.startsWith('+++')) { current = ''; continue; }
    if (current && line.startsWith('+')) {
      if (!addedLines.has(current)) addedLines.set(current, []);
      addedLines.get(current).push(line.slice(1));
    }
  }
}

// (A) Secret scan over added lines.
for (const [file, lines] of addedLines) {
  for (const line of lines) {
    for (const pattern of SECRET_PATTERNS) {
      if (pattern.test(line)) fail('secret pattern in added lines of ' + file);
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

// (D) Project verification commands (baked from fediim.toml [commands]).
// M53: a docs-only diff (memory, lessons, plans, READMEs) skips them.
const docsOnly = changedFiles.length > 0 && changedFiles.every((f) => /[.](md|txt|rst|adoc)$/i.test(f));
for (const [name, cmd] of [['test', TEST_CMD], ['build', BUILD_CMD], ['lint', LINT_CMD]]) {
  if (!cmd) { log('skip ' + name + ' (no command configured)'); continue; }
  if (docsOnly) { log('skip ' + name + ' (docs-only diff)'); continue; }
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
const FILE_EXT_RE = /\.(?:[cm]?[jt]sx?|json|toml|ya?ml|md|rst|adoc|txt|kt|kts|java|py|go|rs|rb|sh|xml|gradle|properties|html?|css|sql)$/i;
const STEP_RE = /^([ST]\d+)\s+\x60?([^\s\x60:]+)(?::\d+(?:-\d+)?)?\x60?(?:\s+\([^)]+\))?\s*(?:::|[—–-]\s)/;
const STEP_LIKE_RE = /^(?:[-*]\s+)?\**[ST]\d+\b/;
const PLAN_FILE_RE = /^\.fediim\/plans\/[^/]+\.md$/;
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
    }
  }
  return errors;
}

const addedPlans = sh('git diff --name-only --no-renames --diff-filter=A ' + base + ' ' + head)
  .split('\n').filter((f) => PLAN_FILE_RE.test(f));
for (const planFile of addedPlans) {
  const absPlan = join(process.cwd(), planFile);
  if (!existsSync(absPlan)) continue;
  const text = readFileSync(absPlan, 'utf8');
  for (const err of planFormatErrors(text)) fail('plan ' + planFile + ' ' + err);
  if (docsOnly) continue;
  let inSection = false;
  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim();
    if (/^##\s+(Steps|Tests)\b/.test(line)) { inSection = true; continue; }
    if (/^##\s+/.test(line)) { inSection = false; continue; }
    if (!inSection) continue;
    const m = stepMatch(line);
    if (!m) continue;
    const targetPath = m[2];
    const isFilePath = targetPath.includes('/') || FILE_EXT_RE.test(targetPath);
    if (isFilePath && !changedFiles.includes(targetPath)) {
      fail('plan ' + planFile + ' ' + m[1] + ' targets ' + targetPath + ' but the diff does not touch it');
    }
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

if (failures.length > 0) {
  console.error('[fediim-guards] ' + failures.length + ' violation(s).');
  process.exit(1);
}
log('all static guards passed.');
