const realFs = require('fs');
let STDIN = '';
try {
  STDIN = realFs.readFileSync(0, 'utf-8');
} catch (err) {
  // no payload: every check allows
}
class HookExit { constructor(code) { this.code = code; } }
const sharedFs = Object.assign({}, realFs, { readFileSync: (f, ...rest) => (f === 0 ? STDIN : realFs.readFileSync(f, ...rest)) });
const sharedRequire = (m) => (m === 'fs' ? sharedFs : require(m));
// A non-zero exit ends the process at once (a check's own try/catch must not swallow
// a deny); exit(0) only leaves that check.
function runCheck(body) {
  let out = '';
  const fakeProcess = new Proxy(process, {
    get: (t, k) => (k === 'exit' ? (c) => { if (c) { if (out) process.stdout.write(out); process.exit(c); } throw new HookExit(0); } : typeof t[k] === 'function' ? t[k].bind(t) : t[k]),
  });
  const fakeConsole = Object.assign(Object.create(console), { log: (...a) => { out += require('util').format(...a) + '\n'; } });
  try {
    body(sharedRequire, fakeProcess, fakeConsole);
    return { out };
  } catch (e) {
    if (e instanceof HookExit) return { out };
    console.error('hook check failed: ' + (e && e.message));
    return { out };
  }
}
const CHECKS = [
(require, process, console) => {
const fs = require('fs');
const path = require('path');
const cp = require('child_process');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // ignore
}

let cwd = (payload.workspacePaths && payload.workspacePaths[0]) || payload.cwd || process.cwd();
if (path.basename(cwd) === '.agents' || path.basename(cwd) === '.agent') {
  cwd = path.dirname(cwd);
}
function realPath(p) {
  let dir = p;
  let rest = '';
  while (!fs.existsSync(dir) && path.dirname(dir) !== dir) {
    rest = path.join(path.basename(dir), rest);
    dir = path.dirname(dir);
  }
  try {
    return path.join(fs.realpathSync(dir), rest);
  } catch (err) {
    return p;
  }
}
let repoRoot = '';
{
  let dir = realPath(cwd);
  for (;;) {
    if (fs.existsSync(path.join(dir, '.git'))) { repoRoot = dir; break; }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
}
if (!repoRoot) {
  try {
    repoRoot = require('child_process').execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || cwd;
  } catch (err) {
    repoRoot = cwd;
  }
}
repoRoot = realPath(repoRoot);
const args = payload.toolCall?.args || payload.tool_input || {};
const content = args.CodeContent || args.Content || args.content || '';
const chunkText = Array.isArray(args.ReplacementChunks) ? args.ReplacementChunks.map((c) => (c && c.ReplacementContent) || '').join('\n') : '';
const replacement = args.ReplacementContent || args.Replacement || args.new_string || args.newString || chunkText || '';
const filePath = args.TargetFile || args.Path || args.file_path || args.filePath || args.path || '';
const targetContent = content || replacement;

if (filePath) {
  const absoluteFilePath = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
  const relativeFilePath = path.relative(repoRoot, realPath(absoluteFilePath));
  const normalized = relativeFilePath.replace(/\\/g, '/');
  const lower = normalized.toLowerCase();
const outsideOf = (root, p) => { const r = path.relative(root, p); return r === '..' || r.startsWith('..' + path.sep) || path.isAbsolute(r); };
const agentDirs = [require('os').tmpdir(), path.join(require('os').homedir(), '.claude'), path.join(require('os').homedir(), '.gemini')].map((d) => realPath(d));
const escapesRoot = outsideOf(repoRoot, realPath(absoluteFilePath)) && agentDirs.every((d) => outsideOf(d, realPath(absoluteFilePath)));
  if (escapesRoot) {
    console.log(JSON.stringify({ decision: 'deny', reason: 'Write-protection: ' + filePath + ' resolves outside the repository root (' + repoRoot + ') and is blocked.' }));
    process.exit(0);
  }

  const approvalsPrefix = ".fediim/approvals/";
  if (lower === approvalsPrefix.slice(0, -1) || lower.startsWith(approvalsPrefix)) {
    console.log(JSON.stringify({ decision: 'deny', reason: 'Write-protection: Writes to ' + approvalsPrefix + ' are blocked.' }));
    process.exit(0);
  }

  const blockedPrefixes = ["src/generated/"];
  if (blockedPrefixes.some((p) => lower.startsWith(p) || lower === p.slice(0, -1))) {
    console.log(JSON.stringify({ decision: 'deny', reason: 'Write-protection: Writes to ' + blockedPrefixes.join(', ') + ' are blocked.' }));
    process.exit(0);
  }

  const protectedPrefixes = ["auth/","billing/"];
  if (protectedPrefixes.some((p) => lower.startsWith(p) || lower === p.slice(0, -1))) {
    let branch = '';
    try {
      branch = cp.execSync('git branch --show-current', { cwd: repoRoot, encoding: 'utf8', stdio: 'pipe' }).trim();
    } catch (err) {
      // ignore
    }

    const approvalPath = path.join(repoRoot, '.fediim', 'approvals', 'security-' + branch + '.ok');
    if (!fs.existsSync(approvalPath)) {
      console.log(JSON.stringify({ decision: 'deny', reason: 'Protected path: Writes to ' + protectedPrefixes.join(', ') + ' are blocked unless approval file exists at ' + approvalPath }));
      process.exit(0);
    }
  }

  // A plan file must be in planner format (#113). Antigravity has no
  // post-write hook, so a partial edit is checked on the text it would
  // produce from the file on disk (#183).
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

  let planText = content;
  const oldText = args.TargetContent || args.Target || '';
  if (PLAN_FILE_RE.test(normalized) && !planText && oldText) {
    try {
      const current = fs.readFileSync(absoluteFilePath, 'utf8');
      if (current.includes(oldText)) {
        planText = args.AllowMultiple ? current.split(oldText).join(replacement) : current.replace(oldText, () => replacement);
      }
    } catch (err) {
      // unreadable or new file: nothing to reconstruct
    }
  }
  if (PLAN_FILE_RE.test(normalized) && planText) {
    const errors = planFormatErrors(planText);
    if (errors.length > 0) {
      console.error('Plan format warning (fix before guards.mjs --check-plan): ' + normalized + ': ' + errors.join(' | '));
    }
  }

  const guardsPath = path.join(repoRoot, '.fediim', 'guards.json');
  if (fs.existsSync(guardsPath)) {
    try {
      const guards = JSON.parse(fs.readFileSync(guardsPath, 'utf8'));

      const normalizeGuard = (entry) => {
        if (typeof entry === 'string') return { file: null, pattern: entry };
        if (entry && typeof entry === 'object' && typeof entry.pattern === 'string') {
          return { file: typeof entry.file === 'string' ? entry.file : null, pattern: entry.pattern };
        }
        return null;
      };

      const normalizeAll = (entries) => (entries || []).map(normalizeGuard).filter(Boolean);

      function guardFileInScope(file, targetPath) {
  if (!file) return true;
  if (/[\^$*+?()[\]{}|\\]/.test(file)) {
    try { return new RegExp(file).test(targetPath); } catch (e) { return true; }
  }
  const scope = file.replace(/\/+$/, '');
  return targetPath === scope || targetPath.startsWith(scope + '/');
}

      const inScope = (guard, targetPath) => guardFileInScope(guard.file, targetPath);

      const compile = (guard) => {
        try {
          return new RegExp(guard.pattern, 'm');
        } catch (err) {
          return null;
        }
      };

      const enforce = (entries, kind) => {
        for (const guard of entries) {
          if (!inScope(guard, normalized)) continue;
          const regex = compile(guard);
          if (regex && regex.test(targetContent)) {
            console.log(JSON.stringify({
              decision: 'deny',
              reason: 'Security guard: forbidden ' + kind + ' in ' + normalized + ' -- matched /' + guard.pattern + '/' + (guard.file ? ' (scope: ' + guard.file + ')' : '')
            }));
            process.exit(0);
          }
        }
      };

      const isDepFile = new RegExp("(?:package\\.json|package-lock\\.json|requirements\\.txt|Gemfile|Cargo\\.toml|go\\.mod|pyproject\\.toml)$").test(normalized);

      if (isDepFile) {
        enforce(normalizeAll(guards.forbiddenDeps), 'dependency');
      } else {
        enforce(normalizeAll(guards.forbiddenImports), 'import');
      }
    } catch (e) {
      // ignore
    }
  }
}

console.log(JSON.stringify({ decision: 'allow' }));
process.exit(0);

},
(require, process, console) => {
const fs = require('fs');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // ignore
}

const args = payload.toolCall?.args || payload.tool_input || {};
const content = args.CodeContent || args.Content || args.content || '';
const chunkText = Array.isArray(args.ReplacementChunks) ? args.ReplacementChunks.map((c) => (c && c.ReplacementContent) || '').join('\n') : '';
const replacement = args.ReplacementContent || args.Replacement || args.new_string || args.newString || chunkText || '';

const secretPatterns = [
  { label: "hardcoded credential assignment", re: new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i") },
  { label: "private key header", re: new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "") },
  { label: "AWS access key ID", re: new RegExp("AKIA[0-9A-Z]{16}", "") },
  { label: "GitHub classic token", re: new RegExp("\\bgh[pousr]_[A-Za-z0-9]{36}\\b", "") },
  { label: "GitHub fine-grained token", re: new RegExp("\\bgithub_pat_[A-Za-z0-9_]{22,}\\b", "") },
  { label: "Anthropic API key", re: new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "") }
];
const INVISIBLE_RE = /[\u202A-\u202E\u2066-\u2069\u200E\u200F\u061C\u200B\u200C\uFEFF]|(?<!\p{Extended_Pictographic}[\uFE0F\u{1F3FB}-\u{1F3FF}]*)\u200D|\u200D(?!\p{Extended_Pictographic})/u;
const INVISIBLE_HINT = 'write a \\uXXXX escape instead of the literal invisible character';
function invisibleCodepoint(text) {
  const m = INVISIBLE_RE.exec(String(text));
  return m ? 'U+' + m[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0') : '';
}

for (const pattern of secretPatterns) {
  if (pattern.re.test(content) || pattern.re.test(replacement)) {
    console.log(JSON.stringify({ decision: 'deny', reason: 'Hardcoded secret detected (' + pattern.label + ').' }));
    process.exit(0);
  }
}
const invisible = invisibleCodepoint(content) || invisibleCodepoint(replacement);
if (invisible) {
  console.log(JSON.stringify({ decision: 'deny', reason: 'Invisible character ' + invisible + ' in the content: ' + INVISIBLE_HINT + '.' }));
  process.exit(0);
}
console.log(JSON.stringify({ decision: 'allow' }));
process.exit(0);

},
(require, process, console) => {
const fs = require('fs');
const path = require('path');
const cp = require('child_process');
let payload = {};
try {
  payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
} catch (err) {
  // ignore
}
let cwd = (payload.workspacePaths && payload.workspacePaths[0]) || payload.cwd || process.cwd();
if (path.basename(cwd) === '.agents' || path.basename(cwd) === '.agent') cwd = path.dirname(cwd);
function realPath(p) {
  let dir = p;
  let rest = '';
  while (!fs.existsSync(dir) && path.dirname(dir) !== dir) {
    rest = path.join(path.basename(dir), rest);
    dir = path.dirname(dir);
  }
  try {
    return path.join(fs.realpathSync(dir), rest);
  } catch (err) {
    return p;
  }
}
let repoRoot = '';
{
  let dir = realPath(cwd);
  for (;;) {
    if (fs.existsSync(path.join(dir, '.git'))) { repoRoot = dir; break; }
    const parent = path.dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
}
if (!repoRoot) {
  try {
    repoRoot = require('child_process').execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || cwd;
  } catch (err) {
    repoRoot = cwd;
  }
}
repoRoot = realPath(repoRoot);
const args = payload.toolCall?.args || payload.tool_input || {};
const content = args.CodeContent || args.Content || args.content || '';
const chunkText = Array.isArray(args.ReplacementChunks) ? args.ReplacementChunks.map((c) => (c && c.ReplacementContent) || '').join('\n') : '';
const replacement = args.ReplacementContent || args.Replacement || args.new_string || args.newString || chunkText || '';
const filePath = args.TargetFile || args.Path || args.file_path || args.filePath || args.path || '';
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

const rel = filePath ? path.relative(repoRoot, realPath(path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath))).replace(/\\/g, '/') : '';
if (rel && !rel.startsWith('..') && isSourceFile(rel)) {
  let branch = '';
  try {
    branch = cp.execFileSync('git', ['-C', repoRoot, 'branch', '--show-current'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch (err) {
    // no branch: no plan
  }
  const plans = branchPlans(branch, (f) => fs.existsSync(path.join(repoRoot, f)));
  if (plans.length > 0) {
    let implicit = [];
    try {
      implicit = (JSON.parse(fs.readFileSync(path.join(repoRoot, '.fediim', 'guards.json'), 'utf8')).implicitSharedFiles || []).map((r) => ({ when: new RegExp(r.when), implies: r.implies }));
    } catch (err) {
      // no guards.json
    }
    const scope = planScope(fs.readFileSync(path.join(repoRoot, plans[0]), 'utf8'), implicit);
    if (!inPlanScope(scope, rel)) {
      const note = 'Plan scope: ' + rel + ' is not named by any S<n>/T<n> line of ' + plans[0] + '. guards.mjs will block it: add an S<n> line, justify it in an L<n> line, or leave it out of this change.';
      console.error(note);
    }
  }
}
process.exit(0);

},
];
for (const body of CHECKS) {
  const r = runCheck(body);
  if (/"decision":"deny"/.test(r.out)) {
    process.stdout.write(r.out);
    process.exit(0);
  }
}
console.log(JSON.stringify({ decision: 'allow' }));
process.exit(0);
