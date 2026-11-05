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
let repoRoot = cwd;
try {
  repoRoot = require('child_process').execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || cwd;
} catch (err) {
  // not a git repo: the cwd is the root
}
repoRoot = realPath(repoRoot);
const args = payload.toolCall?.args || payload.tool_input || {};
const filePath = args.TargetFile || args.file_path || args.filePath || args.path || '';
const content = args.CodeContent || args.content || '';
const replacement = args.ReplacementContent || args.new_string || args.newString || '';
const targetContent = content || replacement;

if (filePath) {
  const absoluteFilePath = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
  const relativeFilePath = path.relative(repoRoot, realPath(absoluteFilePath));
  const normalized = relativeFilePath.replace(/\\/g, '/');
  const lower = normalized.toLowerCase();

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
  // post-write hook, so only a whole-file write is checked; guards.mjs
  // catches a plan finished through partial edits.
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

  if (PLAN_FILE_RE.test(normalized) && content) {
    const errors = planFormatErrors(content);
    if (errors.length > 0) {
      console.log(JSON.stringify({ decision: 'deny', reason: 'Plan format: fix ' + normalized + ': ' + errors.join(' | ') }));
      process.exit(0);
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
