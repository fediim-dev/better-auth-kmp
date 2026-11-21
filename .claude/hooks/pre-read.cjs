const fs = require('fs');
const path = require('path');
const cp = require('child_process');
let payload = {};
try {
  payload = JSON.parse(fs.readFileSync(0, 'utf-8'));
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
const input = payload.tool_input || {};
const filePath = input.file_path || input.path || '';
const sliced = input.offset !== undefined || input.limit !== undefined;
// M95: stamp the linked worktree a command runs in; fediim session prune keeps one stamped in the last hour.
// No git call: in a linked worktree .git is a file naming its admin folder. Fails open.
function markUsed(dir) {
  try {
    let cur = path.resolve(dir);
    while (!fs.existsSync(path.join(cur, '.git')) && path.dirname(cur) !== cur) cur = path.dirname(cur);
    const dotGit = path.join(cur, '.git');
    if (!fs.statSync(dotGit).isFile()) return;
    const m = /^gitdir: (.+)$/m.exec(fs.readFileSync(dotGit, 'utf8'));
    if (m) fs.writeFileSync(path.join(path.resolve(cur, m[1].trim()), 'fediim-used'), '');
  } catch (err) {
    // not a worktree, or not writable
  }
}
markUsed(cwd);
if (filePath) markUsed(path.dirname(path.resolve(cwd, filePath)));
if (filePath && !sliced) {
  try {
    const abs = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
    const st = fs.statSync(abs);
    if (st.isFile() && st.size <= 2 * 1024 * 1024 && fs.existsSync(path.join(repoRoot, '.fediim', 'tools', 'outline.mjs'))) {
      const fd = fs.openSync(abs, 'r');
      let isBinary = false;
      try {
        const probe = Buffer.alloc(Math.min(8192, st.size));
        const n = probe.length ? fs.readSync(fd, probe, 0, probe.length, 0) : 0;
        isBinary = probe.subarray(0, n).includes(0);
      } finally { fs.closeSync(fd); }
      if (!isBinary) {
        const lines = fs.readFileSync(abs, 'utf8').split('\n').length;
        if (lines > 500) {
          const note = path.basename(abs) + ' has ' + lines + ' lines: run `node .fediim/tools/outline.mjs ' + filePath + '` for a symbol map, then Read an offset/limit slice instead of the whole file.';
          console.log(JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', additionalContext: note } }));
        }
      }
    }
  } catch (err) {
    // unreadable: let the tool report it
  }
}
process.exit(0);
