# Task context handoffs

Each dispatched task receives one JSON context package in this directory before it starts. The package is the task's contract: scope, inputs, dependencies, outputs, acceptance criteria, model policy and open blockers.

## Naming

- `TASK_001.json` - bootstrap task context.
- `<TASK-ID>.json` - one file per dispatched task.
- `<TASK-ID>-<attempt>.json` - a retry or re-dispatch; never overwrite the original.

## Minimum fields

| Field | Meaning |
| --- | --- |
| `taskId`, `wbs`, `phase` | Identity from `project-management/wbs.json` |
| `objective` | What must be true when the task ends |
| `inputs` | Files, sources and prior handoffs the task may rely on |
| `outputs` | Paths the task is expected to create or update |
| `acceptanceCriteria` | Evidence required to mark the task complete |
| `dependencies` | Upstream task IDs that must already be complete |
| `modelPolicy` | Eligible model plus the excluded-model guard |
| `blockers` | Open blockers that constrain the task |

## Rules

- Context is read-only for the receiving agent except for the task's declared outputs.
- An agent that needs to change scope returns a handoff request instead of editing the WBS directly.
- Model policy is re-checked at dispatch time, not trusted from an older package.
- Completed context packages are retained for audit; they are not deleted.
