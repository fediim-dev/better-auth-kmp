---
name: rule-assumptions-ledger
description: Use when a requirement is missing or ambiguous mid-task and you are about to pick a default, or before writing an assumption to the task's plan file.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Assumptions ledger  <!-- pack: planning/assumptions-ledger v1.7.0 -->

On a requirements gap, record the most reasonable default as an `L<n>` line under `## Assumptions` in `.fediim/plans/<issue>.md` and proceed.
Exception: with the operator in the conversation, an unconfirmed input format or validation policy (accepted syntax, units, limits, invalid input) is a question — ask once, listing your proposed defaults, before writing code.
`reversible` never block; `irreversible` (data model, auth, public API, vendor) always block.

Line format, one per assumption (global id `#<issue>/L<n>`):

```
L1 <decision> — <rationale> — reversible | irreversible · awaiting-decision
```

- An `L<n>` line fills a gap in the spec; it never stands in for or changes
  anything the issue's `Tasks`, `Definition of Done` / acceptance criteria or
  `CONSTRAINTS.md` fix — even if the change is reversible — and the `AC:` line
  never maps to one. When an issue step or criterion can't be followed as
  written, escalate (`blocked:spec-ambiguity`) before plan review rather than
  recording an `L<n>` override.
- Format and validation questions: put every open point in one message, each
  with your proposed default (for example "a bare number means minutes?"), so
  the operator can confirm them all at once. Record the answers as `L<n>`
  lines. With no operator present (an unattended or autonomous run), record
  the defaults instead and proceed; never block on them.
- Mid-task gaps are appended to the same plan file on the task branch. No plan
  file (work outside /deliver) → a `## Assumptions` section in the PR body.
- The operator reviews assumptions in the PR diff. A veto is a PR comment or
  follow-up issue and triggers targeted re-planning of affected tasks only.
- `irreversible` assumptions always block: mark the line
  `irreversible · awaiting-decision` and escalate per the escalation rule
  (issue comment linking the line, apply `blocked`/`needs-human-approval`),
  then end the run.
- Finding prior assumptions: grep `^L[0-9]` or keywords in
  `.fediim/plans/*.md` (plus legacy `.fediim/assumptions.toml` if it exists),
  read at most 5 hits, never the whole corpus.
- Legacy `.fediim/assumptions.toml` is read-only history: never append to it.
  `fediim doctor --fix` splits it into `.fediim/plans/<task>.md` files.
