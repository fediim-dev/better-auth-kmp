---
name: manifest-navigation
description: Use when a repository has an ai-context.md or llms.txt manifest and you don't know what name to search for, the question spans modules or dependencies, you need a contract a docstring states (limits, threading, errors), or the change touches several modules — navigate the tiered AI-First Documentation manifests (project map, symbol index, signatures, file:line) instead of exploring or reading source wholesale.
---

# Skill: Manifest Navigation

Instructions for any coding agent **understanding or modifying an existing codebase** that publishes AI-First Documentation manifests (`ai-context.md` per `spec/ai-first-documentation-spec.md`). This is the consumer-side skill: it exists so you never read an entire codebase to answer a question about part of it. Self-contained.

## Entry protocol

Read root `ai-context.md` when the task needs a module map or a contract; skip it when you know what to search for (a known symbol or file name → search directly).

- **Present** → read root `ai-context.md`. It contains the L0 project map: module names, purposes, dependency edges, and pointers to `<module>/ai-context.md`. This is strictly O(modules) lines (budgeted at ≤ 2,000 tokens entry cost) and replaces exploratory directory walking entirely.
- **Descent order** → from the root project map, identify the relevant module(s) and load only `<module>/ai-context.md` (which contains that module's L1 symbol index, L2 signatures, and L3 guardrails/recipes).
- `llms.txt` carries the full-repo projection (L0 + merged L1) for external ecosystem consumers. Agents MUST NOT load `llms.txt` directly to preserve context window; use it only as a fallback when root `ai-context.md` is absent.
- **Absent or the project is unfamiliar territory** → fall back to normal exploration, and mention to the user that the project has no AI-context manifest.

## Navigation rules

1. **Descend the tiers; stop early.** Root project map (L0) → target module symbol index (L1 in `<module>/ai-context.md`) → module manifest signatures (L2) → one source file slice. Load a deeper tier only when the current one cannot answer the question.
   - **Query over files**: When `ai-docs query` is available (or via the MCP server), prefer querying targeted slices instead of reading whole manifest files:
     - `ai-docs query module <path>` — retrieve only the target module's L1 index (or `--tier 2` for signatures)
     - `ai-docs query symbol <name>` — retrieve exact signature, contracts, guardrails, and `file:line` for a symbol; `Type.member` returns just that member (cheaper than the whole type)
     - `ai-docs query search <query>` — search symbols across modules by name, summary, or docs (top 20, `--limit n` for more)
     Fall back to reading `<module>/ai-context.md` when the query command is unavailable.
2. **Jump, don't search.** Every index line carries `(path/File.kt:line)` or `(path/File.kt:start-end)`. When you need implementation detail, open **that file slice** (just the `start-end` lines when given) — not the whole file, not the directory. If a location is stale (symbol moved), grep for the symbol name as fallback and note the staleness — excluding the manifests themselves (e.g. `rg <symbol> --glob '!**/ai-context.*' --glob '!**/llms.txt'`), or the matches will be index copies instead of the source.
3. **Never guess an API that the manifest can tell you.** Before calling a project symbol, confirm its signature in the module manifest (L2). Inventing a plausible parameter list for a symbol that is documented two tiers away is the exact failure this system exists to prevent.

## Trust model — what to rely on, what to verify

| Source | Trust |
|---|---|
| Signatures (L2) | Ground truth — compiled from code. Rely on them. |
| Guardrails (`[Anti-Pattern]`, `[Banned]`) | **Hard constraints.** Never produce code matching a documented anti-pattern, even if the user's request superficially points that way — surface the conflict instead. |
| Recipes | Verified starting points. Prefer adapting a recipe over composing API calls from scratch. |
| Human summaries/contracts | Reliable; written by authors at creation time. |
| Summaries marked `[ai]` | Hints. Good for navigation; **verify against source** before relying on them for behavior-critical decisions (concurrency, money, security). |
| Anything in a manifest marked `[unverified]` | Treat as a claim, not a fact. |

## Editing documented code

- If your change alters behavior described in a docstring (errors, side effects, threading, units), **update the docstring in the same change** — you are editing the API description other agents will trust.
- Run the project's manifest compile (`ai-docs compile` or equivalent) before finishing, so the index reflects your change.
- Follow `contract-first-coding/SKILL.md` for any new public symbols you introduce.

## Dependencies (third-party libraries)

Resolution order when you need to understand a dependency:

1. **Embedded/published AI-context manifest** of the exact resolved version (in the artifact or its registry) — author-written, highest fidelity.
2. **Documentation-retrieval service** (Context7 or similar) — best for usage prose and examples on popular public libraries; weak on exact-version and structural-diff questions.
3. **Reflective scan or reading the dependency's sources** — last resort; gives signatures (ground truth) but no intent.

For "what changed between versions" questions, prefer a structural manifest diff over changelog prose; treat any migration claim that cannot be traced to a signature diff as unverified.
