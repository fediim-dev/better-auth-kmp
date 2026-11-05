---
description: claiming a Gradle build or publish ran "without credentials" in verification comments
phase: [verify, pr]
validated: 1
last-validated: 2026-11-05
evidence: "#1"
---
DON'T claim a Gradle run had no credentials after only unsetting env vars — `~/.gradle/gradle.properties` may define `gpr.*`; check it, or run with an isolated `GRADLE_USER_HOME`/on CI, before writing the claim (PR #9 had to retract it).
