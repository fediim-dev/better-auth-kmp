---
trigger: always_on
description: No hardcoded secrets
---
<!-- fediim:generated v1 — do not edit; customise via .fediim/overrides/ -->
# No hardcoded secrets

Never inline API keys, tokens, passwords, private keys, or connection strings
in source, config, tests, fixtures, or docs — read them from env vars or a
credentials manager. Never echo secret values into logs, errors, test
snapshots, or doc manifests. The `block-secrets` hook catches known patterns
only; the rule binds for everything else.
