# Generation Hook: 016-cdm-generic-instruments

- Hook script: `pipeline/generate-state-016-cdm-generic-instruments.sh`
- Render script: `pipeline/render-state-016-cdm-generic-instruments.sh`
- Feature pack: `specs/016-cdm-generic-instruments`

This state follows the patch-set overlay model.

## Patch-Set Inputs

- Parent state id: `009-order-management-matcher`
- Patch directory: `specs/016-cdm-generic-instruments/generation/patches/`
- Canonical patch file: `0001-state-overlay.patch`
- Frontend overrides: `specs/016-cdm-generic-instruments/generation/frontend-overrides/`

## Hook Responsibilities

1. Generate parent state output.
2. Apply all ordered patch files from this pack.
3. Render state-scoped runtime values and copy frontend overrides.
4. Regenerate architecture docs from `system/architecture.model.json`.
5. Keep compatibility with lineage contracts except where this pack declares otherwise — see
   `../contracts/contract-delta.md` for the `/stocks` -> `/instruments` replacement.
6. Produce deterministic output suitable for branch publishing.

The hook keeps the direct-invocation delegation guard used by state `009`: invoked directly, it
re-enters through `pipeline/generate-state.sh` so the post-generation installers (runtime harness, UI
state metadata, API explorer, CI assets, docs refresh) run.

## Render Responsibilities

The render script owns what the patch cannot: values that must be state-scoped rather than inherited
verbatim from the parent's runtime.

- Compose project name -> `traderx-state-016`.
- Observability runtime normalization for state `016`, which scopes the Grafana admin credential
  default to `traderx-state-016`.
- Grafana dashboard Loki filters -> `compose_project="traderx-state-016"`. Inherited dashboard copies
  point at the parent's project and would render empty under this state's runtime.
- The runtime directory README.
- Frontend overrides copied over the generated Angular tree.

## Runtime scripts

- `scripts/start-state-016-cdm-generic-instruments-generated.sh`
- `scripts/status-state-016-cdm-generic-instruments-generated.sh`
- `scripts/stop-state-016-cdm-generic-instruments-generated.sh`
- `scripts/test-state-016-cdm-generic-instruments.sh`

## Capture / Refresh Patch

```bash
bash pipeline/create-state-patchset.sh 016-cdm-generic-instruments 009-order-management-matcher
```

This regenerates both parent and child into the shared generated tree, so it overwrites uncaptured
work. Capture first, then refresh.

**First capture is different.** `create-state-patchset.sh` generates the child before diffing, which
means it applies the patch that does not exist yet and wipes the edited tree in the process. The
first patch was therefore captured by snapshotting the parent before editing, editing the tree, and
diffing the two snapshots with the same exclusions the script uses. Once a patch exists, the command
above is the supported path.

## Excluded from the patch

Some generated files are owned by depth-1 installers that run after the patch applies, so patching
them is pointless — they are rewritten immediately:

- `api-explorer/catalog.json` and `ingress/api-explorer/catalog.json`
- `web-front-end/angular/main/assets/state-ui.json`
- `api-explorer/contracts/**` and `ingress/api-explorer/contracts/**`

The first two also carry a generation timestamp, so including them would make the patch
non-deterministic. The API explorer contract for this state comes from
`../contracts/reference-data/openapi.yaml` instead: `pipeline/install-generated-api-explorer.sh`
prefers a contract published in the active state's pack over the state `001` baseline.
