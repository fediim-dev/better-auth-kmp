---
trigger: always_on
description: TDD gate
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# TDD gate

With `tdd: true` in the task brief, write the test file first: no
implementation until tests exist and fail for the right reason; scenarios come
only from the brief's `## Test specification`. Completion is blocked while
tests fail (Stop hook on both runtimes) — never bypass it.
