#!/usr/bin/env bash
set -euo pipefail

INGRESS_URL="http://localhost:8080"
REFERENCE_DATA_URL="${REFERENCE_DATA_URL:-http://localhost:18085}"
PRICE_URL="${PRICE_URL:-http://localhost:18100}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATED_ROOT="${TRADERX_GENERATED_ROOT:-${REPO_ROOT}/generated}"
TARGET_ROOT="${GENERATED_ROOT}/code/target-generated"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-traderx-state-017}"
COMPOSE_FILE="${TRADERX_COMPOSE_FILE:-${TARGET_ROOT}/us-treasury-trading/docker-compose.yml}"
SKIP_MESSAGING=0

while (( "$#" )); do
  case "$1" in
    --skip-messaging)
      SKIP_MESSAGING=1
      ;;
    *)
      if [[ "${INGRESS_URL}" == "http://localhost:8080" ]]; then
        INGRESS_URL="$1"
      else
        echo "[error] usage: $0 [INGRESS_URL] [--skip-messaging]"
        exit 1
      fi
      ;;
  esac
  shift
done

if [[ "${TRADERX_LOCAL_RUNTIME_SCRIPT:-0}" != "1" ]]; then
  LOCAL_RUNTIME_SCRIPT="${TARGET_ROOT}/scripts/$(basename "${BASH_SOURCE[0]}")"
  if [[ -x "${LOCAL_RUNTIME_SCRIPT}" ]]; then
    args=("${INGRESS_URL}")
    (( SKIP_MESSAGING == 1 )) && args+=("--skip-messaging")
    exec "${LOCAL_RUNTIME_SCRIPT}" "${args[@]}"
  fi
fi

for command in curl jq rg docker; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "[error] required command not found: ${command}"
    exit 1
  }
done
[[ -f "${COMPOSE_FILE}" ]] || {
  echo "[error] compose file not found: ${COMPOSE_FILE}"
  echo "[hint] run: bash pipeline/generate-state.sh 017-us-treasury-trading"
  exit 1
}

http_with_body() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ -n "${body}" ]]; then
    curl -sS -w '\n%{http_code}' -H 'Content-Type: application/json' -X "${method}" -d "${body}" "${url}"
  else
    curl -sS -w '\n%{http_code}' -X "${method}" "${url}"
  fi
}

echo "[check] clean-database prerequisite for inherited State 009 smoke"
lingering_response="$(
  http_with_body POST "${INGRESS_URL}/order-matcher/orders" \
    '{"accountId":22214,"security":"IBM","side":"Buy","quantity":100,"limitPrice":1.000}'
)"
lingering_http="$(echo "${lingering_response}" | tail -n1)"
if [[ "${lingering_http}" != "201" ]]; then
  echo "[error] failed to create the inherited IBM lingering order: HTTP ${lingering_http}"
  echo "${lingering_response}" | sed '$d'
  exit 1
fi

echo "[check] inherited State 016 and State 009 behavior (chained smoke)"
parent_args=("${INGRESS_URL}")
(( SKIP_MESSAGING == 1 )) && parent_args+=("--skip-messaging")
TRADERX_COMPOSE_FILE="${COMPOSE_FILE}" \
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME}" \
TRADERX_GRAFANA_ADMIN_USER="${TRADERX_GRAFANA_ADMIN_USER:-traderx-admin}" \
TRADERX_GRAFANA_ADMIN_PASSWORD="${TRADERX_GRAFANA_ADMIN_PASSWORD:-traderx-state-017}" \
  "${REPO_ROOT}/scripts/test-state-016-cdm-generic-instruments.sh" "${parent_args[@]}"

treasury_keys=(
  UST-20280630
  UST-20310630
  UST-20360515
  UST-20460515
  UST-20560515
)
expected_figis=(
  BBG022ZR1Z79
  BBG022ZR1Z51
  BBG0221YLR31
  BBG0226BZH97
  BBG0221YLR40
)
expected_prices=(99.878 99.665 99.257 98.481 99.293)
expected_price_bands=(0.15 0.30 0.50 0.75 1.00)

