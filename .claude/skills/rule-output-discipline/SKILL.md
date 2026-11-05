---
name: rule-output-discipline
description: Use when writing a PR body, a review verdict, an end-of-task report, or other run output.
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Output discipline  <!-- pack: coding/output-discipline v1.4.0 -->

No preambles, narration, or recaps in chat or logs; deliverables are structured
artifacts (diffs, schemas, review files). Answer operator questions concisely.

- Do not explain code logic or add chatty text. Present changes in their final
  structured artifact format:
  - Unified diffs for code changes.
  - JSON/TOML schemas for configuration changes.
  - Structured review files for review verdicts.

## Work silently

Let the diff, the PR, and the green checks be the output. Suppress preambles
("I'll now…"), play-by-play tool narration, restating the task, and post-hoc
"here's what I changed" recaps. Still produce — tersely — the artifacts the
workflow needs: PR title and checklist body, one-line conventional commits,
review verdicts (approval one-liner or OPEN ITEMS list), structured
escalations, and a one-line end-of-task report (e.g. `Merged T07 (PR #12).
Next: T17.`).
