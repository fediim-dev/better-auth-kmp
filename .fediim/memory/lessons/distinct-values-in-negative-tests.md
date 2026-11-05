---
description: writing a negative test (forged origin, wrong nonce, bad state, untrusted input) next to a positive control in the same test
phase: [plan, code]
validated: 1
last-validated: 2026-11-05
evidence: "#4"
---
DO give each rejected input its own distinct payload (e.g. token "evil.t") and assert the final state equals the valid payload — reusing the valid value makes "accepted" indistinguishable from "ignored" (PR #14 T9/T10).
