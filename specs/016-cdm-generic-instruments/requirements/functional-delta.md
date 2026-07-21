# Functional Delta: 016-cdm-generic-instruments

Parent state: `009-order-management-matcher`

Only functional behavior changes introduced by this state.

## Added

- FR-01601: `GET /instruments` and `GET /instruments/{ticker}` on `reference-data`.
- FR-01603: Instruments carry `ticker`, `companyName`, `currency`, `securityType`, the matching CDM
  sub-type, and a list of CDM `AssetIdentifier` values.
- FR-01604: `securityType` uses CDM `SecurityTypeEnum` literals; this state emits `Equity` and `Fund`.
- FR-01605: Identifiers use CDM `AssetIdTypeEnum` literals; this state populates `BBGTICKER` and `FIGI`.
- FR-01606: Every instrument carries a `BBGTICKER` identifier equal to its `ticker`.
- FR-01607: Every instrument in the supported ticker set carries both `BBGTICKER` and `FIGI`.
- FR-01608: Exactly one of `equityType` / `fundType` is present and matches `securityType`, enforced
  at seed load time.
- FR-01609: Exchange-traded funds (`SPY`, `QQQ`, `IWM`, `VTI`, `GLD`) join the seed universe, giving
  the model a second `securityType` at runtime.
- FR-01610: An ETF is tradable through the inherited trade and order paths and produces a position.
- FR-01611: Identifiers are baked into seed data offline; the runtime calls no symbology provider.
- FR-01612: Seed rows with no resolvable FIGI are emitted with `BBGTICKER` only and log a warning.
- FR-01613: Seed rows that are neither common stock nor an exchange-traded product are mapped to the
  closest CDM type and logged, never silently dropped.

## Changed

- FR-01614: `trade-service` ticker validation resolves against `/instruments/{ticker}` instead of
  `/stocks/{ticker}`. It asserts on the status code only, so the URL is the whole change.
- FR-01616: The supported ticker universe grows by five ETFs and stays aligned between
  `reference-data` and `price-publisher`, preserving the inherited state `008` contract (FR-1014).
  Each ETF needs three edits — a seed row, the ticker configuration in compose, and a price snapshot
  entry — or the inherited alignment check fails.

## Removed

- FR-01602: `GET /stocks` and `GET /stocks/{ticker}` are removed, and are not aliased or redirected.
  This is a declared, breaking divergence from the inherited state `009` contract. States `001`–`009`
  keep `/stocks` and are untouched. See `../contracts/contract-delta.md`.

## Unchanged

- FR-01615: The instrument key remains ticker text. No surrogate `instrumentId`, no foreign key, so
  trade, position, and order models are untouched.
- FR-01617: Pricing, trade, position, and order-management flows inherited from state `009` remain
  compatible.

## Flow Impact

| Flow | Impact | Acceptance |
|---|---|---|
| Instrument lookup | Replaced. `/stocks` -> `/instruments`, new payload shape. | SC-01603, SC-01604, SC-01605, SC-01606, SC-01607 |
| Trade submission | Validation URL only. Behavior unchanged for equities. | SC-01609 |
| Order create / cancel / force-fill | Unchanged, now exercised with an ETF as well as an equity. | SC-01609 |
| Position update | Unchanged. An ETF fill produces a position like an equity fill. | SC-01609 |
| Pricing stream | Unchanged. Five ETF tickers added to the supported universe. | SC-01608 |
| Messaging subjects | None. | — |
| Startup readiness | `016` probes `/instruments`; `009` still probes `/stocks`. | SC-01610 |
