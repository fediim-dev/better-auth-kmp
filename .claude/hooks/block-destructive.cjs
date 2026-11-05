const fs = require('fs');

let payload = {};
try {
  const stdinText = fs.readFileSync(0, 'utf-8');
  payload = JSON.parse(stdinText);
} catch (err) {
  // If parsing fails but stdin was read, we proceed
}

const toolInput = payload.tool_input || {};
const cmd = toolInput.command || '';
const cwd = payload.cwd || process.cwd();

const { execFileSync } = require('child_process');
const path = require('path');

function shellWords(s) {
  return (s.match(/(?:'[^']*'|"[^"]*"|[^\s'"])+/g) || []).map((w) => w.replace(/['"\\]/g, ''));
}

const gitCache = new Map();
function gitOut(dir, args) {
  const key = dir + '\0' + args.join('\0');
  if (gitCache.has(key)) return gitCache.get(key);
  try {
    const out = execFileSync('git', ['-C', dir, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    gitCache.set(key, out);
    return out;
  } catch (err) {
    gitCache.set(key, '');
    return '';
  }
}

// Set by pushesProtectedBranch. A src:dst refspec, a URL or a remote other than
// origin can send an allowed feat/* push somewhere else, and no warning-free Bash
// rule can match a colon then anything.
let retargetedPush = false;

// Without this, a line continuation or a separator inside quotes cuts a push's
// later arguments off from it. A best guess: an apostrophe in a comment pairs
// with a later quote, and "\\" before a newline isn't a continuation, so the
// checks also read the raw command.
function joinPushText(command) {
  return command
    .replace(/\\\n/g, '')
    .replace(/\\[\s\S]|'[^']*'|"(?:\\[\s\S]|[^"\\])*"/g, (quoted) => quoted.replace(/[;&|()\n]/g, ' '));
}

// A statement keeps its pipes, so a flag piped in through xargs counts here. A
// flag anywhere in the statement counts, so "git push x | grep -f y" misfires,
// and so does a $ or backtick after the push, which can expand to a flag or a
// target, so "| tee $LOG" misfires too. A flag in another statement is
// flagCanReachPush's job. A misfire costs a retry, and a miss can rewrite main.
// Quantifiers are bounded so long input stays well inside the hook's timeout.
function stripHeredocs(text) {
  if (!text.includes('<<')) return text;
  return text.replace(/(?:^|[;&|\n])\s*(?:cat|tee)\b[^;&|\n]*<<-?\s*['"](\w+)['"][^\n]*\n[\s\S]*?\n\1(?=\r?\n|$)/g, '');
}

const PUSH_COMMAND = /git\b(?:\s+-\S+(?:\s+[^\s-]\S*)?){0,16}\s+push/;
function hasPush(text) {
  const s = stripHeredocs(text).replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '');
  if (!/\bpush|\b(?:remote|branch|alias)\./i.test(s)) return false;
  return PUSH_COMMAND.test(s) || /\b(?:-c\s+|config\b[^;&|\n]{0,120}\s)(?:remote|push|branch|alias)\./i.test(s);
}
function hasMergeOrGate(text) {
  const s = stripHeredocs(text).replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '');
  if (!/\b(?:merge|edit|api)\b/.test(s)) return false;
  return /\b(?:pr\s+merge|(?:issue|pr)\s+edit|gh\s+api)\b/.test(s);
}
function forcesPush(text) {
  text = stripHeredocs(text);
  if (hasPush(text)) {
    if (/(?<![-\w])[A-Za-z_]\w*=['"][^'"]*\bgit\b[^'"]*\bpush\b/.test(text)) return true;
    if (/(?:^|[;&|\n])\s*(?:function\s+\w+|\w+\s*\(\s*\))\s*[{(]/.test(text)) return true;
    if (/\bGIT_CONFIG_(?:COUNT|PARAMETERS|KEY_|VALUE_|GLOBAL|SYSTEM)\b|--config-env\b/.test(text)) return true;
    if (/\b(?:-c\s+|config\b[^;&|\n]{0,120}\s)['"]?(?:remote|push|branch|alias)\./i.test(text)) return true;
  }
  for (const raw of text.split(/&&|\|\||;|\n/)) {
    const statement = raw.replace(/['"\\]/g, '');
    const push = PUSH_COMMAND.exec(statement);
    if (!push) continue;
    if (/(?:^|\s)(?:--(?:fo(?:r(?:c(?:e)?)?)?|al(?:l)?|m(?:i(?:r(?:r(?:o(?:r)?)?)?)?)?|b(?:r(?:a(?:n(?:c(?:h(?:e(?:s)?)?)?)?)?)?)?)\b|-[a-eg-zA-Z0-9]*f|-[a-zA-Z0-9]*[*?[\]])/.test(statement)) return true;
    if (/[$\x60]/.test(statement.slice(push.index + push[0].length))) return true;
  }
  return flagCanReachPush(text);
}

// The row #94 removed blocked any command holding a git push and a flag. That
// still holds unless the command has no $, backtick, backslash, #, (), {}, redirect or heredoc, no
// statement holds two pushes, and every part between ; & | && || or a newline is a
// plain "git push …", or rm, grep, pkill, gh label, git worktree remove, or gh api whose
// first word after unquoted -X, -f, -F or -H value pairs is a repo's issues path;
// any other flag before it blocks, since gh may read the path as a value. Anything else blocks, including cd,
// a subshell, a process substitution, a shell function, a variable
// assignment and a commit message holding a flag. Listing the safe parts, not the
// unsafe ones, because each list of unsafe forms missed some.
function flagCanReachPush(text) {
  const oldFlag = /(?:^|\s)(?:--force\b|-f\b)/;
  const plain = text.replace(/['"\\]/g, '');
  if (!PUSH_COMMAND.test(plain) || !(oldFlag.test(plain) || oldFlag.test(text))) return false;
  if (/[$\x60\\#(){}]|<</.test(text)) return true;
  const pushCount = (s) => (s.replace(/['"]/g, '').match(new RegExp(PUSH_COMMAND, 'g')) || []).length;
  if (text.split(/&&|\|\||;|\n/).some((statement) => pushCount(statement) > 1)) return true;
  for (const rawPart of text.replace(/(git\s+push\b[^;&|\n]*?)\s*\b2>&1\b/g, '$1').replace(/&>/g, '2>').split(/[;&|\n]/)) {
    const part = pushCount(rawPart) ? rawPart.replace(/\b2>\s*\/dev\/null\b/g, '') : rawPart;
    if (!part.trim()) continue;
    if (/[<>]/.test(part)) return true;
    if (pushCount(part) ? !/^\s*git\s+push(?:\s|$)/.test(part)
      : !/^\s*(?:(?:rm|grep|pkill|gh\s+label|git\s+worktree\s+remove)\s|gh\s+api\s+(?:(?:-[XfFH]|--(?:method|field|raw-field|header))\s+\w[\w.=:-]*\s+)*(?!\S*\.\.)repos\/[\w.-]+\/[\w.-]+\/issues(?:\/\w+)*(?:\s|$))/.test(part)) return true;
  }
  return false;
}

// True when a git push in the command could land on main/master, push every
// branch, force-update a ref, or target a branch it can't resolve ($VAR).
// isQuotedText: this is the inside of a quoted string, often prose, so it asks only
// on a refspec, as a prompt can stall an unattended run.
const MAX_CMD_LENGTH = 8192;
const MAX_SEGMENTS = 64;
function pushesProtectedBranch(command, cwd, isQuotedText = false) {
  command = stripHeredocs(command);
  if (!hasPush(command)) return false;
  if (command.length > MAX_CMD_LENGTH) return true;
  const joined = joinPushText(command);
  if (forcesPush(joined) || forcesPush(command)) return true;
  const quoted = [...command.matchAll(/'([^']*)'|"([^"]*)"/g)].map((m) => m[1] ?? m[2]);
  if (quoted.some((q) => /\bgit\b/.test(q) && pushesProtectedBranch(q, cwd, true))) return true;
  // Redirects go first: "2>&1" isn't a separator, and a redirect target isn't a refspec.
  const withoutRedirects = joined.replace(/&>>?/g, '>').replace(/(?<!\d)\d*[<>]+&?\s*[^\s;&|()\x60]*/g, ' ');
  const joinedSegments = withoutRedirects.split(/&&|\|\||[;|&\n()\x60]/);
  const rawSegments = command.split(/&&|\|\||[;|&\n()]/);
  if (joinedSegments.filter((s) => PUSH_COMMAND.test(s.replace(/['"\\]/g, ''))).length > MAX_SEGMENTS) return true;
  // Raw segments only block: split inside quotes, they are often prose, so they never ask.
  let walkDir = cwd;
  for (const [k, segment] of [...joinedSegments, ...rawSegments].entries()) {
    if (k === joinedSegments.length) walkDir = cwd;
    const w = shellWords(segment);
    if (w[0] === 'cd') {
      const cdTarget = w.slice(1).find((x) => !x.startsWith('-'));
      walkDir = cdTarget ? path.resolve(walkDir, cdTarget) : (process.env.HOME || '/');
      continue;
    }
    for (let g = 0; g < w.length; g++) {
      if (!(w[g] === 'git' || w[g].endsWith('/git'))) continue;
      let i = g + 1;
      let dir = walkDir;
      for (; i < w.length && w[i].startsWith('-'); i++) {
        if (w[i] === '-C') dir = path.resolve(dir, w[++i] || '.');
        else if (w[i] === '-c') {
          if (/^(?:remote|push|branch|alias)\./i.test(w[++i] || '')) return true;
        } else if (/^--config-env(?:=|$)/.test(w[i])) return true;
        else if (/^(--git-dir|--work-tree|--namespace|--exec-path)$/.test(w[i])) i++;
      }
      if (w[i] !== 'push') continue;
      const positional = [];
      for (let j = i + 1; j < w.length; j++) {
        const a = w[j];
        if (/[{}]/.test(a)) return true;
        if (/^(-o|--push-option|--repo|--receive-pack|--exec)$/.test(a)) j++;
        else if (/^--(?:al(?:l)?|m(?:i(?:r(?:r(?:o(?:r)?)?)?)?)?|b(?:r(?:a(?:n(?:c(?:h(?:e(?:s)?)?)?)?)?)?)?|fo(?:r(?:c(?:e)?)?)?)$/.test(a) || /^--force/.test(a) || /^-[a-zA-Z0-9]*f/.test(a) || /^-[a-zA-Z0-9]*[*?[\]]/.test(a)) return true;
        else if (!a.startsWith('-')) positional.push(a);
      }
      const args = w.slice(i + 1);
      // A positional remote overrides --repo, so the first positional is never a refspec.
      const refspecs = positional.slice(1);
      if (refspecs.some((r) => r.startsWith('+'))) return true;
      const otherRemote = args.some((a) => /^--repo\b/.test(a)) || (positional.length && positional[0] !== 'origin');
      const asks = (isQuotedText ? refspecs : args).some((a) => a.includes(':')) || (!isQuotedText && otherRemote);
      if (k < joinedSegments.length && asks) retargetedPush = true;
      const current = gitOut(dir, ['symbolic-ref', '--short', '-q', 'HEAD']);
      if (!refspecs.length) {
        // A bare push is safe only from a feature branch tracking its own name.
        const upstream = gitOut(dir, ['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}']).replace(/^[^/]+\//, '');
        if (!current || /^(main|master)$/.test(current) || upstream !== current) return true;
        continue;
      }
      const targets = refspecs.map((r) => r.slice(r.lastIndexOf(':') + 1)).map((t) => (t === 'HEAD' || t === '@' ? current || 'main' : t));
      if (targets.some((t) => /^(?:(?:refs\/)?heads\/|refs\/)?(main|master)$/.test(t) || /[$\x60{}]/.test(t))) return true;
    }
  }
  return false;
}

function addsGatedLabel(c) {
  if (!/ready|approved-to-start/.test(c.replace(/['"\\]/g, ''))) return false;
  const s = joinPushText(c).replace(/['"\\]/g, '');
  if (/\b(?:issue|pr)\s+edit\b[^;&|\n]*--add-label[\s=]+(?:\S*,)?(?:ready|approved-to-start)(?:,|\s|$)/.test(s)) return true;
  if (/\bgh\b[^;&|\n]*\bapi\b[^;&|\n]*(?:issues\/[^/\s]+\/labels\b|addLabelsToLabelable)[^;&|\n]*\b(?:ready|approved-to-start)\b/.test(s)) return true;
  return false;
}

function removesGatedLabel(c) {
  const raw = c.replace(/['"\\]/g, '');
  if (!/needs-human-approval|issues\/|Labels/.test(raw)) return false;
  const s = joinPushText(c).replace(/['"\\]/g, '');
  if (/\b(?:issue|pr)\s+edit\b[^;&|\n]*--remove-label[\s=]+(?:\S*,)?needs-human-approval(?:,|\s|$)/.test(s)) return true;
  if (/\bgh\b[^;&|\n]*\bapi\b[^;&|\n]*(?:issues\/[^/\s]+\/labels\b|removeLabelsFromLabelable)[^;&|\n]*\bneeds-human-approval\b/.test(s)) return true;
  for (const stmt of s.split(/[;&|\n]/)) {
    if (!/\bgh\b[^;&|\n]*\bapi\b/.test(stmt)) continue;
    if (/\b(?:addLabelsToLabelable|removeLabelsFromLabelable|clearLabelsFromLabelable)\b/.test(stmt)) return true;
    if (!/(?:^|[\s/])issues\/[^/\s]+(?:\/labels)?(?:[?#]\S*)?(?:\s|$)/.test(stmt)) continue;
    if (/(?:^|\s)(?:-(?:X\s*(?:PUT|PATCH|DELETE|POST)|[fF])\b|--(?:method[\s=]+(?:PUT|PATCH|DELETE|POST)|(?:field|raw-field|input)\b))/i.test(stmt)) return true;
  }
  return false;
}

let mergeRefusal = '';
function lacksOriginMain(command, cwd) {
  mergeRefusal = '';
  if (!/\bpr\s+merge\b/.test(command.replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, ''))) return false;
  const masked = joinPushText(command);
  for (const segment of masked.split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    const start = w.findIndex((x) => !/^\w+=/.test(x));
    if (start < 0 || !(w[start] === 'gh' || w[start].endsWith('/gh'))) continue;
    let i = start + 1;
    let repoFlag = [];
    let repoSlug = '';
    for (; i < w.length && w[i].startsWith('-'); i++) {
      if (w[i] === '-R' || w[i] === '--repo') { repoSlug = w[++i] || ''; repoFlag = ['-R', repoSlug]; }
      else if (/^--repo=/.test(w[i])) { repoSlug = w[i].slice(7); repoFlag = [w[i]]; }
    }
    if (w[i] !== 'pr' || w[i + 1] !== 'merge') continue;
    const rest = w.slice(i + 2);
    let prArg = '';
    for (let j = 0; j < rest.length; j++) {
      const a = rest[j];
      if (a === '-R' || a === '--repo') { repoSlug = rest[++j] || ''; repoFlag = ['-R', repoSlug]; continue; }
      if (/^--repo=/.test(a)) { repoSlug = a.slice(7); repoFlag = [a]; continue; }
      if (/^(--body|-b|--body-file|-F|--subject|-t|--author-email|-A|--match-head-commit)$/.test(a)) { j++; continue; }
      if (a.startsWith('-')) continue;
      if (!prArg) prArg = a;
    }
    if (repoSlug) {
      const originUrl = gitOut(cwd, ['remote', 'get-url', 'origin']);
      const slugRe = new RegExp('[:/]' + repoSlug.replace(/[^\w./-]/g, '\\$&') + '(?:\\.git)?/?$', 'i');
      if (!originUrl || !slugRe.test(originUrl)) {
        mergeRefusal = 'gh pr merge blocked: -R ' + repoSlug + ' does not match cwd origin; run gh pr merge from a checkout of ' + repoSlug + '.';
        return true;
      }
    }
    let headSha = '';
    let headRef = '';
    try {
      const viewArgs = ['pr', 'view', ...(prArg ? [prArg] : []), ...repoFlag, '--json', 'headRefOid,headRefName'];
      const out = execFileSync('gh', viewArgs, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
      const parsed = JSON.parse(out) || {};
      headSha = String(parsed.headRefOid || '').trim();
      headRef = String(parsed.headRefName || '').trim();
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: could not read PR headRefOid via gh pr view.';
      return true;
    }
    if (!headSha) {
      mergeRefusal = 'gh pr merge blocked: PR headRefOid is empty.';
      return true;
    }
    try {
      execFileSync('git', ['-C', cwd, 'fetch', 'origin', 'main'], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: git fetch origin main failed — ensure origin/main is reachable before merging.';
      return true;
    }
    try {
      execFileSync('git', ['-C', cwd, 'cat-file', '-e', headSha + '^{commit}'], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      try {
        execFileSync('git', ['-C', cwd, 'fetch', 'origin', headRef || headSha], { stdio: ['ignore', 'ignore', 'ignore'] });
      } catch (fetchErr) {
        // merge-base below will report if the commit is still missing
      }
    }
    const mainSha = gitOut(cwd, ['rev-parse', 'origin/main']);
    if (!mainSha) {
      mergeRefusal = 'gh pr merge blocked: cannot resolve origin/main.';
      return true;
    }
    try {
      execFileSync('git', ['-C', cwd, 'merge-base', '--is-ancestor', mainSha, headSha], { stdio: ['ignore', 'ignore', 'ignore'] });
    } catch (err) {
      mergeRefusal = 'gh pr merge blocked: PR head ' + headSha.slice(0, 12) + ' does not contain origin/main (' + mainSha.slice(0, 12) + '). Merge origin/main into the branch, re-verify, and push before merging.';
      return true;
    }
  }
  return false;
}

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

function unconfiguredPrettierTargets(command, cwd) {
  if (!command.includes('prettier')) return [];
  const bad = [];
  let dir = cwd;
  for (const segment of command.split(/&&|\|\||[;|&\n()]/)) {
    const w = shellWords(segment);
    if (w[0] === 'cd') { dir = path.resolve(dir, w[1] || '.'); continue; }
    const i = w.findIndex((x) => x === 'prettier' || x.endsWith('/prettier') || /^prettier@/.test(x));
    if (i < 0) continue;
    const args = w.slice(i + 1);
    if (!args.some((a) => a === '--write' || a === '-w')) continue;
    if (args.some((a) => /^--(config|find-config-path)\b/.test(a))) continue;
    for (let j = 0; j < args.length; j++) {
      const a = args[j];
      if (/^--(ignore-path|plugin|parser|log-level|cache-location|cache-strategy|stdin-filepath|config-precedence)$/.test(a)) { j++; continue; }
      if (a.startsWith('-')) continue;
      const literal = a.split(/[*?{[]/)[0];
      let target = path.resolve(dir, literal || '.');
      try { if (!fs.statSync(target).isDirectory()) target = path.dirname(target); } catch (e) { target = path.dirname(target); }
      if (!hasPrettierConfig(target)) bad.push(a);
    }
  }
  return bad;
}

let prettierTargets = [];

const blockedPatterns = [
  { test: (c) => c.length > MAX_CMD_LENGTH && (hasPush(c) || hasMergeOrGate(c)), reason: 'Command exceeds maximum length (8192 bytes) and cannot be safely inspected.' },
  { test: (c) => /\.fediim\/approvals(?:\/|\b)/i.test(stripHeredocs(c).replace(/['"\\]/g, '')), reason: 'Write-protection: Writes to ' + ".fediim/approvals/" + ' are blocked.' },
  { test: (c) => c.includes('rm -rf /'), reason: 'Destructive command: rm -rf / is blocked.' },
  { test: (c) => /\bDROP\s+(TABLE|DATABASE)\b/i.test(c), reason: 'Destructive command: DROP TABLE/DATABASE is blocked.' },
  { test: (c) => c.includes('kubectl delete'), reason: 'Destructive command: kubectl delete is blocked.' },
  { test: (c) => c.includes('terraform destroy'), reason: 'Destructive command: terraform destroy is blocked.' },
  { test: (c) => c.includes('--dangerously-skip-permissions'), reason: 'Destructive flag --dangerously-skip-permissions is blocked.' },
  { test: (c) => /git\s+reset\s+--hard/.test(c), reason: 'Destructive command: git reset --hard is blocked.' },
  { test: (c) => /git\s+rebase/.test(c), reason: 'Destructive command: git rebase is blocked.' },
  { test: (c) => /git\s+(filter-branch|filter-repo)/.test(c), reason: 'Destructive command: git filter-branch/repo is blocked.' },
  { test: (c) => pushesProtectedBranch(c, cwd), reason: 'Destructive command: git push to main/master, --all/--mirror, a force push, an unresolved ($VAR) target, or a $ or backtick anywhere in the push is blocked. Push a feature branch by name, with no substitution.' },
  { test: (c) => (prettierTargets = unconfiguredPrettierTargets(c, cwd)).length > 0, get reason() { return 'prettier --write on ' + prettierTargets.join(', ') + ': no Prettier config above it, so it would be reformatted with Prettier defaults. Format only paths with a config, or use prettier --check.'; } },
  { test: (c) => /\bpr\s+merge\b/.test(c.replace(/\\\r?\n/g, ' ').replace(/['"\\]/g, '')) && /\bgh\b[^;&|\n]*\bpr\s+merge\b[^;&|\n]*--(admin)\b/.test(joinPushText(c).replace(/['"\\]/g, '')), reason: 'Destructive command: gh pr merge with admin is blocked.' },
  { test: (c) => lacksOriginMain(c, cwd), get reason() { return mergeRefusal; } },
  { test: (c) => addsGatedLabel(c), reason: 'Human-only gate: the operator applies the ready and approved-to-start labels. Ask the operator to run the gh command themselves, then wait. Do not retry, inspect this hook, or work around it.' },
  { test: (c) => removesGatedLabel(c), reason: 'Human-only gate: only the operator removes needs-human-approval. Ask the operator, then wait. Do not retry, inspect this hook, or work around it.' }
];

for (const pattern of blockedPatterns) {
  if (pattern.test(cmd)) {
    console.error(pattern.reason);
    process.exit(2);
  }
}
if (retargetedPush) {
  console.log(JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'ask', permissionDecisionReason: 'git push with a colon in its arguments or a remote other than origin can send a feature-branch push somewhere else.' } }));
}
process.exit(0);
