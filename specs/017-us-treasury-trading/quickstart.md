# State 017 Quickstart

Run from the repository root on `spec/017-us-treasury-trading`.

## Generate

```bash
bash pipeline/generate-state.sh 017-us-treasury-trading
```

## Static and component verification

```bash
cd generated/code/target-generated/reference-data && npm ci && npm test
cd ../price-publisher && npm ci && npm test
cd ../trade-service && ./gradlew test
cd ../trade-processor && ./gradlew test
cd ../order-matcher && ./gradlew test
cd ../web-front-end/angular && npm ci && npm run buildlocal && npm run test:ci
cd ../../../../..

tools/validate-frontmatter.sh
bash pipeline/speckit/validate-root-spec-kit-gates.sh
bash pipeline/speckit/validate-speckit-readiness.sh
bash pipeline/verify-spec-coverage.sh
```

If Puppeteer's cached browser is incomplete, remove only the versioned broken
cache directory reported by npm and rerun `npm ci`. The implementation run used
the already-downloaded headless-shell binary for Karma by setting `CHROME_BIN`;
the committed Karma configuration respects that explicit override.

## Start

Preferred wrapper:

```bash
./scripts/start-state-017-us-treasury-trading-generated.sh
```

Direct Compose equivalent (also the fallback for an inherited snapshot-wrapper
path issue):

```bash
docker compose \
  -f generated/code/target-generated/us-treasury-trading/docker-compose.yml \
  --project-name traderx-state-017 up -d --build
```

Open:

- UI: `http://localhost:8080`
- API explorer: `http://localhost:8080/api/docs`
- Instruments: `http://localhost:18085/instruments`
- Treasury quote: `http://localhost:18100/prices/UST-20360515`
- Matcher health: `http://localhost:18110/health`
- Grafana: `http://localhost:8080/grafana/`

## Test and status

```bash
./scripts/status-state-017-us-treasury-trading-generated.sh
./scripts/test-state-017-us-treasury-trading.sh --skip-messaging
./scripts/test-state-017-us-treasury-trading.sh --skip-messaging
```

The two runs are an acceptance pair: do not reset the PostgreSQL volume between
them. The smoke reuses State 009's required lingering IBM order, validates the
five fixed seed trades without assuming immutable aggregate totals, and pauses
then unpauses `trade-processor` to verify HTTP 502 pending reconciliation and
exactly-once recovery. Its exit trap tolerates an already-unpaused processor.
Use `--skip-messaging` only to isolate the
inherited ingress NATS-WebSocket issue; the direct `ws://localhost:8081`
diagnostic is not a generated State 017 setting.

Do not run `docker compose down -v` as routine remediation. An unexpected
database condition requires a separate, explicitly approved volume reset.

## Stop

```bash
./scripts/stop-state-017-us-treasury-trading-generated.sh
```

Direct equivalent:

```bash
docker compose \
  -f generated/code/target-generated/us-treasury-trading/docker-compose.yml \
  --project-name traderx-state-017 down
```

## Verification evidence

Verified on 2026-07-30/31 from `spec/017-us-treasury-trading`:

- `bash pipeline/generate-state.sh 017-us-treasury-trading` completed the
  sequential State 001 -> 017 chain and synchronized all generated lockfiles.
- Reference-data and price-publisher suites passed (3 tests each).
- Trade-service, trade-processor, and order-matcher Gradle suites passed,
  including State 017 concurrency, idempotency, long-only, reservation,
  maturity, timeout, and reconciliation cases.
- Angular passed 32 active tests (4 inherited skips) and both local and
  production builds.
- Front-matter, root SpecKit, readiness, spec-coverage, generated contracts,
  compile preflight, UI metadata, and lineage-policy checks passed.
  Prepublication security/license scans and its duplicate Docker-build phase
  were intentionally skipped; the runtime image build completed separately.
- The stack built and started from a fresh `traderx-state-017` PostgreSQL
  volume; status returned HTTP 200 for every listed runtime endpoint.
- `./scripts/test-state-017-us-treasury-trading.sh` passed the complete State
  009 -> 016 -> 017 functional, database, observability, and messaging chain.
  The inherited ingress WebSocket framing issue appeared at
  `ws://localhost:8080/nats-ws`, so the documented direct
  `ws://localhost:8081` diagnostic was applied only to ignored generated smoke
  copies. It was not captured in State 017 sources.
- `./scripts/stop-state-017-us-treasury-trading-generated.sh` stopped the
  verified runtime.

Focused reliability remediation was verified on 2026-08-03:

- State 017 was regenerated into isolated parent, candidate, and clean output
  roots. The overlay was mechanically recaptured from the exact tested
  snapshots, and the clean regeneration matched the tested candidate.
- Affected Node, Gradle, and Angular suites passed, followed by the SpecKit,
  readiness, coverage, generated-contract, compile-preflight, UI metadata, and
  lineage gates.
- The generated runtime was rebuilt and started without resetting PostgreSQL.
  The ignored local NATS browser workaround was applied after generation.
- `./scripts/test-state-017-us-treasury-trading.sh --skip-messaging` passed
  twice consecutively on the same volume. Both runs passed the full inherited
  State 009 -> 016 -> 017 chain, including the expected HTTP 502 during the
  controlled processor pause and exact-once reconciliation after unpause.

Manual UI acceptance remains a separate handoff step.
