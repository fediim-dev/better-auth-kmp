---
name: deliver
description: Use when delivering a GitHub issue end-to-end, or when the operator runs /deliver.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# /deliver — deliver one issue end-to-end

Run ONE issue through select → plan → plan-review → code → verify → PR →
pr-review to the merge gate, then close the run with a retrospective
(Phase 9). Never work two issues in one run. `AGENTS.md` rules still apply. If a
required tool, label, or reviewer is unavailable, escalate (comment on the
issue, apply `blocked`) and stop — never improvise around a gate. A run
that ends `blocked` still runs Phase 9 before stopping.

## Phase 1 — Select

1. Make sure the workflow labels exist (idempotent): `gh label create <name>
   --force` for `ready`, `in-progress`, `plan-pending`, `plan-approved`,
   `blocked` and `needs-human-approval`.
2. List open issues with `gh issue list`. You decide what issue to pick next;
   no `ready` label is required. Only issues labelled `blocked` (or with open
   `Depends on #NN` dependencies) cannot be picked up.
3. Where a build sequence or roadmap defines order, take the lowest-sequence
   eligible issue; otherwise choose the highest-impact open issue. Priority
   (`P0` > `P1` > `P2` > `P3`) breaks ties only.
4. Label it `in-progress`. This issue is the only work item for this run.

## Phase 2 — Plan

1. Plan ONLY from the approved task spec in the issue (spec-lock): no scope
   expansion, no invented requirements, no "improvements" to the spec.
   Run `node .fediim/tools/recall.mjs --phase plan "<issue terms>"`; give its
   DO/DON'T lines to the `planner`.
2. Draft breadth-first inline when this session already runs on `opus`/`pro` and
   the scope is ≤2 files; otherwise Claude Code invoke the `planner` subagent,
   Antigravity `invoke_subagent` (TypeName: 'planner', Role: 'Planner', Model: 'inherit', Workspace: 'inherit')
   with the issue spec and adopt the draft — you own it.
3. Include `Alternatives considered` (2–3 approaches with one-line
   rejection reasons, or why only one works). List interpretations for
   ambiguity — reversible picks go to `## Assumptions`, irreversible ones are a
   human gate.
4. Name exact modules/files (`S<n>`), interfaces (`I<n>`) and tests (`T<n>`);
   no TBD, every step groundable in the working tree. Respect `CONSTRAINTS.md`;
   call out any touch of a protected or `autonomy: gated` path (human gate).
5. Create the branch now (`feat/<task-id>-<slug>`, `fix/`, `chore/`), write the
   plan to `.fediim/plans/<n>.md` in planner format, commit `docs(plan): #<n>`,
   push, comment on the issue linking the file at that commit, and add
   `plan-pending`. Write NO code; commit only the plan file.

## Phase 3 — Plan review

1. Run `node .fediim/tools/dispatch-check.mjs --plan .fediim/plans/<n>.md`
   (exit 1 → narrow plan or wait on the PR). Invoke **plan-reviewer** — Claude Code
   `plan-reviewer` subagent; Antigravity `invoke_subagent`
   (TypeName: 'plan-reviewer', Role: 'Plan Reviewer', Model: 'inherit', Workspace: 'inherit')
   — and read the verdict. Never review your own plan.
2. Brief per `rule-review-quorum` (load it): issue spec, plan
   file + commit SHA, iteration N of max 3 (1 + earlier
   `VERDICT: block — plan review` comments after `BUDGET: reset`; from N≥2
   the plan diff since last reviewed SHA + prior OPEN ITEMS).
3. Record the verdict: comment on the issue with first line
   `VERDICT: pass — plan review` or `VERDICT: block — plan review`, second line
   `Plan: <sha>`, then the reviewer's OPEN ITEMS citing plan ids.
4. `VERDICT: pass` → replace `plan-pending` with `plan-approved` and continue.
   `VERDICT: block` → edit the plan in place from OPEN ITEMS, commit,
   push and re-review, max 3 cycles; no revision comment (next verdict
   names the new SHA). If it cannot converge or carries
   `blocked`, comment on the issue, apply `blocked`, and STOP.

## Phase 4 — Code

Run `node .fediim/tools/recall.mjs --phase code --plan .fediim/plans/<n>.md`;
give matched DO/DON'T lines to any implementer.

Who writes the code: load `rule-implementation-delegation` on Claude Code or
`rule-implementation-delegation-antigravity` on Antigravity, and follow it.

1. Continue on the Phase 2 branch and execute `.fediim/plans/<n>.md` (`I<n>`,
   `T<n>`, `S<n>` in order). One logical change in focused files (split any
   near the size limit); a diff outgrowing the plan splits the issue, not
   the PR. Record reversible gaps as new `L<n>` plan lines (assumptions-ledger
   rule).
