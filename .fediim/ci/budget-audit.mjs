// <!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
// fediim review budget audit — counts recorded review verdicts on an issue
// (plan review) or PR (PR review) against the budgets rule.
// Usage: node .fediim/ci/budget-audit.mjs <comments.json> <plan|pr>
// comments.json: GitHub issue comments (an array, or pages of arrays).
// Exit 0 = within budget or passed, 1 = exhausted, 2 = bad input.
import { readFileSync } from 'node:fs';

const LIMITS = {"plan":3,"pr":5};
const REVIEW = { plan: 'plan review', pr: 'PR review' };
const VERDICT_LINE = /^VERDICT:\s*(pass|block)\s*[—–-]\s*(plan review|PR review)\b/i;
const RESET_LINE = /^BUDGET:\s*reset(?:\s*[—–-]\s*(plan review|PR review))?$/i;

const [commentsPath, kind] = process.argv.slice(2);
if (!commentsPath || !Object.hasOwn(LIMITS, kind)) {
  console.error('usage: node budget-audit.mjs <comments.json> <plan|pr>');
  process.exit(2);
}
let comments;
try {
  comments = JSON.parse(readFileSync(commentsPath, 'utf8')).flat();
} catch (err) {
  console.error('[budget-audit] cannot read comments: ' + err.message);
  process.exit(2);
}

// A bare "BUDGET: reset" clears both kinds; "BUDGET: reset — <kind>" only that kind.
const verdicts = [];
for (const c of comments) {
  const first = String((c && c.body) || '').split('\n')[0].trim();
  const reset = RESET_LINE.exec(first);
  if (reset) {
    if (!reset[1] || reset[1].toLowerCase() === REVIEW[kind].toLowerCase()) verdicts.length = 0;
    continue;
  }
  const m = VERDICT_LINE.exec(first);
  if (m && m[2].toLowerCase() === REVIEW[kind].toLowerCase()) verdicts.push(m[1].toLowerCase());
}
const max = LIMITS[kind];

if (verdicts.at(-1) === 'pass') {
  console.log('budget-audit: PASS — the ' + REVIEW[kind] + ' passed.');
  process.exit(0);
}
const blocks = verdicts.filter(v => v === 'block').length;
if (blocks >= max) {
  console.log('budget-audit: EXHAUSTED — ' + blocks + ' block verdicts on the ' + REVIEW[kind] + ' (max ' + max + ').');
  console.log('The review did not converge: escalate per the escalation rule and end the run.');
  process.exit(1);
}
console.log('budget-audit: OK — next is ' + REVIEW[kind] + ' cycle ' + (blocks + 1) + ' of ' + max + '.');
