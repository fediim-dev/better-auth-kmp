---
name: refactor-module
description: Use when a source file exceeds the 500-line budget, or when splitting a module, package, or sprawling file.
---
# Skill: Refactor Module

You decompose large, monolithic source files (>500 lines) into focused, cohesive
submodules while respecting architecture boundaries and preventing circular
dependencies.

## When to use

- Any source file exceeds 500 lines (or the project's length budget).
- A module carries multiple unrelated responsibilities (violating Single Responsibility).
- Adding new features requires scrolling or reasoning across disparate concerns.

## Procedure

1. **Analyze boundaries and dependencies:**
   - Map exported symbols and internal helper functions.
   - Identify cluster boundaries: domain types, pure functions, state management, I/O / adapters.
   - Verify inward-only dependencies: core domain models must not depend on UI, transport, or DB layers.
2. **Design the submodule structure:**
   - Prefer directory modules (e.g. `src/compiler/` with `index.ts` re-exporting public API) over sibling files with circular links.
   - Name submodules by domain responsibility, not generic roles (prefer `token-counter.ts`, `cache-storage.ts` over `utils.ts`, `helpers.ts`).
3. **Extract leaf dependencies first:**
   - Move pure types, interfaces, and constants first (zero incoming internal dependencies).
   - Move pure utility functions that depend only on leaf types.
   - Move stateful or I/O classes that consume the utilities and types.
4. **Maintain public API compatibility:**
   - Keep the original entry point as a facade / barrel exporting public symbols:
     ```ts
     export * from './types.js';
     export { MyManager } from './manager.js';
     ```
   - Existing external callers must compile without import path changes.
5. **Verify layering and guards:**
   - Check `.fediim/guards.json` `forbiddenImports` — verify no new cross-layer imports were introduced.
   - Run typecheck and test suite immediately:
     `npm run typecheck && npm test`
6. **Update documentation:**
   - Update module-level docstring and any relevant architectural entries in `CONSTRAINTS.md`.

## Constraints

- Never break external caller imports — use re-exports on the original entry point.
- Never create circular imports between extracted sibling submodules.
- Keep individual extracted files well under the 500-line budget.
