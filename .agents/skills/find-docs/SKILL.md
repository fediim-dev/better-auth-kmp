---
name: find-docs
description: Use when a task depends on a library, framework, SDK, CLI tool or cloud service's current API, configuration or behaviour, including well-known ones.
---
# Skill: Find current docs (Context7)

Your training data may be out of date for any library or tool. Before relying on
an API signature, config option, CLI flag or migration step, look it up with the
Context7 CLI. It needs no MCP server.

## Procedure

1. **Check local / published manifests first.**
   If the dependency is internal or has a pre-compiled manifest (`ai-context.md`
   or inside its artifact/directory), read its L1 index and L2 signatures first
   per `manifest-navigation` — zero network cost and exact-version truth.
2. **Resolve the library ID (Context7).**
   `npx ctx7@0.8.2 library "<official name>" "<what you need to know>"`
   Use the official name with its punctuation ("Next.js", not "nextjs"). Pick
   the best match by exact name, description, snippet count, source reputation
   (High/Medium) and benchmark score. Skip this step if you already have an
   ID in `/org/project` form; use `/org/project/<version>` for a specific
   version.
2. **Fetch the docs.**
   `npx ctx7@0.8.2 docs <libraryId> "<one specific question>"`
   Ask one concept per call; split a question that covers several topics.
3. **Answer from what you fetched**, and say so when the docs don't cover it.

## Limits

- At most 3 ctx7 commands per question.
- ctx7 needs Node ≥ 22.13; on an older Node, say so instead of retrying.
- Never put API keys, passwords or other secrets in a query.
- On a quota error, tell the operator and suggest `npx ctx7@0.8.2 login` or
  setting `CONTEXT7_API_KEY`. Do not silently fall back to memory.

## Don't use it for

Refactoring, writing scripts from scratch, debugging business logic, code
review, or general programming concepts.
