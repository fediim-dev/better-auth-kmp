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

const cwd = payload.cwd || process.cwd();
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
const toolInput = payload.tool_input || {};
const filePath = toolInput.file_path || toolInput.filePath || toolInput.path || toolInput.TargetFile || '';
const content = toolInput.content || '';
const newString = toolInput.new_string || toolInput.newString || '';
const targetContent = content || newString;

if (filePath) {
  const absoluteFilePath = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
  const relativeFilePath = path.relative(repoRoot, realPath(absoluteFilePath));
  const normalized = relativeFilePath.replace(/\\/g, '/');
  const lower = normalized.toLowerCase();

  const approvalsPrefix = ".fediim/approvals/";
  if (lower === approvalsPrefix.slice(0, -1) || lower.startsWith(approvalsPrefix)) {
    console.error('Write-protection: Writes to ' + approvalsPrefix + ' are blocked.');
    process.exit(2);
  }

  const blockedPrefixes = ["src/generated/"];
  if (blockedPrefixes.some((p) => lower.startsWith(p) || lower === p.slice(0, -1))) {
    console.error('Write-protection: Writes to ' + blockedPrefixes.join(', ') + ' are blocked.');
    process.exit(2);
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
      console.error(`Protected path: Writes to ${protectedPrefixes.join(', ')} are blocked unless approval file exists at ${approvalPath}`);
      process.exit(2);
    } else {
      console.error(`⚠ SAFETY-CRITICAL EDIT: ${normalized} — approval marker present; change requires human review before merge.`);
    }
  }

  const guardsPath = path.join(repoRoot, '.fediim', 'guards.json');
  if (fs.existsSync(guardsPath)) {
    try {
      const guards = JSON.parse(fs.readFileSync(guardsPath, 'utf8'));

      // A guard entry is either a bare pattern string, or {file, pattern} where `file`
      // scopes the pattern to a path prefix. Anything else is malformed: warn and skip it
      // rather than letting one bad entry disable the whole guard.
      const normalizeGuard = (entry) => {
        if (typeof entry === 'string') return { file: null, pattern: entry };
        if (entry && typeof entry === 'object' && typeof entry.pattern === 'string') {
          return { file: typeof entry.file === 'string' ? entry.file : null, pattern: entry.pattern };
        }
        console.error('Security guard: skipping malformed guard entry: ' + JSON.stringify(entry));
        return null;
      };

      const normalizeAll = (entries) => (entries || []).map(normalizeGuard).filter(Boolean);

      // `file` scopes a guard to a path prefix or a regex (see guardFileInScope). An
      // unscoped entry applies everywhere.
      function guardFileInScope(file, targetPath) {
  if (!file) return true;
  if (/[\^$*+?()[\]{}|\\]/.test(file)) {
    try { return new RegExp(file).test(targetPath); } catch (e) { return true; }
  }
  const scope = file.replace(/\/+$/, '');
  return targetPath === scope || targetPath.startsWith(scope + '/');
}

      const inScope = (guard, targetPath) => guardFileInScope(guard.file, targetPath);

      // Multiline: the patterns anchor with ^ and must match any line. Without the `m`
      // flag ^ binds to the first byte of the file, and a Kotlin file opens with
      // `package ...`, so `^import` could never match a real source file.
      const compile = (guard) => {
        try {
          return new RegExp(guard.pattern, 'm');
        } catch (err) {
          console.error('Security guard: skipping malformed pattern /' + guard.pattern + '/: ' + err.message);
          return null;
        }
      };

      const enforce = (entries, kind) => {
        for (const guard of entries) {
          if (!inScope(guard, normalized)) continue;
          const regex = compile(guard);
          if (regex && regex.test(targetContent)) {
            console.error(
              'Security guard: forbidden ' + kind + ' in ' + normalized +
              ' -- matched /' + guard.pattern + '/' +
              (guard.file ? ' (scope: ' + guard.file + ')' : '')
            );
            process.exit(2);
          }
        }
      };

      const isDepFile = new RegExp("(?:package\\.json|package-lock\\.json|requirements\\.txt|Gemfile|Cargo\\.toml|go\\.mod|pyproject\\.toml)$").test(normalized);

      if (isDepFile) {
        enforce(normalizeAll(guards.forbiddenDeps), 'dependency');
      } else {
        enforce(normalizeAll(guards.forbiddenImports), 'import');
      }

      for (const guard of normalizeAll(guards.flagPaths)) {
        const regex = compile(guard);
        if (regex && regex.test(normalized)) {
          console.error('⚠ Security warning: flagged path edit: ' + normalized);
        }
      }

      for (const guard of normalizeAll(guards.flagContent)) {
        if (!inScope(guard, normalized)) continue;
        const regex = compile(guard);
        if (regex && regex.test(targetContent)) {
          console.error('⚠ Security warning: flagged content in ' + normalized + ' -- matched /' + guard.pattern + '/');
        }
      }
    } catch (e) {
      console.error('Security guard: could not evaluate ' + guardsPath + ': ' + e.message);
    }
  }
}
process.exit(0);
