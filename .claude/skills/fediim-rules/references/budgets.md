<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Budgets  <!-- pack: planning/budgets v1.8.0 -->

Every task is bounded (max attempts, cost, wall-clock). Review loops get at
most 3 plan-review and 5 PR-review cycles, counted by CI from the recorded
verdicts; on exhaustion, escalate and end the run.

## Forced convergence

A human can restart a review budget by commenting `BUDGET: reset` (both kinds)
or `BUDGET: reset — plan review` / `BUDGET: reset — PR review` (that kind
only); CI counts only verdicts posted after the last applicable reset.

Review loops must converge, not oscillate:

- Plan review: max 3 review cycles. On the final cycle the reviewer
  approves despite non-blocking open items, and the host files each one
  as a backlog issue (label `wave:backlog`). Only a blocking rule violation
  that survived all cycles escalates to a human instead.
- PR review: max 5 review cycles. On the final cycle without approval,
  escalate per the escalation rule with a summary of every attempt and why
  it failed. Fix rounds 1–3 resume the original implementer; rounds 4–5 use a
  fresh context one tier above whoever got stuck (mechanical → standard;
  standard → the host or a fresh top-tier subagent) with the brief, the
  report file and the open findings.
- **Counting.** Cycles are counted from the verdict comments the
  review-quorum rule requires, never from a running tally: the next cycle is
  one more than the earlier `VERDICT: block — … review` comments. The
  `fediim-budgets` CI action (`.fediim/ci/budget-audit.mjs`) runs on every
  verdict comment and labels the issue/PR `blocked` once the budget is
  exhausted. That label is authoritative: end the run.
- A reviewer brief must always include: the task/issue spec, the current
  iteration number and maximum, and the reviewer's authorities (labels it
  may change, whether it may merge).
