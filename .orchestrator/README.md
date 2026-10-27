# .orchestrator

Persistent execution state for the Yomira multi-agent orchestration, laid out per
**Industrial Engineering Orchestrator V11.0, Section 7**. Initialized by `TASK-01-INV`
(WBS 1.1.1) and completed by `TASK_001` (WBS 1.0, PHASE-1) on 2026-10-27.

## Layout

| Path | Purpose |
| --- | --- |
| `model-registry/providers.json` | 7 providers, 89 eligible models, variants and capabilities |
| `model-registry/endpoints.json` | 90 inventory model endpoints + 7 provider endpoints, transport/auth and per-endpoint policy status |
| `model-registry/base-models.json` | 60 canonical model families behind the 89 eligible provider/model pairs |
| `model-registry/capabilities.json` | Per-model modalities, tool-call/reasoning flags and limits |
| `model-registry/benchmarks.json` | Benchmark ledger; suites defined, no scores measured yet |
| `model-registry/pricing.json` | Provider pricing reference; rates unknown and stored as `null` |
| `model-registry/sources.json` | Provenance index (`SRC-001`..`SRC-008`) |
| `model-registry/exclusion-policy.json` | User-mandated model exclusion; PDF source remains a 90-model historical inventory |
| `model-registry/subagent-selection-policy.json` | Pre-dispatch deny rules and eligible candidate list |
| `project-management/project-charter.json` | Objectives, scope, deliverables, constraints, governance |
| `project-management/wbs.json` | Phase / work-package / task tree (6 phases, 18 work packages, 27 tasks) |
| `project-management/task-dependencies.json` | Task graph (`nodes` + `edges`) |
| `project-management/schedule.json` | Milestones M0-M8 and earliest-start schedule entries |
| `project-management/critical-path.json` | Longest chain and near-critical branches |
| `project-management/resource-assignments.json` | Per-task agent/model assignment and policy check |
| `project-management/risk-register.json` | R-001..R-010 with controls and residual scores |
| `project-management/inventory-device-report.json` | Restart discovery and physical-device installation results |
| `execution/agent-assignments.json` | 7 agent roles, primary models, backups and write scope |
| `execution/active-tasks.json` | Currently running tasks |
| `execution/completed-tasks.json` | Completed task ledger with results |
| `execution/blockers.json` | Open blockers (BLK-001 device install) and watch items |
| `performance/credits.json` | Provider accounts, phase allocations and spend ledger |
| `performance/context-usage.json` | Context limits per agent and per-task token ledger |
| `performance/actual-performance.json` | Estimate vs measured duration, tokens and cost |
| `performance/metrics.json` | Raw task-level timing/token record schema |
| `handoffs/task-context/` | Task-context handoffs; `_template.json` plus one file per dispatched task |
| `handoffs/HO-*.json` | Handoff records between orchestrated tasks |

## State model

- **Project**: Yomira, `5.4.1-beta` -> `6.0.0-Beta`.
- **Roadmap**: 10x Smart Matcher (PHASE-2), Vault cleanup/classifier (PHASE-3),
  Watchable adaptations - Anime/Hentai/Kdrama (PHASE-4), Vault 2.0 dual realm
  (PHASE-5), signed build and 10-minute mobile test (PHASE-6).
- **Completed**: `TASK_001` and `TASK-01-INV`. **Ready roots**: `SM-001`, `VC-001`, `WA-001`.
- **Open blocker**: `BLK-001` blocks only `MT-002`/`MT-003` until the device owner approves installation.

## Model policy

`google/antigravity-gemini-3.8-flash` is excluded from **all** dispatch roles:
orchestrator, worker and backup. Enforcement is a case-insensitive
`gemini[-_. ]?3[._-]?8[-_. ]?flash` deny check before every provider call, plus an
allowlist check against `providers.json`. The orchestrator runs on
`evren/deepseek-v4.1-flash`. A policy violation is a release blocker (`R-001`).

## Conventions

- All files are UTF-8 JSON with a `schemaVersion` field; timestamps are ISO-8601 UTC
  (`Z` preferred; older records may use `+00:00`).
- Model identifiers use `<providerId>/<slug>`, e.g. `evren3/deepseek-v4.1-flash`.
- `providers.json` is the source of truth for model availability; `base-models.json` is derived.
- `null` means **not measured**, never zero. Pricing, benchmark scores, token counts and
  costs stay `null` until a dated source or runtime report supplies them.
- Registry data comes from the inventory PDF. Limits/tool-call/reasoning for the
  `evren`, `evren2` and `evren3` providers are enriched from the OpenCode provider
  configuration; other providers carry `null` where the inventory does not specify a value.
- A task is dispatched only with a handoff in `handoffs/task-context/` naming inputs,
  outputs, dependencies and model policy. `_template.json` is the starting point.
- Update `execution/`, `performance/` and the handoff after every task, before every phase gate.
- `.orchestrator/` is git-ignored; the repository `.gitignore` is unchanged.

## Provenance

Registry facts trace to `sources.json`:

- `SRC-001` - desktop inventory PDF, 2026-10-20, SHA-256 `5b19ede4...d8b`
- `SRC-002` - owner model-isolation directive
- `SRC-003` - OpenCode provider configuration
- `SRC-004` - Orchestrator V11.0 manual, Section 7
- `SRC-005` - repository baseline docs
- `SRC-006` - TASK_001 dispatch brief
- `SRC-007` - device discovery report
- `SRC-008` - derived inventory snapshot
