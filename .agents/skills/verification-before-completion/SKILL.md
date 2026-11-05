---
name: verification-before-completion
description: Use when about to claim work is complete, fixed, or passing, and before committing or opening a pull request.
---

# Verification Before Completion

**Core principle:** Evidence before claims, always.

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

## The Gate Function

Before claiming work is complete, fixed, or passing (and in `/deliver` Phase 5 before opening a PR):

1. **IDENTIFY:** Check the project's `[commands]` `build` and `test` in `fediim.toml` (skip both only for a docs-only diff touching `.md`/`.txt`/`.rst`/`.adoc`).
2. **CHECK PLAN:** If `.fediim/plans/<n>.md` exists, verify every `I<n>`, `T<n>`, and `S<n>` is implemented in the diff.
3. **RUN:** Execute the full verification command(s) once after coding finishes (during coding and review-fix iterations, compile only; run the full test suite at the verification gate).
4. **READ:** Inspect the full output, exit code, and failure count.
5. **CLAIM WITH EVIDENCE:** State the exact command run, exit status, and HEAD SHA (`Local verification: <commands> → pass|skipped (docs-only) @ <sha>`).

## What Requires What Evidence

| Claim | Required Evidence | Not Sufficient |
|-------|-------------------|----------------|
| Build succeeds | `[commands]` build exits 0 | Linter passing, "looks right" |
| Tests pass | `[commands]` test exits 0 with 0 failures | Earlier run before latest edit, partial check |
| Bug fixed | Reproduction test fails before fix, passes after | Code changed without test |
| Subagent completed | `git diff` / `git status` inspected directly | Subagent's self-reported summary |
| Requirements met | Every acceptance item + plan `S<n>`/`I<n>`/`T<n>` checked against diff | "Tests pass" alone |

## Red Flags — STOP

- Saying "should pass", "probably fixed", or "looks good" without running the command
- Trusting a subagent's completion report without checking `git status` / `git diff`
- Weakening, skipping, or deleting a test, guard, or hook to make verification green (`coding/verification-gates`)
