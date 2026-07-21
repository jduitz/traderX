# System Design

State: `016-cdm-generic-instruments`

## Design Intent

Replaces TraderX's two-string stock concept with an instrument model shaped after the FINOS Common Domain Model. Reference data serves /instruments with CDM asset identifiers and security types, and ETFs join equities in the seed universe. The runtime topology is inherited from state 009 unchanged.

## Runtime Topology / Flow (Spec Extract)

# Runtime Topology: 016-cdm-generic-instruments

Parent state: `009-order-management-matcher`

The topology is inherited from state `009` unchanged: same 19+ compose services, same ports, same
messaging subjects, same observability stack. **No component is added, removed, or rewired.** This
state changes what one existing service serves, not the shape of the runtime.

## Entrypoints

- Runtime directory: `generated/code/target-generated/cdm-generic-instruments/`
- Compose project: `traderx-state-016`
- Start: `./scripts/start-state-016-cdm-generic-instruments-generated.sh`
- Status: `./scripts/status-state-016-cdm-generic-instruments-generated.sh`
- Stop: `./scripts/stop-state-016-cdm-generic-instruments-generated.sh`
- Smoke: `./scripts/test-state-016-cdm-generic-instruments.sh`

The runtime directory and compose project are state-scoped, following the `008` -> `009` convention
(`pricing-awareness-market-data/` -> `order-management-matcher/`). State `009`'s directory and
project remain in the tree during development and are pruned from the published snapshot by the
lineage allowlist, so the two states can run side by side on one host.

## Components

| Component | Change |
|---|---|
| `reference-data` | Serves `/instruments` and `/instruments/{ticker}`. `/stocks` removed. Seed file renamed and extended with identifier columns and ETF rows. |
| `trade-service` | Ticker validation resolves `/instruments/{ticker}`. Status-code check only, so the URL is the whole change. |
| `price-publisher` | Five ETF tickers added to the supported universe and the price snapshot. |
| `web-front-end` | `SymbolService` fetches `/instruments`; the default status check probes `:18085/instruments`. |
| `order-matcher` | Unchanged. Now exercised with an ETF as well as an equity. |
| `trade-processor`, `position-service`, `account-service`, `people-service`, `database` | Unchanged. |
| `nats-broker`, `ingress`, observability stack | Unchanged. |

## Networking

Unchanged from `009`. Ports, ingress routes, and the NATS topology are inherited as-is. `reference-data`
still listens on `18085` behind the `/reference-data/` ingress route; only the resource path beneath
it moved.

## Startup / Health Order

Inherited from `009`, with one substitution: the reference-data readiness probe and the Prometheus
blackbox probe target `/instruments` rather than `/stocks`.

1. `database` (postgres readiness)
2. `reference-data` — `http://localhost:18085/instruments`
3. `nats-broker` — `http://localhost:8222/varz`
4. `price-publisher` — `http://localhost:18100/health`
5. `order-matcher` — `http://localhost:18110/health`
6. `ingress` — `http://localhost:8080/health`
7. Observability control plane — Grafana, Prometheus, Loki, Tempo, OTel collector

State `009`'s scripts are untouched and still probe `/stocks`, because `009`'s runtime still serves
it. A probe left pointing at the removed resource fails at container-health time rather than build
time, which presents as a timeout rather than a clean error — that is the failure this substitution
exists to avoid.

## State-scoped runtime values

| Value | State 009 | State 016 |
|---|---|---|
| Compose project | `traderx-state-009` | `traderx-state-016` |
| Runtime directory | `order-management-matcher/` | `cdm-generic-instruments/` |
| Grafana admin password default | `traderx-state-009` | `traderx-state-016` |
| Dashboard log filter | `compose_project="traderx-state-009"` | `compose_project="traderx-state-016"` |

Grafana dashboards filter Loki by compose project, so inherited dashboard copies are rewritten by
this state's render script. Left alone, they would render empty against this state's runtime.
