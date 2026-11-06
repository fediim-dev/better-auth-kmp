---
description: writing a "fails until X" / "works once X" claim in a plan, README or PR about CI, credentials, secrets or config wiring
phase: [plan]
validated: 1
last-validated: 2026-11-06
evidence: "#32"
---
DO trace a "fails until X" claim through the consuming file (e.g. which secret name ci.yml passes) so X lists every change needed, not just the first one: plan #32 cycle 1 blocked because "until a PAT secret is added" omitted the ci.yml edit, since ci.yml:57 hardcodes `secrets.GITHUB_TOKEN`.
