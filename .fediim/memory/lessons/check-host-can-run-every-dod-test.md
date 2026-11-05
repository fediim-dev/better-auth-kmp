---
description: planning a KMP/multi-target build whose DoD requires tests on a target the local host may not run (iosSimulatorArm64 on Intel Mac) and deferring that evidence to CI
phase: [plan]
validated: 1
last-validated: 2026-11-05
evidence: "#1"
---
DO confirm in the plan phase that the local host (`uname -m`) or a working CI runner can run every test the DoD requires, and escalate before coding if neither can — #1 deferred iOS simulator tests to CI on an x86_64 host, CI was in a billing outage, and the run blocked only after full implementation and two review cycles.