echo "[check] five Treasury instruments expose Debt economics and bounded identifiers"
all_instruments="$(curl -fsS "${REFERENCE_DATA_URL}/instruments")"
treasury_count="$(echo "${all_instruments}" | jq '[.[] | select(.assetClass == "US_TREASURY")] | length')"
[[ "${treasury_count}" == "5" ]] || {
  echo "[error] expected 5 Treasury instruments, got ${treasury_count}"
  exit 1
}
for index in "${!treasury_keys[@]}"; do
  key="${treasury_keys[$index]}"
  figi="${expected_figis[$index]}"
  expected_price="${expected_prices[$index]}"
  expected_band="${expected_price_bands[$index]}"
  instrument="$(curl -fsS "${REFERENCE_DATA_URL}/instruments/${key}")"
  echo "${instrument}" | jq -e \
    --arg key "${key}" --arg figi "${figi}" \
    '.instrumentKey == $key
      and (.displayName | length > 0)
      and .assetClass == "US_TREASURY"
      and .securityType == "Debt"
      and .currency == "USD"
      and .matured == false
      and .debtEconomics.fixedInterest.rateType == "Fixed"
      and .debtEconomics.fixedInterest.couponFrequency == "Semiannual"
      and .debtEconomics.principalRepayment.style == "Bullet"
      and ([.identifiers[] | select(.identifierType == "FIGI" and .identifier == $figi)] | length) == 1
      and ([.identifiers[] | select(.identifierType == "CUSIP" or .identifierType == "ISIN" or .identifierType == "BBGTICKER")] | length) == 0' \
    >/dev/null || {
      echo "[error] invalid Treasury reference record for ${key}"
      echo "${instrument}"
      exit 1
    }
  quote="$(curl -fsS "${PRICE_URL}/prices/${key}")"
  echo "${quote}" | jq -e --argjson expected "${expected_price}" --argjson band "${expected_band}" '
    .assetClass == "US_TREASURY"
      and .priceSemantics == "CLEAN_PERCENT_OF_PAR"
      and .simulated == true
      and .matured == false
      and .openPrice == $expected
      and .closePrice == $expected
      and ((.price - $expected) | fabs) <= $band
      and .cleanPrice == .price
      and .quoteTimestamp == .asOf
      and (.approximateYtmPercent | type == "number")
  ' >/dev/null || {
    echo "[error] invalid Treasury quote for ${key}"
    echo "${quote}"
    exit 1
  }
done

echo "[check] literal legacy Compose universe variables remain aligned"
for variable in REFERENCE_DATA_SUPPORTED_TICKERS PRICE_TICKERS; do
  rg -q "${variable}:.*UST-20280630.*UST-20560515" "${COMPOSE_FILE}" || {
    echo "[error] ${variable} does not contain the State 017 Treasury universe"
    exit 1
  }
done

echo "[check] State 017 account, users, seed trades, and positions"
seed_counts="$(
  docker compose -f "${COMPOSE_FILE}" --project-name "${COMPOSE_PROJECT_NAME}" exec -T database \
    psql -U traderx -d traderx -Atc \
    "select (select count(*) from accounts where id=17017 and displayname='U.S. Treasury Trading Account'),(select count(*) from accountusers where accountid=17017 and username in ('user02','user08','user10')),(select count(*) from trades where accountid=17017 and security like 'UST-%' and state='Settled'),(select count(*) from positions where accountid=17017 and security like 'UST-%' and quantity=100000);"
)"
[[ "${seed_counts}" == "1|3|5|5" ]] || {
  echo "[error] unexpected State 017 seed counts: ${seed_counts}"
  exit 1
}

echo "[check] invalid Treasury face amounts are rejected early"
invalid_order="$(http_with_body POST "${INGRESS_URL}/order-matcher/orders" \
  '{"accountId":17017,"security":"UST-20360515","side":"Buy","quantity":150,"limitPrice":99.250}')"
[[ "$(echo "${invalid_order}" | tail -n1)" == "400" ]] || {
  echo "[error] expected HTTP 400 for a $150 Treasury order"
  exit 1
}
invalid_trade="$(http_with_body POST "${INGRESS_URL}/trade-service/trade/" \
  '{"accountId":17017,"security":"UST-20360515","side":"Buy","quantity":150}')"
