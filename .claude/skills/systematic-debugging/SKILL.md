---
name: systematic-debugging
description: Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes.
---

# Systematic Debugging

**Core principle:** ALWAYS find the root cause before attempting fixes. Symptom fixes are failure.

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

If you have not completed Phase 1, do not propose a fix.

## The Four Phases

Complete each phase in order before moving to the next.

### Phase 1: Root Cause Investigation

1. **Read error messages completely** — stack traces, file paths, line numbers, error codes.
2. **Reproduce consistently** — identify exact trigger steps. If not reproducible, gather more data instead of guessing.
3. **Check recent changes** — `git diff`, recent commits, dependency or config changes.
4. **Instrument multi-component boundaries** — when a flow spans multiple layers (CI → build → signing, API → service → DB), log inputs/outputs at each boundary once to isolate the failing layer before changing code.
5. **Trace data flow backward** — trace bad values up the call stack to their origin and fix at the source, not the symptom (see `root-cause-tracing.md` in this directory).

### Phase 2: Pattern Analysis

1. **Find working examples** of similar code in the same repository.
2. **Compare completely** against reference implementations (do not skim).
3. **List every difference** between working and broken paths, including config and environment assumptions.

### Phase 3: Hypothesis and Testing

1. **Form one explicit hypothesis:** *"X is the root cause because Y."*
2. **Test minimally:** change one variable at a time to confirm or refute the hypothesis.
3. **Verify before continuing:** if refuted, revert and form a new hypothesis based on the new evidence — never stack speculative fixes.

### Phase 4: Implementation

1. **Write a failing reproduction test first** (`coding/tdd-gate` binds when `tdd: true`).
2. **Implement a single fix** addressing the root cause — no bundled refactoring or "while I'm here" edits.
3. **Verify the fix** using `verification-before-completion` (`[commands]` build + test).
4. **Budget & architecture check:** if < 3 attempts failed, return to Phase 1 with the new evidence. If **≥ 3 fixes failed** (or each fix exposes a new issue elsewhere), **STOP** — do not attempt Fix #4; escalate the architectural issue to the operator (`planning/budgets`).

## Red Flags — STOP and Return to Phase 1

- "Quick fix for now, investigate later" or "Just try changing X and see if it works"
- Stacking multiple changes at once without isolating what failed
- Skipping the reproduction test
- Attempting a 3rd or 4th fix without re-evaluating the architecture

## Supporting Techniques (in this directory)

- **`root-cause-tracing.md`** — trace bugs backward through the call stack to the original trigger
- **`defense-in-depth.md`** — add validation at multiple layers after finding the root cause
- **`condition-based-waiting.md`** — replace arbitrary timeouts with condition polling