2. Follow TDD where the task requires it: failing test first.
3. Never touch protected paths without the recorded approval marker; never
   add a runtime dependency — both `autonomy: gated`, need a human.
4. Compile only while coding (build command); tests run once, in Phase 5.

## Phase 5 — Verify

1. Verify once, locally (verification-gates rule; a docs-only diff skips
   it): `[commands]` build + test; note the HEAD SHA. Red → fix the root
   cause within the attempt budget (budgets rule), else escalate and end.
2. NEVER weaken, skip, or delete a test, guard, or hook to go green; that
   ends the run as a failure: comment on the issue, apply `blocked`.

## Phase 6 — Pull request

1. Run `[commands.format]` on `git diff --name-only origin/HEAD...` (commit
   changes as `style: format`), then `[commands].lint` if set.
2. Grep `docs/`, README, API specs, docstrings and E2E specs for each changed
   route, screen, old wording or public identifier; fix stale prose in the
   same commit. Then `gh pr create`: title starts with the task id, body is
   `Closes #<n>`, the acceptance-criteria checklist, and
   `Plan: .fediim/plans/<n>.md` — the checklist IS the body.
3. Comment `Local verification: <commands> → pass|skipped (docs-only) @ <sha>`
   on the PR; never wait on CI.

## Phase 7 — PR review

1. Pick this PR's reviewers from the eligibility list (load `rule-review-quorum`). The **pr-reviewer** is
   always required, dispatched only after the `Local verification:` comment;
   never review or approve your own PR.
   The **security-reviewer** is required only when that list marks the PR
   security-relevant; otherwise do not dispatch it. It may run earlier; a
   commit from its notes needs re-verifying before the pr-reviewer.
2. Dispatch reviewers:
   - On Claude Code: invoke `pr-reviewer` (and `security-reviewer` if required).
   - On Antigravity:
     - If security-reviewer is required: dispatch `pr-reviewer` (TypeName: 'pr-reviewer', Role: 'PR Reviewer', Model: 'inherit', Workspace: 'inherit') and `security-reviewer` (TypeName: 'security-reviewer', Role: 'Security Reviewer', Model: 'inherit', Workspace: 'inherit') via `invoke_subagent`.
     - If security-reviewer is skipped: dispatch `pr-reviewer` (TypeName: 'pr-reviewer', Role: 'PR Reviewer', Model: 'inherit', Workspace: 'inherit') via `invoke_subagent`.
3. Brief each reviewer per review-quorum: issue spec,
   `.fediim/plans/<n>.md`, PR number, the local verification comment and
   iteration N of max 5 (1 + earlier `VERDICT: block — PR review` comments
   after `BUDGET: reset`). Input: `gh pr diff <n>`; from N≥2 only the delta
   since the last reviewed SHA + prior OPEN ITEMS.
4. Record the cycle: comment on the PR with first line
   `VERDICT: pass — PR review` or
   `VERDICT: block — PR review`, then each reviewer's verdict and OPEN ITEMS.
5. Review verdicts:
   - All dispatched reviewers must return `VERDICT: pass` → proceed to the merge gate.
   - `VERDICT: block` from any reviewer → fix from OPEN ITEMS, run `[commands.format]` on every fix commit, compile only, push and re-review (cite `file:line` or a
     test for any behaviour sentence or snippet you add, even a reviewer's),
     at most 5 cycles; on the final cycle without a pass, or once the PR
   carries `blocked`, comment the attempt history on the PR, apply `blocked`,
   and STOP.

## Phase 8 — Merge gate (delivery ends here)

1. If non-doc commits landed after the verified SHA, or `git fetch origin
   main && git merge-base --is-ancestor origin/main HEAD` fails (merge
   `origin/main` in), run build + test once at the PR head and comment it.
   Red → fix and re-review.
2. You MAY merge with an allowed method (`gh pr merge <n> --merge|--squash|--rebase`,
   never `--admin`) only when ALL hold: the independent pr-review verdict is
   pass; local verification is green at the PR head and cited on the PR (a
   CI check blocks only if its steps ran and failed — billing, no runner or
   a startup failure is an outage: name it on the PR and go on local
   verification); the issue/PR is not labelled
   `safety-critical` or `needs-human-approval`; and no `autonomy: gated`
   path was touched. The merge is yours: brief the pr-reviewer read-only and
   never delegate the merge to it.
3. If any condition fails, comment on the PR that it is ready and request
   human merge.
4. Delivery is complete at this gate; continue to
   Phase 9.

## Phase 9 — Retrospect (then the run ends)

1. REQUIRED: run the `retro` skill for this run, whether it delivered or
   ended `blocked`. Follow the skill; do not improvise a shorter retro.
2. STOP — never select the next issue in the same run; start a new
   `/deliver` run.
