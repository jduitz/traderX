## TraderX State 016 Runtime

This directory defines the compose runtime for:

- `016-cdm-generic-instruments`

It extends the order-management runtime with:

- An instrument model shaped after the FINOS Common Domain Model (CDM)
- `reference-data` serving `/instruments` and `/instruments/{ticker}`; `/stocks` is removed, not aliased
- CDM asset identifiers (`BBGTICKER`, `FIGI`) baked into seed data offline
- Exchange-traded funds (SPY, QQQ, IWM, VTI, GLD) alongside equities, as a second CDM `securityType`

Primary endpoints:

- TraderX UI: `http://localhost:8080`
- Instruments: `http://localhost:18085/instruments`
- Grafana dashboards (via ingress): `http://localhost:8080/grafana/`
- Grafana (direct): `http://localhost:3001`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`
- Tempo: `http://localhost:3200`
- NATS monitor: `http://localhost:8222/varz`
- Price publisher: `http://localhost:18100/health`
- Order matcher: `http://localhost:18110/health`

Grafana access:

- Dashboards are anonymous Viewer surfaces through ingress for demos.
- Local admin access is available at the direct Grafana URL.
- The generated start script prints the active admin credential on startup.
- Defaults are state-scoped: `TRADERX_GRAFANA_ADMIN_USER` falls back to `traderx-admin`; `TRADERX_GRAFANA_ADMIN_PASSWORD` falls back to `traderx-state-016`.
- Override those values before startup for any shared or long-lived environment.
