# Project Constraints & Architecture

> Authoritative and hand-maintained. fediim scaffolded this file once and
> will never overwrite it. Amending any constraint requires updating the
> related spec/RFC and this register in the same PR.

## Architecture overview

[One screen: components, data flow, and the seams agents must respect,
e.g. "A and B never call each other; they communicate only through the
database. Respect this boundary."]

## Constraints register

Violating a constraint is a defect, not a style choice.

### C1 · Language & Runtime
- **Constraint:** [e.g. TypeScript strict ESM, Node >= 20, for all components]
- **Rationale:** [e.g. single CI toolchain]

### C2 · Vendor & API Policy
- **Constraint:** [e.g. which model/cloud vendors are allowed and why]
- **Rationale:** []

### C3 · Storage & Data Policy
- **Constraint:** [e.g. SQLite only; no cloud-hosted databases]
- **Rationale:** []

### C4 · Reliability Policy
- **Constraint:** [e.g. idempotent writes over transactions; crash-safe by design]
- **Rationale:** []

### C5 · Provenance & Metadata
- **Constraint:** [e.g. metadata is stamped by the pipeline, never produced by a model]
- **Rationale:** []

### C6 · Input Handling & Security
- **Constraint:** [e.g. untrusted content is delimited/sanitized at the boundary]
- **Rationale:** []
