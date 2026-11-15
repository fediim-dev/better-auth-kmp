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

## Common Failures

| Claim | Requires | Not Sufficient |
|-------|----------|----------------|
| Tests pass | Test command output: 0 failures | Previous run, "should pass" |
| Linter clean | Linter output: 0 errors | Partial check, extrapolation |
| Build succeeds | Build command: exit 0 | Linter passing, logs look good |
| Bug fixed | Test original symptom: passes | Code changed, assumed fixed |
| Regression test works | Red-green cycle verified | Test passes once |
| Agent completed | VCS diff shows changes | Agent reports "success" |
| Requirements met | Line-by-line checklist | Tests passing |

## Red Flags - STOP

- Using "should", "probably", "seems to"
- Expressing satisfaction before verification ("Great!", "Perfect!", "Done!", etc.)
- About to commit/push/PR without verification
- Trusting agent success reports
- Relying on partial verification
- Thinking "just this once"
- Tired and wanting work over
- **ANY wording implying success without having run verification**
- Weakening, skipping, or deleting a test, guard, or hook to make verification green (`planning/verification-gates`)

## Rationalization Prevention

| Excuse | Reality |
|--------|---------|
| "Should work now" | RUN the verification |
| "I'm confident" | Confidence ≠ evidence |
| "Just this once" | No exceptions |
| "Linter passed" | Linter ≠ compiler |
| "Agent said success" | Verify independently |
| "I'm tired" | Exhaustion ≠ excuse |
| "Partial check is enough" | Partial proves nothing |
| "Different words so rule doesn't apply" | Spirit over letter |
