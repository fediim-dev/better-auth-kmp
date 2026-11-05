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
const tddRequiredPath = path.resolve(repoRoot, '.fediim', 'hooks', 'tdd-required');

const TEST_CMD = "./gradlew check -q";

if (TEST_CMD && fs.existsSync(tddRequiredPath)) {
  try {
    cp.execSync(TEST_CMD, { cwd: repoRoot, stdio: 'inherit' });
  } catch (err) {
    console.log(JSON.stringify({ decision: 'continue', reason: 'TDD Gate: Tests failed.' }));
    process.exit(0);
  }
}
console.log(JSON.stringify({ decision: 'approve' }));
process.exit(0);
