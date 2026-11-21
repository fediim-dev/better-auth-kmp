// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim outline — a startLine-endLine symbol map so a big file is read in slices.
// Usage: node .fediim/tools/outline.mjs <file>...
// Prints "<file> (<n> lines)" then "<start>-<end>: <symbol>" per top-level TS/JS
// function/class/interface/type/enum/const (class methods indented under their class), or
// "<start>-<end>: ## Heading" per markdown heading; other files list def/class/fun/func
// declarations. Exit 2 = no file given, 1 = a file could not be read.
import { readFileSync } from 'node:fs';

const files = process.argv.slice(2);
if (files.length === 0) { console.error('usage: node outline.mjs <file>...'); process.exit(2); }

const DECL = /^(?:export\s+)?(?:default\s+)?(?:declare\s+)?(?:abstract\s+)?(?:async\s+)?(function\*?|class|interface|type|enum|const|let|var|namespace)\s+([A-Za-z_$][\w$]*)/;
const METHOD = /^\s+(?:(?:public|private|protected|static|async|readonly|override|get|set)\s+)*(?:\*\s*)?([A-Za-z_$#][\w$]*)\s*(?:<[^>]*>)?\(/;
const CONTINUES = /(?:[=(,&|+?:]|=>)\s*$/;
// Strip strings, line comments and multi-line block comments so declarations/braces inside comments do not count.
function stripBlockComments(lines) {
  let inBlock = false;
  return lines.map((line) => {
    let out = '';
    let quote = '';
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (inBlock) {
        if (ch === '*' && line[i + 1] === '/') { inBlock = false; i++; }
        continue;
      }
      if (quote) {
        if (ch === '\\') { out += ch + (line[i + 1] || ''); i++; continue; }
        if (ch === quote) quote = '';
        out += ch;
        continue;
      }
      if (ch === '/' && line[i + 1] === '*') { inBlock = true; i++; continue; }
      if (ch === '/' && line[i + 1] === '/') break;
      if (ch === "'" || ch === '"' || ch === '\x60') quote = ch;
      out += ch;
    }
    return out;
  });
}
const bare = (line) => line.replace(/\/\/.*$/, '').replace(/'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|\x60(?:[^\x60\\]|\\.)*\x60/g, '""');
const delta = (line) => { let d = 0; for (const ch of bare(line)) { if ('{[('.includes(ch)) d++; else if ('}])'.includes(ch)) d--; } return d; };

function endOf(lines, start) {
  let depth = 0;
  for (let i = start; i < lines.length; i++) {
    depth += delta(lines[i]);
    if (depth <= 0 && !CONTINUES.test(bare(lines[i]).trimEnd())) return i;
  }
  return lines.length - 1;
}

function outlineCode(rawLines) {
  const lines = stripBlockComments(rawLines);
  const out = [];
  let i = 0;
  while (i < lines.length) {
    const m = DECL.exec(lines[i]);
    if (!m) { i++; continue; }
    const end = endOf(lines, i);
    out.push((i + 1) + '-' + (end + 1) + ': ' + m[2] + ' (' + m[1].replace('function*', 'function') + ')');
    if (m[1] === 'class') {
      let depth = 0;
      for (let j = i; j <= end; j++) {
        const mm = depth === 1 ? METHOD.exec(lines[j]) : null;
        if (mm && !/^(?:if|for|while|switch|catch|return)$/.test(mm[1])) out.push('  ' + (j + 1) + '-' + (endOf(lines, j) + 1) + ': ' + m[2] + '.' + mm[1]);
        depth += delta(lines[j]);
      }
    }
    i = end + 1;
  }
  return out;
}

function outlineMarkdown(lines) {
  const heads = [];
  let fence = false;
  lines.forEach((line, i) => {
    if (/^\s*(\x60{3}|~{3})/.test(line)) fence = !fence;
    const m = !fence && /^(#{1,6})\s+(.*?)\s*#*\s*$/.exec(line);
    if (m) heads.push({ line: i, level: m[1].length, text: m[1] + ' ' + m[2] });
  });
  return heads.map((h, k) => {
    const next = heads.slice(k + 1).find((n) => n.level <= h.level);
    return (h.line + 1) + '-' + (next ? next.line : lines.length) + ': ' + h.text;
  });
}

const OTHER_DECL = /^(?:(?:(?:public|private|protected|internal|open|abstract|sealed|data|value|enum|annotation|inline|noinline|crossinline|suspend|override|operator|infix|tailrec|external|actual|expect|final|const|lateinit)\s+)*(fun|class|object|interface)|(async\s+def|def|func))\s+([\w.<>]+)/;
function outlineOther(rawLines) {
  const lines = stripBlockComments(rawLines);
  const starts = [];
  lines.forEach((line, i) => {
    const m = OTHER_DECL.exec(line);
    if (m) starts.push({ line: i, name: m[3], kind: m[1] || m[2] });
  });
  return starts.map((s, k) => (s.line + 1) + '-' + (k + 1 < starts.length ? starts[k + 1].line : lines.length) + ': ' + s.name + ' (' + s.kind + ')');
}

let status = 0;
for (const file of files) {
  let text;
  try { text = readFileSync(file, 'utf8'); } catch (e) { console.error('outline: cannot read ' + file); status = 1; continue; }
  const lines = text.replace(/\n$/, '').split('\n');
  console.log(file + ' (' + lines.length + ' lines)');
  const body = /\.(md|mdx)$/i.test(file) ? outlineMarkdown(lines) : /\.[cm]?[jt]sx?$/.test(file) ? outlineCode(lines) : outlineOther(lines);
  for (const row of body) console.log(row);
}
process.exit(status);
