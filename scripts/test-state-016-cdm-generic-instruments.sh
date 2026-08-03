#!/usr/bin/env bash
set -euo pipefail

INGRESS_URL="http://localhost:8080"
REFERENCE_DATA_PORT="${REFERENCE_DATA_PORT:-18085}"
REFERENCE_DATA_URL="http://localhost:${REFERENCE_DATA_PORT}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-traderx-state-016}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATED_ROOT="${TRADERX_GENERATED_ROOT:-${REPO_ROOT}/generated}"
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
        echo "[error] unknown argument: $1"
        echo "[hint] usage: $0 [INGRESS_URL] [--skip-messaging]"
        exit 1
      fi
      ;;
  esac
  shift
done

if [[ "${TRADERX_LOCAL_RUNTIME_SCRIPT:-0}" != "1" ]]; then
  LOCAL_RUNTIME_SCRIPT="${GENERATED_ROOT}/code/target-generated/scripts/$(basename "${BASH_SOURCE[0]}")"
  if [[ -x "${LOCAL_RUNTIME_SCRIPT}" ]]; then
    args=("${INGRESS_URL}")
    if [[ "${SKIP_MESSAGING}" -eq 1 ]]; then
      args+=("--skip-messaging")
    fi
    exec "${LOCAL_RUNTIME_SCRIPT}" "${args[@]}"
  fi
fi

TARGET_ROOT="${GENERATED_ROOT}/code/target-generated"
COMPOSE_FILE="${TRADERX_COMPOSE_FILE:-${TARGET_ROOT}/cdm-generic-instruments/docker-compose.yml}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker command not found"
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[error] compose file not found: ${COMPOSE_FILE}"
  echo "[hint] run: bash pipeline/generate-state.sh 016-cdm-generic-instruments"
  exit 1
fi

# CDM AssetIdTypeEnum literals. There is no TICKER member; the Bloomberg ticker
# symbology is BBGTICKER.
BBGTICKER_TYPE="BBGTICKER"
FIGI_TYPE="FIGI"

identifier_value() {
  # identifier_value <instrument-json> <identifierType>
  echo "$1" | jq -r --arg t "$2" '[.identifiers[]? | select(.identifierType == $t) | .identifier][0] // ""'
}

echo "[check] instruments collection is served"
instruments_payload="$(curl -fsS "${REFERENCE_DATA_URL}/instruments")"
instruments_count="$(echo "${instruments_payload}" | jq 'length')"
if [[ "${instruments_count}" -lt 1 ]]; then
  echo "[error] expected at least one instrument from ${REFERENCE_DATA_URL}/instruments"
  exit 1
fi
echo "[info] instruments served=${instruments_count}"

# Asserted against what compose actually serves (the supported ticker set), not
# the full seed file: the unmapped-ticker policy permits BBGTICKER-only rows
# there, and those rows are filtered out before they reach this endpoint.
echo "[check] every inherited stock/ETF carries both ${BBGTICKER_TYPE} and ${FIGI_TYPE}"
missing_identifiers="$(
  echo "${instruments_payload}" | jq -r --arg bbg "${BBGTICKER_TYPE}" --arg figi "${FIGI_TYPE}" '
    [ .[]
      | select(
          .securityType != "Debt"
          and (
            ([.identifiers[]? | select(.identifierType == $bbg)] | length) == 0
            or ([.identifiers[]? | select(.identifierType == $figi)] | length) == 0
          )
        )
      | (.instrumentKey // .ticker)
    ] | join(",")
  '
)"
if [[ -n "${missing_identifiers}" ]]; then
  echo "[error] served instruments missing ${BBGTICKER_TYPE}/${FIGI_TYPE} identifiers: ${missing_identifiers}"
  exit 1
fi
echo "[info] all inherited stocks/ETFs carry both identifiers"

