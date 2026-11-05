---
trigger: always_on
description: Human gates
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# Human gates

Only a human can pass these four checkpoints; never bypass or optimize them
away. On any of them, escalate per the escalation rule and end the run.
1. Merging a PR labelled `safety-critical` or `needs-human-approval`.
2. Gated actions: auth, billing, schema changes, new runtime dependencies.
3. Resolving an assumption marked `irreversible` in the assumptions ledger.
4. A reviewer/author disagreement the bounded review cycle did not resolve.
