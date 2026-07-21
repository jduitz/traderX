# Research: CDM Generic Instruments

## Objective

Capture the implementation research context for state `016-cdm-generic-instruments` as a transition
from `009-order-management-matcher` on the `functional` track.

## Inputs Reviewed

- `finos/common-domain-model`, `rosetta-source/src/main/rosetta/base-staticdata-asset-common-enum.rosetta`
- `finos/common-domain-model`, `rosetta-source/src/main/rosetta/base-staticdata-asset-common-type.rosetta`
- OpenFIGI v3 mapping API (`https://api.openfigi.com/v3/mapping`)
- `specs/014-fdc3-intent-interoperability/spec.md` (TD-01402)
- `specs/008-pricing-awareness-market-data/spec.md` (FR-1014 / SC-1006, ticker alignment)
- `templates/reference-data-specfirst/**` (read-only, to see what the parent materializes)

## Key Decisions

1. **Adopt CDM's asset identifier and taxonomy layer only.** `EconomicTerms` / `Payout` /
   `TradableProduct` model bilateral OTC derivative economics. TraderX is a blotter where you buy 100
   shares. Adopting that layer would add structure nothing reads.
2. **Keep the taxonomy as documentation, not as a runtime union.** The `Asset -> Instrument ->
   Security` choice tree records what the record maps onto. Building it now would be speculative
   generality with one implementing type.
3. **FIGI as the second identifier.** ISIN and CUSIP are licensed and cannot be redistributed in seed
   data. FIGI is openly licensed, and OpenFIGI serves it without an API key. Both remain valid
   `identifierType` values in the contract; they are simply unpopulated.
4. **Ticker stays the key.** A surrogate `instrumentId` plus foreign keys would reach the trade,
   position, and order models — a different state's worth of work. This state is the model and the
   standards alignment.
5. **Keep `companyName`.** Semantically imperfect for an ETF, but renaming reaches 14 consumers for
   zero CDM benefit. See TD-01602.
6. **Enforce CDM conditions at load, not in prose.** TypeScript erases at runtime, so a documented
   invariant would drift silently.

## OpenFIGI mechanics

Free, no key required. `curl` can POST to it; `WebFetch` cannot.

```bash
curl -s -X POST 'https://api.openfigi.com/v3/mapping' -H 'Content-Type: application/json' \
  -d '[{"idType":"TICKER","idValue":"IBM","exchCode":"US"}]'
# -> {"figi":"BBG000BLNNH6","securityType":"Common Stock","marketSector":"Equity",...}
```

Findings that shaped the loader:

- **Key off `securityType`, not `securityType2`.** OpenFIGI reports `securityType2: "Mutual Fund"`
  for SPY, which would classify every ETF as a mutual fund. `securityType` reports `"ETP"`, which is
  the stock-versus-ETF discriminator: `"Common Stock"` -> CDM `Equity`, `"ETP"` -> CDM `Fund`.
- **Keyless limits: 10 jobs per request, 25 requests per minute.** 514 tickers is ~52 requests, a few
  minutes when batched in tens and paced. A free key raises this to 100 jobs per request.
- **Share-class separator differs.** The S&P list writes `BRK.B`; OpenFIGI wants `BRK/B`.
- **The seed list is a historical snapshot.** 39 of 514 tickers do not resolve as active listings —
  they have been acquired or delisted. `includeUnlistedEquities: true` recovers 25 of them, leaving
  14 genuinely unresolvable. All 25 supported tickers resolve.
- **`DFS` needed the unlisted pass.** Discover Financial Services was acquired by Capital One and
  delisted, so it does not resolve as an active listing — but it is in the default supported set, and
  would have failed the both-identifiers invariant. `includeUnlistedEquities` returns `BBG000QBR5J5`.
- **Ticker reuse produces false matches.** `INFO` was IHS Markit; the ticker now belongs to the
  Harbor PanAgora ETF, so an S&P constituent row resolves to an ETF FIGI. Since the constituent list
  contains no funds by construction, a constituent row resolving to `ETP` is treated as a false match
  and falls back to the `BBGTICKER`-only policy.
- **Names are not display names.** OpenFIGI returns `INTL BUSINESS MACHINES CORP` and `APPLE INC`.
  The seed file already carries nicer ones (`IBM`, `Apple`), and the UI shows them, so display names
  stay seed-sourced.

Results were baked into `data/instruments.csv` and the supplemental seed list. The runtime never
calls OpenFIGI.

## Risks and Mitigations

- **Risk: the ticker alignment check fails.** If `reference-data` knows a ticker the price publisher
  does not, the inherited state `008` contract breaks. Each ETF needs three edits — seed row, compose
  ticker configuration (which appears twice, as `REFERENCE_DATA_SUPPORTED_TICKERS` and
  `PRICE_TICKERS`), and a price snapshot entry.
  - Mitigation: asserted directly by the state smoke test across all three locations.
- **Risk: a stale `/stocks` reference fails at container-health time.** The Prometheus blackbox probe
  target is easy to miss and surfaces as a confusing timeout rather than a clean error.
  - Mitigation: swept the whole generated tree for `/stocks`; the probe target is updated and the
    smoke test asserts `/stocks` returns 404.
- **Risk: seed identifiers rot.** Tickers get acquired, delisted, and reissued.
  - Mitigation: unresolvable rows degrade to `BBGTICKER` only with a warning rather than failing.
    The both-identifiers invariant is scoped to the supported set, which the smoke test asserts.
- **Risk: the instrument payload breaks `trade-service` deserialization.** `TradeOrderController`
  builds a plain `new RestTemplate()`, so it does not use Spring Boot's configured `ObjectMapper`.
  If unknown properties were fatal, the added fields would reject every trade.
  - Mitigation: not reasoned about, exercised. The smoke test orders and force-fills an ETF and
    asserts a position appears, which only happens if validation and the trade path both accepted it.
- **Risk: behavior drift from the parent state.**
  - Mitigation: the parent's smoke script is chained wholesale (following the `014 -> 012 -> 011`
    convention), so 016 picks up 009's checks automatically instead of asserting a stale snapshot.
    See `tests/smoke/README.md` and TD-01601 (resolved).

## Rejected Alternatives

- **Parenting from `014`.** The newest functional tip, but reaching it needs a Kubernetes cluster. A
  state that cannot be run cannot be verified. See "Lineage tradeoff" in `spec.md`.
- **Aliasing `/stocks` to `/instruments`.** Two names for one resource with nothing to retire the old
  one. The removal is declared instead.
- **Renaming `companyName` to `name`.** 14 consumers, zero CDM benefit. TD-01602.
- **Emitting CIK as an identifier.** CDM's `AssetIdTypeEnum` has no CIK member, and this state does
  not invent one. The CIK column stays in the seed file.
- **Adding `securityType` to the CSV as CDM's own vocabulary.** The column holds OpenFIGI's raw
  `securityType` string, and the mapping to CDM lives in the loader where it is testable and where
  the closest-fit decisions are visible.