echo "[check] inherited instrument key equals the ${BBGTICKER_TYPE} identifier value"
ticker_mismatch="$(
  echo "${instruments_payload}" | jq -r --arg bbg "${BBGTICKER_TYPE}" '
    [ .[]
      | select(
          .securityType != "Debt"
          and ([.identifiers[]? | select(.identifierType == $bbg) | .identifier][0] // "")
            != (.instrumentKey // .ticker)
        )
      | (.instrumentKey // .ticker)
    ] | join(",")
  '
)"
if [[ -n "${ticker_mismatch}" ]]; then
  echo "[error] instruments whose ticker disagrees with ${BBGTICKER_TYPE}: ${ticker_mismatch}"
  exit 1
fi

echo "[check] every served instrument satisfies the CDM sub-type conditions"
subtype_violations="$(
  echo "${instruments_payload}" | jq -r '
    [ .[]
      | select(
          (.securityType == "Equity" and ((.equityType | not) or (.fundType != null)))
          or (.securityType == "Fund" and ((.fundType | not) or (.equityType != null)))
          or (.securityType == "Debt" and ((.debtEconomics | not) or (.equityType != null) or (.fundType != null)))
          or (.securityType != "Equity" and .securityType != "Fund" and .securityType != "Debt")
        )
      | (.instrumentKey // .ticker)
    ] | join(",")
  '
)"
if [[ -n "${subtype_violations}" ]]; then
  echo "[error] instruments violating the CDM securityType/sub-type conditions: ${subtype_violations}"
  exit 1
fi

echo "[check] every served instrument is USD-denominated"
currency_violations="$(
  echo "${instruments_payload}" | jq -r '[.[] | select(.currency != "USD") | (.instrumentKey // .ticker)] | join(",")'
)"
if [[ -n "${currency_violations}" ]]; then
  echo "[error] instruments without currency=USD: ${currency_violations}"
  exit 1
fi

echo "[check] ETF instrument SPY maps onto CDM Fund"
spy="$(curl -fsS "${REFERENCE_DATA_URL}/instruments/SPY")"
echo "${spy}" | jq -e '.securityType == "Fund"' >/dev/null || {
  echo "[error] expected SPY securityType=Fund"
  echo "${spy}"
  exit 1
}
echo "${spy}" | jq -e '.fundType == "ExchangeTradedFund"' >/dev/null || {
  echo "[error] expected SPY fundType=ExchangeTradedFund"
  echo "${spy}"
  exit 1
}
echo "${spy}" | jq -e '.equityType == null' >/dev/null || {
  echo "[error] expected SPY to omit equityType (CDM EquitySubType condition)"
  echo "${spy}"
  exit 1
}
spy_figi="$(identifier_value "${spy}" "${FIGI_TYPE}")"
[[ -n "${spy_figi}" ]] || {
  echo "[error] expected SPY to carry a ${FIGI_TYPE} identifier"
  exit 1
}
echo "[info] SPY securityType=Fund fundType=ExchangeTradedFund figi=${spy_figi}"

echo "[check] equity instrument IBM maps onto CDM Equity"
ibm="$(curl -fsS "${REFERENCE_DATA_URL}/instruments/IBM")"
echo "${ibm}" | jq -e '.securityType == "Equity"' >/dev/null || {
  echo "[error] expected IBM securityType=Equity"
  echo "${ibm}"
  exit 1
}
echo "${ibm}" | jq -e '.equityType.equityType == "Ordinary"' >/dev/null || {
  echo "[error] expected IBM equityType.equityType=Ordinary"
  echo "${ibm}"
  exit 1
}
echo "${ibm}" | jq -e '.fundType == null' >/dev/null || {
  echo "[error] expected IBM to omit fundType (CDM FundSubType condition)"
  echo "${ibm}"
  exit 1
}

# Guards the decision to keep the seed file's display name rather than adopting
# OpenFIGI's, which renders IBM as "INTL BUSINESS MACHINES CORP" and Apple as
# "APPLE INC".
echo "[check] instruments expose the seed display name, not the OpenFIGI name"
ibm_name="$(echo "${ibm}" | jq -r '.displayName // .companyName // ""')"
if [[ -z "${ibm_name}" ]]; then
  echo "[error] IBM instrument is missing companyName"
  echo "${ibm}"
  exit 1
fi
if [[ "${ibm_name}" == "INTL BUSINESS MACHINES CORP" ]]; then
  echo "[error] IBM companyName is the OpenFIGI name; expected the seed display name"
  exit 1
fi
aapl_name="$(curl -fsS "${REFERENCE_DATA_URL}/instruments/AAPL" | jq -r '.displayName // .companyName // ""')"
if [[ "${aapl_name}" != "Apple" ]]; then
  echo "[error] expected AAPL companyName='Apple' from seed data, got '${aapl_name}'"
  exit 1
fi
echo "[info] display names preserved (IBM='${ibm_name}' AAPL='${aapl_name}')"

echo "[check] supplemental (non-CSV) tickers carry both identifiers"
# UBS, DB, FNMA and FNF live only in SUPPLEMENTAL_SAMPLE_INSTRUMENTS, so a FIGI
# column on the seed file alone would never reach them.
for supplemental_ticker in UBS DB FNMA FNF; do
  supplemental="$(curl -fsS "${REFERENCE_DATA_URL}/instruments/${supplemental_ticker}")"
  supplemental_bbg="$(identifier_value "${supplemental}" "${BBGTICKER_TYPE}")"
  supplemental_figi="$(identifier_value "${supplemental}" "${FIGI_TYPE}")"
  if [[ -z "${supplemental_bbg}" || -z "${supplemental_figi}" ]]; then
    echo "[error] supplemental instrument ${supplemental_ticker} missing identifiers (bbgticker='${supplemental_bbg}' figi='${supplemental_figi}')"
    echo "${supplemental}"
    exit 1
  fi
  echo "[info] supplemental ${supplemental_ticker} bbgticker=${supplemental_bbg} figi=${supplemental_figi}"
done

echo "[check] /stocks is removed, not aliased"
stocks_code="$(curl -sS -o /dev/null -w '%{http_code}' "${REFERENCE_DATA_URL}/stocks")"
if [[ "${stocks_code}" != "404" ]]; then
  echo "[error] expected 404 from removed ${REFERENCE_DATA_URL}/stocks, got ${stocks_code}"
  exit 1
fi
stocks_ingress_code="$(curl -sS -o /dev/null -w '%{http_code}' "${INGRESS_URL}/reference-data/stocks")"
if [[ "${stocks_ingress_code}" != "404" ]]; then
  echo "[error] expected 404 from removed ${INGRESS_URL}/reference-data/stocks, got ${stocks_ingress_code}"
  exit 1
fi

echo "[check] instruments are reachable through ingress"
curl -fsS "${INGRESS_URL}/reference-data/instruments" >/dev/null || {
  echo "[error] expected instruments collection through ingress"
  exit 1
}

echo "[check] ETF tickers are aligned across reference-data and price-publisher"
# Inherited alignment contract from state 008 (FR-1014 / SC-1006): a supported
# ticker with no price stream fails the runtime.
SNAPSHOT_PRICES="${TARGET_ROOT}/price-publisher/data/snapshot-prices.json"
[[ -f "${SNAPSHOT_PRICES}" ]] || {
  echo "[error] snapshot prices file not found: ${SNAPSHOT_PRICES}"
  exit 1
}
for etf_ticker in SPY QQQ IWM VTI GLD; do
  if ! rg -q "REFERENCE_DATA_SUPPORTED_TICKERS:.*\b${etf_ticker}\b" "${COMPOSE_FILE}"; then
    echo "[error] ${etf_ticker} missing from REFERENCE_DATA_SUPPORTED_TICKERS in ${COMPOSE_FILE}"
    exit 1
  fi
  if ! rg -q "PRICE_TICKERS:.*\b${etf_ticker}\b" "${COMPOSE_FILE}"; then
    echo "[error] ${etf_ticker} missing from PRICE_TICKERS in ${COMPOSE_FILE}"
    exit 1
  fi
  if ! jq -e --arg t "${etf_ticker}" 'has($t)' "${SNAPSHOT_PRICES}" >/dev/null; then
    echo "[error] ${etf_ticker} missing an open/close entry in ${SNAPSHOT_PRICES}"
    exit 1
  fi
  if ! curl -fsS "${REFERENCE_DATA_URL}/instruments/${etf_ticker}" >/dev/null; then
    echo "[error] supported ETF ${etf_ticker} is not served by reference-data"
    exit 1
  fi
done
echo "[info] all 5 ETFs are aligned across reference-data, compose ticker config, and price-publisher"

echo "[check] state 016 lifecycle scripts probe /instruments"
rg -Fq "http://localhost:18085/instruments" "${REPO_ROOT}/scripts/start-state-016-cdm-generic-instruments-generated.sh" || {
  echo "[error] expected state 016 start script to probe /instruments"
  exit 1
}

echo "[check] the /instruments replacement is not backported to state 009"
# The replacement is declared by this state: 009 keeps /stocks and must stay
# runnable on its own terms. This only applies where 009's scripts are present.
# They are absent from this state's generated tree and from a published 016
# branch, which ship 016's lifecycle scripts only.
STATE_009_START="${REPO_ROOT}/scripts/start-state-009-order-management-matcher-generated.sh"
if [[ -f "${STATE_009_START}" ]]; then
  if rg -Fq "http://localhost:18085/instruments" "${STATE_009_START}"; then
    echo "[error] state 009's start script probes /instruments; the replacement must not be backported"
    exit 1
  fi
  rg -Fq "http://localhost:18085/stocks" "${STATE_009_START}" || {
    echo "[error] expected state 009's start script to keep probing /stocks"
    exit 1
  }
  echo "[info] state 009's start script still probes /stocks"
else
  echo "[info] state 009 lifecycle scripts are not shipped with this state; nothing to check"
fi

# The order and trade paths both run every security through trade-service's
# ticker validation, which this state repointed at /instruments. An ETF exercises
# the new CDM securityType end to end: validation, matching, trade, position.
echo "[check] ETF order lifecycle: limit order on SPY reaches the matcher"
spy_order_response="$(
  curl -sS -w '\n%{http_code}' \
    -H "Content-Type: application/json" \
    -X POST \
    -d '{"accountId":22214,"security":"SPY","side":"Buy","quantity":25,"limitPrice":1.000}' \
    "${INGRESS_URL}/order-matcher/orders"
)"
spy_order_http="$(echo "${spy_order_response}" | tail -n1)"
spy_order_body="$(echo "${spy_order_response}" | sed '$d')"
if [[ "${spy_order_http}" != "201" ]]; then
  echo "[error] expected 201 creating a SPY limit order, got ${spy_order_http}"
  echo "${spy_order_body}"
  exit 1
