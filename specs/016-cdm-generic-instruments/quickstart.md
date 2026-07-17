# Quickstart: CDM Generic Instruments

## 1) Generate This State

```bash
bash pipeline/generate-state.sh 016-cdm-generic-instruments
```

Generation is sequential — it takes a lock at `generated/.locks/generate-state.lock`, so do not run
two states at once. `generated/` is scratch: regeneration wipes it.

## 2) Start Runtime

```bash
./scripts/start-state-016-cdm-generic-instruments-generated.sh
```

- UI: `http://localhost:8080`
- Instruments: `http://localhost:18085/instruments`
- API explorer: `http://localhost:8080/api/docs`
- Grafana: `http://localhost:8080/grafana/` (anonymous Viewer) or `http://localhost:3001` (admin)

The compose project is `traderx-state-016` and the runtime directory is `cdm-generic-instruments/`,
so this state does not collide with a running state `009`.

## 3) Look at the model

```bash
# An equity: CDM Equity / Ordinary, with BBGTICKER + FIGI
curl -s localhost:18085/instruments/IBM | jq

# An ETF: CDM Fund / ExchangeTradedFund, no equityType
curl -s localhost:18085/instruments/SPY | jq

# The replacement is real, not an alias
curl -s -o /dev/null -w '%{http_code}\n' localhost:18085/stocks   # 404
```

## 4) Run Smoke Tests

```bash
./scripts/test-state-016-cdm-generic-instruments.sh
./scripts/test-state-016-cdm-generic-instruments.sh --skip-messaging   # faster
```

See `tests/smoke/README.md` for what is asserted, and for why this state restates some of state
`009`'s checks instead of chaining its smoke script.

## 5) Stop Runtime

```bash
./scripts/stop-state-016-cdm-generic-instruments-generated.sh
```

## Quality Gates

```bash
tools/validate-frontmatter.sh
bash pipeline/speckit/validate-root-spec-kit-gates.sh
bash pipeline/speckit/validate-speckit-readiness.sh
bash pipeline/verify-spec-coverage.sh
bash pipeline/validate-state-pack-artifacts.sh
bash pipeline/refresh-state-docs.sh --check
bash pipeline/prepublish-generated-state-gate.sh 016-cdm-generic-instruments
```

## Refresh the patchset

After changing code in the generated tree:

```bash
bash pipeline/create-state-patchset.sh 016-cdm-generic-instruments 009-order-management-matcher
```

This regenerates both parent and child, so it overwrites the generated tree. Do not run it with
uncaptured work in `generated/` — capture first, then refresh.

## Publish

```bash
bash pipeline/publish-generated-state-branch.sh 016-cdm-generic-instruments --push
```

Target branch: `code/generated-state-016-cdm-generic-instruments`. This enforces the
one-snapshot-commit-per-branch invariant (reset to base, then force-push). Never publish by hand.
