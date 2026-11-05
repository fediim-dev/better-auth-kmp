---
name: rule-architecture-boundaries
description: Use when adding a module, importing across layers or packages, or editing `.fediim/guards.json`.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Architecture boundaries  <!-- pack: coding/architecture-boundaries v1.2.0 -->

Encode each module's layering boundary as a `forbiddenImports` regex in
`.fediim/guards.json`. A cross-boundary import is a defect the guard hook
blocks: fix the design or escalate — never weaken the entry.

`.fediim/guards.json` `forbiddenImports` is a project-owned array of regex
strings; `protect-paths.cjs` matches them against edited source files and
**BLOCKs** the write on a hit. That is the enforcement rail — this rule is the
doctrine for using it.

- **A new module declares its boundary.** When you add a module or package,
  state what it must *not* import and add the regex to `.fediim/guards.json`
  `forbiddenImports` in the same change — the boundary is encoded, not implied.
  Its one-paragraph boundary doc (the `project-docs` new-module doc) names the
  same constraint in prose.
- **Layer inward only.** Dependencies point one way: Routes → Services →
  Repositories, never back. A feature directory never imports a sibling
  feature. A domain/core layer stays framework-free (no web, ORM, or transport
  imports reach it). Each of these is a `forbiddenImports` entry.
- **A cross-boundary import is a defect, not a preference.** Do not route
  around the layering with a "temporary" import or a re-export shim; fix the
  design or escalate. The guard block is the same class of stop as a red test
  — never weaken or delete the `forbiddenImports` entry to get moving.
- The architecture overview and any layering exceptions live in the
  constraints register ([[constraints-register]], `CONSTRAINTS.md`); the
  per-module boundary lives in its doc ([[project-docs]]). This rule keeps
  both in sync with the enforced `guards.json` patterns.
