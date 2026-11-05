---
name: rule-parallel-dispatch
description: Use when considering running more than one issue at the same time with parallel agents or worktrees.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Parallel dispatch  <!-- pack: planning/parallel-dispatch v1.6.0 -->

An orchestrator MAY run issues in parallel only with pairwise-disjoint Files
Affected — one agent/branch/worktree each; reviews and merges stay serial.

- **Eligibility.** Dispatch issues in parallel only when, for every issue in
  the batch: all of its declared dependencies (`Depends on #NN`) are closed,
  it is labelled `ready` and not `blocked`/`needs-human-approval`, and its
  **Files Affected** section is pairwise disjoint with every other issue in
  the batch. An issue without a Files Affected section is NOT eligible for
  parallel dispatch — run it alone. Neither is an issue with an unchecked
  operator task (`- [ ] Decide …`, `operator confirms …`) until a comment
  starting `Operator decision:` records the answer.
- **Mechanical check.** Before dispatching a batch, run
  `node .fediim/tools/dispatch-check.mjs <n> <n> …`; dispatch in parallel only
  on exit 0. Exit 1 lists the issues that must run alone or serially.
  Files that changes in one area always touch (e.g. `openapi.yaml` for
  routers) go in `.fediim/guards.json` `implicitSharedFiles`
  (`{"when": "<path regex>", "implies": ["<file>"]}`), so the check counts them.
- **Isolation invariant.** No two agents ever share a branch or a worktree;
  each agent works only the files its issue declares.
- **Verification.** Workers run targeted suites for their touched files and
  hand back at PR creation; the orchestrator runs full verification at each PR
  head serially before merge, and pr-reviewer is dispatched only after it.
- **Serial gates.** Reviews and merges still pass their gates one at a time
  per the review-quorum and human-gates rules; parallelism applies to the
  plan/code phases, never to the merge gate.
- **Overlap discovered mid-flight** (an agent needs a file another in-flight
  issue declares) — stop the later-dispatched issue, label it `blocked` with
  a comment naming the conflict, and let the earlier one finish.
