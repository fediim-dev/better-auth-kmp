---
name: ai-doc-authoring
description: Use when creating or changing a public symbol or module — discover first, write the contract docstring, then implement.
---
# Skill: AI-First Documentation Authoring

Apply when you create or change a **public** symbol or module. Covers inline
docstrings and the contract-first order; READMEs and `docs/` follow the
project-docs rule. Conforms to `spec/ai-first-documentation-spec.md` v1.0.

## The one rule

**Document only what the signature cannot express.** Names and types are the
primary documentation. First try to make a comment unnecessary: rename
(`timeout` → `timeoutMillis`), tighten the type, narrow the visibility.

## Procedure (new public symbol)

1. **Discover before you create.** Search `ai-context.md` (L1 symbol index or
   the module manifest) for overlapping symbols; extend or reuse a near-match
   and say why if you cannot. Check the module's L3 guardrails: new code must
   not be a documented `[Anti-Pattern]`. Use design tokens, not hardcoded
   values a token defines.
2. **Write the contract first**: signature and docstring before the body.
   If the summary does not fit one sentence, split the symbol first.
3. **Implement to the contract.** If the body forces a contract change, update
   the docstring in the same edit and tell the user: it is an API decision.
4. **Recipe and registration.** New component: one minimal `[Recipe]` that
   compiles against the final API. New module: a module docstring (purpose,
   2–3 entry points, forbidden dependencies) registered in the project's docs
   config (`ai-docs.config.json` or equivalent).

Private symbols: docstring optional. Prototypes: exclude the module from the
manifest rather than ship half-true contracts. Never hand-edit manifests.

## Docstring quick reference

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

## Never

- Restate a name or type (`/** Gets the user. */ fun getUser()`), or write
  `@param`/`@returns` that only repeat the type.
- Narrate implementation; inline comments explain *why* only.
- Put changelog entries, authors, or dates in docstrings.
- Document trivial members or exceed the budget.
- Include secrets, internal hostnames, or `TODO`/`FIXME` text.

## Pre-finish checklist

- [ ] Discovery done; new public symbols have a summary, changed ones stay true.
- [ ] Units, threading, lifecycle and errors stated where types don't.
- [ ] Traps are `[Anti-Pattern]`; recipes compile against the committed API.
- [ ] Manifests recompiled (`ai-docs compile`); `ai-docs lint` passes.
- [ ] `pr-reviewer` checks contracts, `[Recipe]`, and stale docs in `/deliver`; load `audit.md` for standalone audits.
