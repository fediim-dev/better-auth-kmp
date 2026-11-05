---
name: rule-plan-breadth
description: Use when drafting or revising any implementation plan.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Plan breadth  <!-- pack: planning/plan-breadth v1.4.0 -->

Plans list 2–3 materially different approaches, each with a substantive
rejection reason, under `Alternatives considered` — never omitted.

Breadth is mandatory scaffolding for weak-reasoning hosts and cheap for
strong ones.

- Enumerate 2–3 materially different approaches with a one-line rejection
  reason each. When only one approach is genuinely viable, the
  `Alternatives considered` section says why — it is never omitted.
- Stress-test the chosen approach before committing: trace call sites of
  changed contracts (blast radius), verify no existing helper in
  `ai-context.md` or the tree is duplicated, and map edge/failure cases into
  `T<n>` test items.
- When the spec admits more than one reading, list the plausible
  interpretations and justify the chosen one. Reversible interpretation
  choices are recorded per the assumptions-ledger rule; irreversible ones
  (data model shape, auth approach, public API surface) block.
- Rejection reasons must be substantive — a token alternative written only
  to satisfy this rule is a review finding (see the plan-reviewer rubric).
