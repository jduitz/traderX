# Implementation Plan: 016-cdm-generic-instruments

## Scope

- Transition from `009-order-management-matcher` to `016-cdm-generic-instruments`.
- Track focus: `functional`.
- Replace TraderX's two-string stock concept with an instrument model shaped after the FINOS Common
  Domain Model, and prove it with stocks and ETFs.

## Where changes may live

This state generates from `009`, which generates from the shared `templates/` baseline. Templates are
the baseline every state generates from, so a rename there would regenerate `001`–`009` with
`/instruments` and break their inherited contracts. **`templates/**` is not edited by this state.**

Per `docs/spec-kit/generated-state-branches.md:158`, derived state deviations live in state
patchsets. This state has four legitimate places to put changes:

| Mechanism | Used for |
|---|---|
| `generation/patches/0001-state-overlay.patch` | Code deltas, captured from the edited generated tree |
| `generation/frontend-overrides/` | Wholesale Angular file copies, applied by the render script |
| `specs/016-*/**.md` and `contracts/` | The spec pack itself — hand-authored, never patched |
| `pipeline/*.sh`, `scripts/*.sh` | Repo-level generators, installers, and this state's lifecycle scripts |

Patches are for code. Spec markdown is written directly under `specs/016-*/`.

## Deliverables

1. Requirement deltas in `requirements/`.
2. Contract delta in `contracts/contract-delta.md`, plus this state's reference-data contract at
   `contracts/reference-data/openapi.yaml`.
3. Supporting artifacts: `research.md`, `data-model.md`, `quickstart.md`.
4. Architecture and topology deltas in `system/`, including `system/messaging-subject-map.md`
   (mandatory from state `006` onward; the scaffolder does not emit it).
5. Generation hook: `pipeline/generate-state-016-cdm-generic-instruments.sh`.
6. Render script: `pipeline/render-state-016-cdm-generic-instruments.sh`.
7. Lifecycle scripts: `scripts/{start,stop,status}-state-016-cdm-generic-instruments-generated.sh`.
8. Smoke test: `scripts/test-state-016-cdm-generic-instruments.sh`.

## Approach

1. **Fetch identifiers offline, once.** OpenFIGI, batched and paced. Bake into seed data; the runtime
   never calls it. See `research.md`.
2. **Edit the generated `009` tree**, never `templates/`. Full rename of `reference-data`'s
   `src/stocks/` to `src/instruments/`, seed file gains FIGI and security type columns plus ETF rows,
   `load-csv-data.ts` maps OpenFIGI onto CDM and enforces the sub-type conditions at load,
   `trade-service` validation repoints at `/instruments`.
3. **Capture the patch from a parent snapshot**, then wire the hook. `create-state-patchset.sh`
   regenerates the child, so it would wipe the working tree on first capture; the parent snapshot is
   taken before editing and the first patch is diffed against it. Once a patch exists, the documented
   refresh command works.
4. **Angular via frontend overrides**, following `008`/`009`/`014`, based on the generated post-`009`
   tree rather than `templates/web-front-end` — state `009` ships 19 override files of its own, and
   copying from the baseline would silently revert them.
5. **Regenerate from scratch and verify.** Edits and patch capture are not interleaved.

## Registration

A new state is not just a spec pack. These repo-level surfaces each need a `016` entry, and a missing
one fails a gate or produces a wrong runtime:

- `catalog/state-catalog.json` — `status` and `generation.mode` flip `planned` -> `implemented`.
- `pipeline/install-generated-runtime-harness.sh` — lifecycle scripts to copy into the tree.
- `pipeline/install-generated-ci-assets.sh` — snapshot root allowlist, and the compose file path.
- `pipeline/install-generated-ui-state-metadata.sh` — the reference-data status check probes
  `/instruments` from state `016` onward.
- `pipeline/install-generated-api-explorer.sh` — a state pack's own component contract wins over the
  `001` baseline, so this state's explorer documents `/instruments`.
- `pipeline/validate-generated-state-lineage-invariants.sh` — the allowlist policy matrix. An
  implemented state with no entry fails the gate.
- `pipeline/validate-lifecycle-script-contract.sh` — the state number list (`015` is a docs pack, not
  a state, and is skipped).
- `pipeline/publish-generated-state-branch.sh` — snapshot allowlist and run-bundle content.

## Exit Criteria

- Spec and tasks are complete and reviewed.
- The generation hook produces the expected artifacts from a clean tree.
- Quality gates pass; see `quickstart.md`.
- Smoke tests pass against a running runtime — not a typecheck.
- The state can be published to `code/generated-state-016-cdm-generic-instruments`.
