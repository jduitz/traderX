# Run From Clone

Prerequisites:
- Docker Desktop (or Docker Engine + Compose plugin)

Start:

```bash
./scripts/start-state-016-cdm-generic-instruments-generated.sh
./scripts/start-state-016-cdm-generic-instruments-generated.sh --skip-build
```

Endpoints:
- UI / ingress: `http://localhost:8080`
- API explorer (ingress): `http://localhost:8080/api/docs`
- Ingress health: `http://localhost:8080/health`
- Instruments: `http://localhost:18085/instruments`
- Instruments (ingress): `http://localhost:8080/reference-data/instruments`
- Order matcher health: `http://localhost:18110/health`
- Grafana dashboards: `http://localhost:8080/grafana/`
- Grafana local admin: `http://localhost:3001`
- Prometheus: `http://localhost:9090`

Look at the instrument model:

```bash
# An equity: CDM Equity / Ordinary, with BBGTICKER + FIGI identifiers
curl -s http://localhost:18085/instruments/IBM

# An ETF: CDM Fund / ExchangeTradedFund
curl -s http://localhost:18085/instruments/SPY

# /stocks was replaced by /instruments, not aliased
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:18085/stocks
```

Grafana access:
- Dashboards are anonymous Viewer surfaces through ingress.
- The start script prints the active local admin credential.
- Default convention: user from `TRADERX_GRAFANA_ADMIN_USER` or `traderx-admin`; password from `TRADERX_GRAFANA_ADMIN_PASSWORD` or `traderx-state-016`.

Smoke test:

```bash
./scripts/test-state-016-cdm-generic-instruments.sh
./scripts/test-state-016-cdm-generic-instruments.sh --skip-messaging
```

Status / stop:

```bash
./scripts/status-state-016-cdm-generic-instruments-generated.sh
./scripts/stop-state-016-cdm-generic-instruments-generated.sh
```

## Stable Entrypoints

Use root wrappers for this generated branch:

```bash
./start-env.sh   # start this state runtime
./status-env.sh  # runtime health/status
./stop-env.sh    # stop runtime
./test-env.sh    # state smoke/validation
```

Wrappers intentionally delegate to numbered state scripts to maximize reuse while keeping clone-first commands stable.
