---
name: rule-constraints-register
description: Use when a task touches, contradicts, or would amend a constraint registered in CONSTRAINTS.md.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Project constraints register  <!-- pack: planning/constraints-register v1.4.0 -->

Read `CONSTRAINTS.md` (repo root) before any task. Violating a constraint is a
defect; a specific constraint beats a generic rule; contradictions escalate.

- Amending a constraint is a gated human decision: the change must update
  the related spec/RFC and `CONSTRAINTS.md` in the same PR.
- If `CONSTRAINTS.md` still contains template placeholders for an area your
  task touches, record an assumption per the assumptions-ledger rule and
  flag the placeholder in your PR.
