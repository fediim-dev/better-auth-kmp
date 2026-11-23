# Documentation Audit Checklist

Use when auditing a diff or repository for inline docstring contracts (`ai-doc-authoring`) and prose/project documentation freshness (`project-docs`). Check only; do not rewrite prose or code style unless asked to fix findings.

## Pre-check

When `ai-docs.config.json` exists, run `ai-docs compile && ai-docs lint` first so mechanical manifest and lint gaps are caught before manual review.

## Procedure

1. Read the diff or target file list. Identify:
   - Every public symbol added or whose signature/behaviour changed.
   - Every prose or project doc the change touched or should have updated (`README*`, `docs/`, architecture/spec files).
2. For each **public symbol**, check:
   - One-sentence summary on line 1 (`<= 300` chars) that does not restate the name or signature.
   - Non-obvious contracts stated (units, threading, lifecycle, side effects, errors).
   - Known traps encoded as `[Anti-Pattern]`; new components carry one compiling `[Recipe]`.
   - No trivial-member docs and no `@param`/`@returns` lines that only repeat types.
   - Changed symbols: existing docstring still matches the new behaviour.
3. Check budgets: full class docstring `<= 150` tokens; methods `0-2` lines.
4. For **prose/project docs**, check freshness and linkage:
   - Any change to documented behaviour, a public API, a module's responsibility, or architecture updated the affected doc/spec in the **same** diff.
   - Any new module or package includes its one-paragraph boundary doc.
   - Docs link to the source of truth (spec, RFC, `CONSTRAINTS.md` id, code) rather than duplicating it.

## Output format

```
VERDICT: pass | block
FINDINGS:
  - severity: high | medium | low
    file: <path:line>
    symbol: <name or "(prose)">
    issue: <one line — which checklist item failed>
    fix: <one line — what to write or delete>
```

`block` only for: a missing summary on a new public symbol, a stale contract, a `[Recipe]` that no longer matches the API, or a prose/project doc left stale by the change (describing replaced behaviour or a new module missing a boundary doc). Style and budget findings are `medium`/`low` and do not block.
