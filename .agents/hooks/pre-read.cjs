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
const args = payload.toolCall?.args || payload.tool_input || {};
const filePath = args.AbsolutePath || args.Path || args.file_path || args.path || '';
const sliced = ['StartLine', 'EndLine', 'start_line', 'end_line', 'offset', 'limit'].some((k) => args[k] !== undefined);
if (filePath && !sliced) {
  try {
    const abs = path.isAbsolute(filePath) ? filePath : path.resolve(cwd, filePath);
    const lines = fs.readFileSync(abs, 'utf8').split('\n').length;
    if (lines > 500 && fs.existsSync(path.join(cwd, '.fediim', 'tools', 'outline.mjs'))) {
      const note = path.basename(abs) + ' has ' + lines + ' lines: run `node .fediim/tools/outline.mjs ' + filePath + '` for a symbol map, then Read an offset/limit slice instead of the whole file.';
      console.error(note);
    }
  } catch (err) {
    // unreadable: let the tool report it
  }
}
process.exit(0);
