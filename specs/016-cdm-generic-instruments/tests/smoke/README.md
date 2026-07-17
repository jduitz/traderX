# Smoke Tests: 016-cdm-generic-instruments

- Primary smoke script: `scripts/test-state-016-cdm-generic-instruments.sh`
- Runtime under test: `generated/code/target-generated/cdm-generic-instruments/docker-compose.yml`
- Compose project: `traderx-state-016`

Run it after the state is up:

```bash
./scripts/start-state-016-cdm-generic-instruments-generated.sh
./scripts/test-state-016-cdm-generic-instruments.sh
./scripts/test-state-016-cdm-generic-instruments.sh --skip-messaging   # faster
```

## State-specific checks

These are what this state adds. They are asserted against what compose actually
serves rather than against the seed file, because the unmapped-ticker policy
permits `BBGTICKER`-only rows in the seed data:

- `/instruments` is served, and every served instrument carries both a
  `BBGTICKER` and a `FIGI` identifier.
- `ticker` equals the `BBGTICKER` identifier value on every served instrument.
- Every served instrument satisfies CDM's `EquitySubType` / `FundSubType`
  conditions, and is `Equity` or `Fund` (the only two types 016 emits).
- Every served instrument is USD-denominated.
- `SPY` is `Fund` with `fundType: ExchangeTradedFund` and no `equityType`.
- `IBM` is `Equity` with `equityType.equityType: Ordinary` and no `fundType`.
- Display names come from the seed file, not from OpenFIGI (`AAPL` is `Apple`,
  not `APPLE INC`). This guards the decision to keep `companyName`.
- `UBS`, `DB`, `FNMA` and `FNF` carry both identifiers. These four are not in
  the seed CSV at all, so a FIGI column alone would never reach them.
- `/stocks` returns 404 directly and through ingress. The replacement is real,
  not an alias.
- The five ETFs are aligned across reference-data, the compose ticker
  configuration, and `price-publisher/data/snapshot-prices.json`.
- 016's start script probes `/instruments`; 009's still probes `/stocks`.

## Inherited state 009 behavior

State 014 chains its parent's smoke script (`test-state-014` calls
`test-state-012`, which calls `test-state-011`). **State 016 does not**, and the
reason is worth recording.

`scripts/test-state-009-order-management-matcher.sh` resolves its compose file
from a fixed `order-management-matcher/` path. 016's runtime directory is
`cdm-generic-instruments/`, so the parent's script cannot find 016's compose
project. Everything else it needs is already environment-overridable
(`COMPOSE_PROJECT_NAME`, `TRADERX_GRAFANA_ADMIN_PASSWORD`), and teaching it to
read the compose path from the environment too is a one-line change.

That one-line change is currently blocked.
`specs/004-containerized-compose-runtime/generation/patches/0001-state-overlay.patch`
carries `deleted file mode` hunks for every script under `scripts/`, and a git
deletion hunk embeds the deleted file's entire text. Editing any of those
scripts therefore makes the 004 patch fail to apply, breaking generation for 004
and every state below it. The deletions are themselves no-ops — the runtime
harness copies those scripts straight back at the end of every generation — but
they pin the bytes regardless. Generalizing 009's test harness is a follow-on
task, and it should remove those dead hunks first.

Until then, 016 splits the inherited coverage:

- **Chained live**, so improvements to them are picked up automatically. The
  three helpers 009's smoke calls are all fully parameterized and take no
  compose path: `test-api-explorer-pubsub-inspector.sh`,
  `test-web-angular-baseline-ux-contract.sh`, and
  `test-messaging-009-order-management-matcher.sh`.
- **Restated**, because they are compose-bound: running service count, order
  matcher health and lifecycle metrics, and the order create/cancel/force-fill
  path through to a trade and a position.

### Local environment note

On some local Docker setups the ingress websocket path `ws://localhost:8080/nats-ws`
fails with NATS websocket framing errors while the direct NATS websocket
(`ws://localhost:8081`) works. This is a pre-existing issue observed identically
on stock 008 and 009 runtimes — 016 changes no messaging or ingress websocket
configuration (its `nats.conf` is byte-identical to 009's). When it occurs, the
final messaging step of this smoke fails with `MESSAGE_BUS_UNREACHABLE`; run
with `--skip-messaging`, which is the intended escape hatch. The same local
workaround used for 008/009 (pointing `tradeFeedUrl` in the frontend
`environment*.ts` files at `:8081`) applies to 016's generated tree, but it is
a local diagnostic aid and must not be captured into the state patchset.

The restated checks are a snapshot and will drift from 009. They are scoped to
what this state can plausibly break: every order and trade runs its security
through the trade-service ticker validation that 016 repointed at
`/instruments`, so the ETF order-to-position path is the assertion carrying the
most weight here. 009's Grafana and Prometheus dashboard assertions are not
restated — 016 does not touch observability, and copying them would add drift
risk for no coverage.
