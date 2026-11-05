---
name: rule-project-docs
description: Use when a change touches a README, the docs/ tree, or architecture or spec files, or makes existing prose documentation stale.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Project documentation  <!-- pack: coding/project-docs v1.4.0 -->

Update affected prose docs (READMEs, `docs/`, architecture/spec files) in the
same PR, never as a follow-up; link to the source of truth, don't restate it.

Inline docstrings on public symbols follow the ai-first-docs rule; this rule
governs prose and project-level documentation.

- **Keep docs in sync in the same change.** If a change alters documented
  behavior, a public API, a module's responsibility, or the architecture,
  update the affected doc/spec in the same PR. Stale prose is worse than none
  — the same discipline the ai-first-docs rule applies to docstrings.
- **Sweep before the PR.** Grep `docs/`, the nearest README, API specs
  (e.g. `openapi.yaml`), docstrings and E2E specs for every route, screen,
  old wording or public identifier whose behaviour changed; update stale prose
  in the same commit.
- **Claims stay scoped.** Prose claims follow the ai-first-docs claim rule.
- **Link to the source of truth; do not duplicate it.** Docs cite the spec,
  RFC, `CONSTRAINTS.md` constraint id, or the code — they do not restate it.
  Duplicated prose drifts; a link does not.
- **New module or package → a short doc.** One paragraph: its purpose, its
  2–3 entry-point symbols, and what it must not depend on (its boundary).
- **Docs describe the *why* and the cross-cutting shape**; code and its
  docstrings own the *what* at the symbol level. Do not narrate implementation
  in prose docs.
- If a doc change would touch a `CONSTRAINTS.md` constraint or an approved
  spec, that is a gated edit (constraints-register / spec-lock) — update the
  register/spec and the doc in the same PR, or escalate the contradiction per
  the escalation rule; never silently diverge the doc from the spec.
