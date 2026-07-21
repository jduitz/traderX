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

State 016 chains its parent's smoke script, following the existing convention
(`test-state-014` calls `test-state-012`, which calls `test-state-011`): the
smoke invokes `scripts/test-state-009-order-management-matcher.sh` with

- `TRADERX_COMPOSE_FILE` pointing at 016's compose file (009's script defaults
  to its own `order-management-matcher/` path; the env override was added for
  this),
- `COMPOSE_PROJECT_NAME=traderx-state-016`, and
- `TRADERX_GRAFANA_ADMIN_PASSWORD=traderx-state-016` (state-scoped default),

plus 016's ingress URL and a pass-through of `--skip-messaging`. When 009's
checks change, 016 picks the changes up automatically instead of asserting a
stale snapshot.

Historical note: 016 originally could not chain 009's script. The state 004
patchset carried `deleted file mode` hunks for every script under `scripts/`,
which pinned their exact bytes — editing any of them broke generation for 004
and every state below it, so even the one-line env-override in `test-state-009`
was off the table, and 016 restated 009's compose-bound checks instead. Those
dead hunks were removed (fork PR #1, merge `c3a7a04`), verified
behavior-neutral, and the chaining above replaced the restated checks. One
remnant: the state 010 patch still pins
`scripts/status-state-009-order-management-matcher-generated.sh` and
`scripts/stop-state-009-order-management-matcher-generated.sh` — do not edit
those two files until 010's patch gets the same cleanup.

On top of the chained parent run, this smoke keeps one 016-specific lifecycle
check: the SPY order create/cancel and force-fill-to-position path, because an
ETF exercising trade-service's `/instruments/{ticker}` validation end to end is
this state's own contribution.

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
