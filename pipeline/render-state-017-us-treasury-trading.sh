#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATED_ROOT="${TRADERX_GENERATED_ROOT:-${ROOT}/generated}"
TARGET_ROOT="${GENERATED_ROOT}/code/target-generated"
STATE_DIR="${TARGET_ROOT}/us-treasury-trading"
TARGET_FRONTEND_DIR="${TARGET_ROOT}/web-front-end/angular"
FRONTEND_OVERRIDE_SOURCE_DIR="${ROOT}/specs/017-us-treasury-trading/generation/frontend-overrides/web-front-end/angular"
COMPOSE_FILE="${STATE_DIR}/docker-compose.yml"
DASHBOARD_DIR="${STATE_DIR}/observability/grafana/dashboards"
COMPOSE_PROJECT_LABEL="traderx-state-017"

[[ -f "${COMPOSE_FILE}" ]] || {
  echo "[fail] missing required file: ${COMPOSE_FILE}"
  exit 1
}
[[ -d "${FRONTEND_OVERRIDE_SOURCE_DIR}" ]] || {
  echo "[fail] frontend override source not found: ${FRONTEND_OVERRIDE_SOURCE_DIR}"
  exit 1
}

perl -0pi -e "s/^name:\\s*traderx-state-\\d+/name: ${COMPOSE_PROJECT_LABEL}/m" "${COMPOSE_FILE}"
bash "${ROOT}/pipeline/normalize-observability-runtime.sh" "017" "${COMPOSE_FILE}"

while IFS= read -r dashboard_file; do
  perl -0pi -e "s/compose_project=\\\\\"traderx-state-\\d+\\\\\"/compose_project=\\\\\"${COMPOSE_PROJECT_LABEL}\\\\\"/g" "${dashboard_file}"
done < <(find "${DASHBOARD_DIR}" -maxdepth 1 -type f -name '*.json' | sort)

cp -R "${FRONTEND_OVERRIDE_SOURCE_DIR}/." "${TARGET_FRONTEND_DIR}/"

cat > "${STATE_DIR}/README.md" <<'EOF'
## TraderX State 017 Runtime

This directory defines the Compose runtime for `017-us-treasury-trading`.

State 017 extends State 016 with five fixed-rate U.S. Treasury instruments,
simulated clean prices and approximate YTM, long-only face-amount trading,
authoritative synchronous Treasury booking, retry-safe pending executions, and
unified stock/ETF/Treasury tickets and blotters.

Primary endpoints:

- TraderX UI: `http://localhost:8080`
- API explorer: `http://localhost:8080/api/docs`
- Instruments: `http://localhost:18085/instruments`
- Price publisher: `http://localhost:18100/prices`
- Order matcher health/metrics: `http://localhost:18110/health`, `http://localhost:18110/metrics`
- Grafana: `http://localhost:8080/grafana/`
- Prometheus: `http://localhost:9090`
- NATS monitor: `http://localhost:8222/varz`

Treasury prices are simulated clean percent-of-par prices. Accrued interest,
dirty settlement value, coupons, and maturity repayment are outside this state.
EOF

echo "[done] rendered state 017 U.S. Treasury trading refinements into ${STATE_DIR}"
