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

if (filePath) {
  const absoluteFilePath = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);

  if (fs.existsSync(absoluteFilePath)) {
    // 1. Format via the baked glob→command map (first match on the basename wins)
    const FORMAT_MAP = {"*.kt":"ktfmt","*.kts":"ktfmt"};
    const globToRegex = (glob) =>
      new RegExp('^' + glob.replace(/[.+^$}{)(|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*') + '$');
    // Formatting with prettier's defaults loops: the agent reverts it and the
    // hook re-formats. Run a prettier command only when a config is in reach.
function hasPrettierConfig(dir) {
  for (;;) {
    let names = [];
    try { names = fs.readdirSync(dir); } catch (e) {}
    if (names.some((n) => n.startsWith('.prettierrc') || n.startsWith('prettier.config.'))) return true;
    if (names.includes('package.json')) {
      try {
        if ('prettier' in JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'))) return true;
      } catch (e) {}
    }
    const parent = path.dirname(dir);
    if (parent === dir) return false;
    dir = parent;
  }
}
    const baseName = path.basename(absoluteFilePath);
    for (const glob of Object.keys(FORMAT_MAP)) {
      if (globToRegex(glob).test(baseName)) {
        if (FORMAT_MAP[glob].includes('prettier') && !hasPrettierConfig(path.dirname(absoluteFilePath))) break;
        try {
          cp.execSync(FORMAT_MAP[glob] + (process.platform === 'win32' ? ' "%FEDIIM_FMT_FILE%"' : ' "$FEDIIM_FMT_FILE"'), {
            cwd: repoRoot,
            stdio: 'ignore',
            env: Object.assign({}, process.env, { FEDIIM_FMT_FILE: absoluteFilePath }),
          });
        } catch (e) {
          // ignore formatting failures
        }
        break;
      }
    }

    // 2. Secret scan the file on disk
    let fileContent = '';
    try {
      fileContent = fs.readFileSync(absoluteFilePath, 'utf8');
    } catch (e) {
      // ignore read failure
    }

    const secretPatterns = [
  new RegExp("(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\/+]{12,}['\"]", "i"),
  new RegExp("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", ""),
  new RegExp("AKIA[0-9A-Z]{16}", ""),
  new RegExp("\\bghp_[A-Za-z0-9]{36}\\b", ""),
  new RegExp("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b", "")
];

    for (const pattern of secretPatterns) {
      if (pattern.test(fileContent)) {
        console.error('Hardcoded secret detected on disk scan.');
        process.exit(2);
      }
    }

    // 3. A plan file must be in planner format (#113), reported on write
    // rather than at plan review or the merge gate.
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

    const planPath = path.relative(repoRoot, realPath(absoluteFilePath)).replace(/\\/g, '/');
    if (PLAN_FILE_RE.test(planPath)) {
      const errors = planFormatErrors(fileContent);
      if (errors.length > 0) {
        console.error('Plan format: fix ' + planPath + ':\n' + errors.map((e) => '- ' + e).join('\n'));
        process.exit(2);
      }
    }
  }
}
process.exit(0);
