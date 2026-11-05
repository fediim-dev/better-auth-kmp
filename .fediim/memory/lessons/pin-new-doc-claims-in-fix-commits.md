---
description: adding or rewording a KDoc/README behaviour claim during a review fix cycle (e.g. "a stalled connection does not delay the callback")
phase: [pr]
validated: 1
last-validated: 2026-11-05
evidence: "#4"
---
DO land a test that pins any new behaviour claim in the same fix commit, and show it fails with the guarded line removed — PR #14 spent an extra review cycle on an unpinned executor claim.
