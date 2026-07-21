#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATED_ROOT="${TRADERX_GENERATED_ROOT:-${ROOT}/generated}"
TARGET_ROOT="${GENERATED_ROOT}/code/target-generated"
STATE_DIR="${TARGET_ROOT}/cdm-generic-instruments"
TARGET_FRONTEND_DIR="${TARGET_ROOT}/web-front-end/angular"
FRONTEND_OVERRIDE_SOURCE_DIR="${ROOT}/specs/016-cdm-generic-instruments/generation/frontend-overrides/web-front-end/angular"
COMPOSE_FILE="${STATE_DIR}/docker-compose.yml"
DASHBOARD_DIR="${STATE_DIR}/observability/grafana/dashboards"
COMPOSE_PROJECT_LABEL="traderx-state-016"

require_file() {
  local path="$1"
  [[ -f "${path}" ]] || {
    echo "[fail] missing required file: ${path}"
    exit 1
  }
}

require_file "${COMPOSE_FILE}"

perl -0pi -e "s/^name:\\s*traderx-state-\\d+/name: ${COMPOSE_PROJECT_LABEL}/m" "${COMPOSE_FILE}"

# Grafana credentials and Promtail/Grafana runtime defaults are state-scoped, so
# this state's compose must not inherit 009's baked-in values.
bash "${ROOT}/pipeline/normalize-observability-runtime.sh" "016" "${COMPOSE_FILE}"

# Dashboards filter Loki by compose project. Inherited copies point at the
# parent's project and would render empty under this state's runtime.
while IFS= read -r dashboard_file; do
  perl -0pi -e "s/compose_project=\\\\\"traderx-state-\\d+\\\\\"/compose_project=\\\\\"${COMPOSE_PROJECT_LABEL}\\\\\"/g" "${dashboard_file}"
done < <(find "${DASHBOARD_DIR}" -maxdepth 1 -type f -name '*.json' | sort)

cat > "${STATE_DIR}/README.md" <<'EOF'
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
EOF

if [[ -d "${FRONTEND_OVERRIDE_SOURCE_DIR}" ]]; then
  cp -R "${FRONTEND_OVERRIDE_SOURCE_DIR}/." "${TARGET_FRONTEND_DIR}/"
else
  echo "[fail] frontend override source not found: ${FRONTEND_OVERRIDE_SOURCE_DIR}"
  exit 1
fi

echo "[done] rendered state 016 CDM generic instruments refinements into ${STATE_DIR}"
