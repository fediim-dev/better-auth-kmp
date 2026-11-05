---
name: inline-ai-doc-encoder
description: Use when creating or modifying any public class, interface, enum, function, or exported constant.
---
# Skill: Writing AI-First Documentation

Apply whenever you create or change a **public** symbol. Covers inline
docstrings only; READMEs, `docs/` and architecture files follow the
project-docs rule. Conforms to `spec/ai-first-documentation-spec.md` v1.0.

## The one rule

**Document only what the signature cannot express.** Names and types are the
primary documentation. First try to make a comment unnecessary: rename
(`timeout` → `timeoutMillis`), tighten the type, narrow the visibility.

## Quick reference

1. Line 1: imperative summary — what + when + what distinguishes it — ≤ 300 chars.
2. Contracts, only the non-obvious ones: units (`maxSize bytes`), threading
   (`thread-safe`), lifecycle (`caller closes`), side effects, errors.
3. Known traps as `[Anti-Pattern]`; one compiling `[Recipe]` per component.
4. Deprecations: `@deprecated [Migrate: <replacement>]`, always with a path.
5. Budget: 50–80 tokens per class (150 max), 1–2 lines per non-obvious
   function, zero for trivial members.

Tags (inside standard KDoc / JSDoc / PEP 257 / Rustdoc / Godoc; no others):
`[Anti-Pattern]` forbidden usage + why · `[Banned]` + replacement ·
`[Recipe]` minimal verified usage · `[Invariant]` property callers may rely on ·
`[SideEffects]` non-obvious I/O or global state ·
`@tokens` / `@DesignTokens` design-token containers.

**Load `templates.md` in this folder when writing a docstring** — it holds the
per-language templates, a worked example and an anti-example.

## Procedure

1. Name the symbol so it needs no comment.
2. Write the one-sentence summary. Never start with "This class/function…".
3. Add only the contracts a reader would guess wrong.
4. Add `[Anti-Pattern]` for any plausible-but-wrong usage.
5. Add or update the component's `[Recipe]` whenever its API changes.
6. New module: add a module docstring — purpose, the 2–3 entry points, what
   it must not depend on.
7. Changed behavior: update the docstring in the same change.
8. Deprecation: name the exact replacement symbol or procedure.

## Never

- Restate a name or type (`/** Gets the user. */ fun getUser()`), or write
  `@param`/`@returns` that only repeat the type.
- Narrate implementation; inline comments explain *why* and constraints only.
- Put changelog entries, authors, or dates in docstrings.
- Document trivial members or exceed the budget.
- Include secrets, internal hostnames, or `TODO`/`FIXME` text.

## Pre-finish checklist

- [ ] Every new public symbol has a one-sentence summary.
- [ ] Every changed public symbol's docstring still tells the truth.
- [ ] Units, threading, lifecycle and errors are stated where types don't.
- [ ] Known traps are `[Anti-Pattern]`; recipes compile against the current API.
- [ ] No comment restates a name or type; no trivial member is documented.
- [ ] If the `doc-auditor` persona is installed, its `/deliver` gate checks
      these docstrings — run it over your diff and clear any `block` findings
      first.