[[ "$(echo "${invalid_trade}" | tail -n1)" == "400" ]] || {
  echo "[error] expected HTTP 400 for a $150 Treasury trade"
  exit 1
}

echo "[check] derived open-sell reservations prevent oversubscription"
reserved_order="$(
  http_with_body POST "${INGRESS_URL}/order-matcher/orders" \
    '{"accountId":17017,"security":"UST-20460515","side":"Sell","quantity":60000,"limitPrice":200.000}'
)"
[[ "$(echo "${reserved_order}" | tail -n1)" == "201" ]] || {
  echo "[error] could not create the Treasury reservation order"
  exit 1
}
reserved_id="$(echo "${reserved_order}" | sed '$d' | jq -r '.orderId')"
over_reserved="$(
  http_with_body POST "${INGRESS_URL}/order-matcher/orders" \
    '{"accountId":17017,"security":"UST-20460515","side":"Sell","quantity":50000,"limitPrice":200.000}'
)"
[[ "$(echo "${over_reserved}" | tail -n1)" == "409" ]] || {
  echo "[error] expected HTTP 409 when Treasury sells exceed unreserved face amount"
  exit 1
}
curl -fsS -H 'Content-Type: application/json' -X POST -d '{}' \
  "${INGRESS_URL}/order-matcher/orders/${reserved_id}/cancel" >/dev/null

echo "[check] Treasury matcher execution books synchronously with stable IDs"
booking_order="$(
  curl -fsS -H 'Content-Type: application/json' -X POST \
    -d '{"accountId":17017,"security":"UST-20310630","side":"Buy","quantity":200000,"limitPrice":1.000}' \
    "${INGRESS_URL}/order-matcher/orders"
)"
booking_id="$(echo "${booking_order}" | jq -r '.orderId')"
first_fill="$(
  curl -fsS -H 'Content-Type: application/json' -X POST -d '{}' \
    "${INGRESS_URL}/order-matcher/orders/${booking_id}/force-fill"
)"
echo "${first_fill}" | jq -e \
  '.status == "PARTIALLY_FILLED" and .remainingQuantity == 100000 and .pendingTradeId == null' \
  >/dev/null || {
    echo "[error] expected the first $200,000 Treasury force-fill to book $100,000"
    echo "${first_fill}"
    exit 1
  }
second_fill="$(
  curl -fsS -H 'Content-Type: application/json' -X POST -d '{}' \
    "${INGRESS_URL}/order-matcher/orders/${booking_id}/force-fill"
)"
echo "${second_fill}" | jq -e \
  '.status == "FILLED" and .remainingQuantity == 0 and .pendingTradeId == null' >/dev/null || {
    echo "[error] expected the second Treasury force-fill to complete the order"
    echo "${second_fill}"
    exit 1
  }

booked_trades="$(curl -fsS "${INGRESS_URL}/position-service/trades/17017")"
echo "${booked_trades}" | jq -e --arg order "${booking_id}" '
  ([.[] | select(.sourceOrderId == $order)] | length) == 2
    and ([.[] | select(.id == ($order + "-exec-0"))] | length) == 1
    and ([.[] | select(.id == ($order + "-exec-100000"))] | length) == 1
' >/dev/null || {
  echo "[error] stable synchronous Treasury execution IDs were not persisted exactly once"
  exit 1
}

echo "[check] matcher reconciliation metrics are exported"
curl -fsS "http://localhost:18110/metrics" | rg -q 'traderx_treasury_reconciliation_retries_total' || {
  echo "[error] reconciliation metrics are missing"
  exit 1
}

echo "[check] State 017 frontend carries grouped selectors and Treasury semantics"
frontend="${TARGET_ROOT}/web-front-end/angular/main/app"
rg -q 'U\.S\. Treasuries' "${frontend}/trade/trade-ticket/trade-ticket.component.ts"
rg -q 'Face Amount' "${frontend}/trade/trade-ticket/trade-ticket.component.html"
rg -q 'Accrued interest and dirty settlement value are excluded' \
  "${frontend}/trade/order-ticket/order-ticket.component.html"
rg -q 'assetClassFilter' "${frontend}/trade/position-blotter/position-blotter.component.ts"

echo "[done] state 017 U.S. Treasury trading smoke tests passed"
