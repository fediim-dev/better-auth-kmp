---
name: receiving-code-review
description: Use when receiving code review feedback, before implementing suggestions — especially when the feedback seems unclear or technically questionable.
---

# Code Review Reception

**Core principle:** Verify feedback against the codebase and `.fediim/plans/<n>.md` before implementing. Technical correctness beats performative agreement.

## The Response Pattern

1. **READ:** Read all feedback items completely before editing.
2. **UNDERSTAND & CLARIFY:** If an item is ambiguous and reversible during `/deliver`, record the chosen interpretation in `.fediim/plans/<n>.md` under `## Assumptions` (`L<n>`); if it is irreversible or from the operator interactively, ask before implementing.
3. **VERIFY:** Check each suggestion against the actual codebase, callers, and `CONSTRAINTS.md`.
4. **EVALUATE:** Does the suggestion break existing behavior, violate YAGNI, or conflict with the approved spec/plan?
5. **RESPOND:** Give a concise technical acknowledgment (`Fixed in <file:line>`) or reasoned technical pushback citing code/tests — never performative praise (`"You're absolutely right!"`, `"Great point!"`, `"Thanks!"`).
6. **IMPLEMENT:** Apply valid fixes in priority order (blocking/security → simple → complex). In `/deliver` Phase 7 review cycles, run the formatter and compile (`[commands]` build) before pushing; the full test suite runs once at the verification/merge gate.

## When to Push Back

Push back with concrete evidence (`file:line`, test cases, `CONSTRAINTS.md`, or `## Assumptions`) when a suggestion:
- Breaks existing functionality or callers
- Adds unused/unrequested scope (violates spec-lock or YAGNI)
- Is technically invalid for the project's stack/target versions
- Conflicts with prior operator decisions in `.fediim/memory/rules.md` or `CONSTRAINTS.md`

If your pushback turns out to be wrong after checking, state the factual correction concisely and implement the fix.

## GitHub Thread Replies

When replying to inline review comments on GitHub, reply in the comment thread (`gh api repos/{owner}/{repo}/pulls/{pr}/comments/{id}/replies`), not as a top-level PR comment.
