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

## Red Flags - STOP and Follow Process

If you catch yourself thinking:
- "Quick fix for now, investigate later"
- "Just try changing X and see if it works"
- "Add multiple changes, run tests"
- "Skip the test, I'll manually verify"
- "It's probably X, let me fix that"
- "I don't fully understand but this might work"
- "Pattern says X but I'll adapt it differently"
- "Here are the main problems: [lists fixes without investigation]"
- Proposing solutions before tracing data flow
- **"One more fix attempt" (when already tried 2+)**
- **Each fix reveals new problem in different place**

**ALL of these mean: STOP. Return to Phase 1.**

**If 3+ fixes failed:** Question the architecture (see Phase 4, step 4).

## The operator's Signals You're Doing It Wrong

**Watch for these redirections:**
- "Is that not happening?" - You assumed without verifying
- "Will it show us...?" - You should have added evidence gathering
- "Stop guessing" - You're proposing fixes without understanding
- "Ultra-think this" - Question fundamentals, not just symptoms
- "We're stuck?" (frustrated) - Your approach isn't working

**When you see these:** STOP. Return to Phase 1.

## Common Rationalizations

| Excuse | Reality |
|--------|---------|
| "Issue is simple, don't need process" | Simple issues have root causes too. Process is fast for simple bugs. |
| "Emergency, no time for process" | Systematic debugging is FASTER than guess-and-check thrashing. |
| "Just try this first, then investigate" | First fix sets the pattern. Do it right from the start. |
| "I'll write test after confirming fix works" | Untested fixes don't stick. Test first proves it. |
| "Multiple fixes at once saves time" | Can't isolate what worked. Causes new bugs. |
| "Reference too long, I'll adapt the pattern" | Partial understanding guarantees bugs. Read it completely. |
| "I see the problem, let me fix it" | Seeing symptoms ≠ understanding root cause. |
| "One more fix attempt" (after 2+ failures) | 3+ failures = architectural problem. Question pattern, don't fix again. |

## Supporting Techniques (in this directory)

- **`root-cause-tracing.md`** — trace bugs backward through the call stack to the original trigger
- **`defense-in-depth.md`** — add validation at multiple layers after finding the root cause
- **`condition-based-waiting.md`** — replace arbitrary timeouts with condition polling
