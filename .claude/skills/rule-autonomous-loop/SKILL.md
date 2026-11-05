---
name: rule-autonomous-loop
description: Use when configuring or starting an unattended agent run, such as /deliver driving itself from task to task.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Autonomous run discipline  <!-- pack: planning/autonomous-loop v1.4.0 -->

Each run works one issue; a blocked issue ends the run. Never run unattended
(auto-mode) on `safety-critical`, `needs-human-approval`, or gated work.

An autonomous loop optimising a weak condition is how a gate gets quietly
eroded to "go green". These rules bound it:

- **Scope a run to one issue, and cross phase gates only on recorded pass
  verdicts.** A run may chain phases (plan → code → PR, e.g. the `/deliver`
  cycle) only when each phase gate closes with a recorded reviewer pass
  verdict; a gate that is skipped, self-assessed, or "deferred" ends the run.
  Human gates — merge, `needs-human-approval`, irreversible assumptions —
  always stop the run; never run through them.
- **Encode the real Definition of Done in the stopping condition,** not just
  "it compiles" or "tests pass". The condition must include the verification
  gates AND that no rule in this pack set was traded away (no forbidden
  dependency added, no protected path touched, no safety test weakened). A weak
  condition is the entire risk.
- **Bound every run.** Put a turn or wall-clock limit in the condition and be
  ready to interrupt. Budgets from the budgets rule still apply beneath the
  loop.
- **The mechanical gates still bind the loop.** Permission deny-lists and hooks
  hold during an autonomous run: it cannot merge, flip approval labels, or add
  a forbidden dependency even when no human is watching each step. That is why
  those gates are mechanical and not merely instructed.
- **Auto-mode is off for gated and safety-critical work.** Anything labelled
  `safety-critical` / `needs-human-approval`, or touching an `autonomy: gated`
  path, gets human-in-the-loop turns — never an unattended loop.
