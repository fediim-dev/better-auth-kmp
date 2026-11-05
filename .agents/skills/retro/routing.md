# Retro routing

Route each finding by the first row that fits. Rows are ordered by cost per
future session: a check costs nothing to obey, a lesson must be recalled.

| Finding | Route |
|---|---|
| Mechanical mistake a check could catch — a fixed pattern, banned API, import shape, file location, or a command that should have failed | Proposal issue (step 7) for a lint rule, hook, guard, or CI check. Read the repo's existing check commands first: a check that exists but is unwired or broken is the finding. No lesson. |
| Repeated steps, or bulky output a script could cut | Lesson (step 4) whose description starts `friction:`, naming the `.fediim/tools/` script to forge. |
| Slow lookup — several searches or reads to find one fact | Proposal issue for a one-line pointer in the doc searched first. Never a new always-loaded rule. |
| Missing information — a needed fact was unreachable (logs, service state, docs) | Proposal issue naming the access or log to provide. |
| Reusable multi-step procedure | `learned-<slug>` skill (step 6). |
| Judgement call no check could catch | Lesson file (step 4). |
| Any other change to a pack, `AGENTS.md`, `CONSTRAINTS.md`, or a generated file | Proposal issue (step 7). |

## Prune

Also name at most one thing to remove, if the run showed it: a rule, lesson,
or skill that was loaded and changed nothing, or that a check now enforces.

- Lesson → archive it (step 5).
- Rule, pack text, or generated file → proposal issue (step 7) quoting the
  text to delete.
