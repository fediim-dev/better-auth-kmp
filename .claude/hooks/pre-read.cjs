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
const input = payload.tool_input || {};
const filePath = input.file_path || input.path || '';
const sliced = input.offset !== undefined || input.limit !== undefined;
if (filePath && !sliced) {
  try {
    const abs = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
    const lines = fs.readFileSync(abs, 'utf8').split('\n').length;
    if (lines > 500 && fs.existsSync(path.join(cwd, '.fediim', 'tools', 'outline.mjs'))) {
      const note = path.basename(abs) + ' has ' + lines + ' lines: run `node .fediim/tools/outline.mjs ' + filePath + '` for a symbol map, then Read an offset/limit slice instead of the whole file.';
      console.log(JSON.stringify({ hookSpecificOutput: { hookEventName: 'PreToolUse', additionalContext: note } }));
    }
  } catch (err) {
    // unreadable: let the tool report it
  }
}
process.exit(0);