fi
spy_order_id="$(echo "${spy_order_body}" | jq -r '.orderId // empty')"
[[ -n "${spy_order_id}" ]] || {
  echo "[error] SPY order response missing orderId"
  echo "${spy_order_body}"
  exit 1
}
echo "[info] created SPY limit order=${spy_order_id}"

echo "[check] ETF order lifecycle: cancel"
spy_cancel_body="$(
  curl -fsS -H "Content-Type: application/json" -X POST -d '{}' \
    "${INGRESS_URL}/order-matcher/orders/${spy_order_id}/cancel"
)"
spy_cancel_status="$(echo "${spy_cancel_body}" | jq -r '.status // empty')"
if [[ "${spy_cancel_status}" != "CANCELED" ]]; then
  echo "[error] expected CANCELED for SPY order, got ${spy_cancel_status}"
  echo "${spy_cancel_body}"
  exit 1
fi

echo "[check] ETF trade path: force-filled SPY order becomes a trade and a position"
pre_spy_qty="$(curl -fsS "${INGRESS_URL}/position-service/positions/44044" | jq -r '[.[] | select(.security == "SPY")][0].quantity // 0')"
spy_fill_body="$(
  curl -fsS -H "Content-Type: application/json" -X POST \
    -d '{"accountId":44044,"security":"SPY","side":"Buy","quantity":30,"limitPrice":524.100}' \
    "${INGRESS_URL}/order-matcher/orders"
)"
spy_fill_id="$(echo "${spy_fill_body}" | jq -r '.orderId // empty')"
[[ -n "${spy_fill_id}" ]] || {
  echo "[error] SPY force-fill setup order missing orderId"
  echo "${spy_fill_body}"
  exit 1
}
spy_forced="$(
  curl -fsS -H "Content-Type: application/json" -X POST -d '{}' \
    "${INGRESS_URL}/order-matcher/orders/${spy_fill_id}/force-fill"
)"
spy_forced_status="$(echo "${spy_forced}" | jq -r '.status // empty')"
if [[ "${spy_forced_status}" != "FILLED" ]]; then
  echo "[error] expected FILLED for force-filled SPY order, got ${spy_forced_status}"
  echo "${spy_forced}"
  exit 1
