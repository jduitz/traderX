#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_ID="017-us-treasury-trading"
PARENT_STATE_ID="016-cdm-generic-instruments"

# Direct calls delegate to the canonical orchestrator so generated runtime,
# API-explorer, CI, metadata, and documentation installers all run.
if (( ${TRADERX_GENERATION_DEPTH:-0} == 0 )); then
  echo "[info] delegating direct invocation to pipeline/generate-state.sh ${STATE_ID}"
  exec bash "${ROOT}/pipeline/generate-state.sh" "${STATE_ID}"
fi

echo "[info] generating parent state ${PARENT_STATE_ID} for ${STATE_ID}"
bash "${ROOT}/pipeline/generate-state.sh" "${PARENT_STATE_ID}"
bash "${ROOT}/pipeline/apply-state-patchset.sh" "${STATE_ID}"
bash "${ROOT}/pipeline/render-state-017-us-treasury-trading.sh"
bash "${ROOT}/pipeline/generate-state-architecture-doc.sh" "${STATE_ID}"

cat <<'EOT'
[summary] state=017-us-treasury-trading
[summary] parent-state=016-cdm-generic-instruments
[summary] track=functional
[summary] impacted-assets=treasury-reference-data,clean-price-simulation,trade-validation,authoritative-booking,order-reconciliation,treasury-ui
[summary] generated-path=generated/code/target-generated/us-treasury-trading
[summary] runtime-entrypoint=./scripts/start-state-017-us-treasury-trading-generated.sh
EOT
