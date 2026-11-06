# TeachTrack AI_SPEC Index

## Current product versions

- `1.x MVP`: Local-first teacher workspace; prompts `docs/prompts/00-09`.
- `2.0.0-alpha.1`: TeachTrack Copilot planning and implementation specification; prompts `docs/prompts/10-16`.
- `3.0.0-alpha.3`: Portable Workspace + OpenMinis Agent integration (current shipped line).
- `4.0.0` (planned): Cloud question bank, exam analysis, AI precision practice, cross-device sync, MCP.

## Version 4.0 documents (M01 audit baseline)

- `docs/ai-practice/CURRENT_ARCHITECTURE.md`: as-is architecture audit with file-level evidence.
- `docs/ai-practice/GAP_ANALYSIS.md`: requirement-by-requirement gap matrix, conflicts and risks.
- `docs/ai-practice/TEACHTRACK_AI_PRACTICE_ARCHITECTURE.md`: target architecture and ADRs.
- `docs/ai-practice/TEACHTRACK_DATABASE_SCHEMA.md`: PostgreSQL schema and Room→PG mapping.
- `docs/ai-practice/TEACHTRACK_MIGRATION_PLAN.md`: migration, idempotency, rollback, reconciliation.
- `docs/ai-practice/MIGRATION_PLAN.md`: M01 entry pointing to the canonical migration plan.
- `docs/ai-practice/TEACHTRACK_IMPLEMENTATION_ROADMAP.md`: M01–M07 delivery plan and gates.

## Version 2.0 documents

- `30_V2_AI_SPEC.md`: source classification, scope, goals, non-goals, assumptions, and acceptance boundary.
- `31_V2_REQUIREMENTS.md`: structured requirements and requirement IDs.
- `32_V2_ARCHITECTURE.md`: provider abstraction, intent compiler, action registry, confirmation, audit, and offline boundary.
- `33_V2_TASK_TREE.md`: dependency-ordered Epic/Feature/Task/Verification tree.
- `34_V2_SECURITY_AND_PROVIDER.md`: privacy, provider configuration, secrets, network, consent, and rollback rules.
- `35_V2_VERIFICATION_MATRIX.yaml`: v3.1 verification matrix.
- `36_V2_INVARIANTS.yaml`: v3.1 invariant registry.

## State records

- `24_PROJECT_STATE.md`
- `25_PROGRESS.md`
- `26_DECISION_LOG.md`
- `27_KNOWN_ISSUES.md`
- `28_CHANGELOG.md`
- `29_FINAL_AUDIT.md`