fi

# A fill only becomes a trade if trade-service accepted the security, so this is
# the assertion that /instruments/{ticker} validation actually works.
expected_spy_qty="$((pre_spy_qty + 30))"
spy_position_observed=0
for _ in {1..30}; do
  current_spy_qty="$(curl -fsS "${INGRESS_URL}/position-service/positions/44044" | jq -r '[.[] | select(.security == "SPY")][0].quantity // 0')"
  if [[ "${current_spy_qty}" == "${expected_spy_qty}" ]]; then
    spy_position_observed=1
    break
  fi
  sleep 2
done
if [[ "${spy_position_observed}" -ne 1 ]]; then
  echo "[error] expected SPY position quantity=${expected_spy_qty} for account 44044 after force-fill"
  echo "[hint] a fill that never becomes a trade usually means trade-service ticker validation rejected the security"
  exit 1
fi
echo "[info] SPY position moved ${pre_spy_qty} -> ${expected_spy_qty} through the inherited trade pipeline"

echo "[check] inherited state 009 behavior (chained parent smoke)"
# Chaining the parent rather than re-asserting a snapshot of its checks,
# following scripts/test-state-014 -> 012 -> 011. TRADERX_COMPOSE_FILE plus the
# state-scoped project name and Grafana credential are what point the parent's
# compose-bound checks at 016's runtime instead of 009's.
chained_args=("${INGRESS_URL}")
if [[ "${SKIP_MESSAGING}" -eq 1 ]]; then
  chained_args+=("--skip-messaging")
fi
TRADERX_COMPOSE_FILE="${COMPOSE_FILE}" \
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME}" \
TRADERX_GRAFANA_ADMIN_USER="${TRADERX_GRAFANA_ADMIN_USER:-traderx-admin}" \
TRADERX_GRAFANA_ADMIN_PASSWORD="${TRADERX_GRAFANA_ADMIN_PASSWORD:-traderx-state-016}" \
  "${REPO_ROOT}/scripts/test-state-009-order-management-matcher.sh" "${chained_args[@]}"

echo "[done] state 016 CDM generic instruments smoke tests passed"
