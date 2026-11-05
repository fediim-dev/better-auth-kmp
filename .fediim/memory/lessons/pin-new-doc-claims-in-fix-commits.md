---
description: adding or rewording a KDoc/README behaviour claim during a review fix cycle (e.g. "a stalled connection does not delay the callback")
phase: [pr]
validated: 3
last-validated: 3026-11-05
evidence: "#4; #6 (PR #16: two epoch fences each survived removal alone because each masked the other; a mutation check per branch found it); #7 (PR #17: two review cycles spent on KDoc sentences added in fix commits — callback cancellation, close() revoke, storage failure — without pinning tests)"
---
DO land a test that pins any new behaviour claim in the same fix commit, and show it fails with the guarded line removed — PR #14 spent an extra review cycle on an unpinned executor claim; PR #16 found two overlapping fences that each survived removal alone, so remove each guard branch on its own.
