# Feature Specification: CDM Generic Instruments

**Feature Branch**: `016-cdm-generic-instruments`  
**Created**: 2026-07-16  
**Status**: Implemented  
**Input**: Transition delta from `009-order-management-matcher`

## Context

TraderX has no instrument model. The entire concept is `Security { ticker, companyName }` — two
strings — and everywhere else an instrument travels as a bare `String security`. There is no
`Instruments` table; reference data is a hardcoded S&P 500 CSV inside the NestJS service. That blocks
any asset class that is not a US common stock.

This state replaces that with an instrument model shaped after the **FINOS Common Domain Model
(CDM)**, and proves it with stocks and ETFs. It deliberately does *not* generalize pricing,
quantities, or position keys — those are later states. The contribution here is the model and the
standards alignment, not new asset classes.

The direction came from CDM's industry-standard symbology: an instrument naming scheme already exists
as a standard, so TraderX should adopt it rather than invent one. State `014` independently recorded
the same debt (`specs/014-fdc3-intent-interoperability/spec.md:56`):

> TD-01402: Symbol interoperability across apps remains ticker-centric and not fully normalized to
> CDM-grade symbology; this should be upgraded to canonical multi-identifier handling (for example
> CDM-backed FIGI/ISIN/RIC strategy) in a follow-on state.

### Lineage tradeoff

The newest functional tip is `014` (`previous: 012`), not `009`. Parenting from `009` means this
state **does not close TD-01402 on the FDC3/C3 line** — it forks below C3, as a sibling of `010`.

`009` is chosen anyway, for a product reason rather than convenience. Reaching `014` means generating
through `010` (Kubernetes) and `011` (Tilt), which needs a cluster. A state that cannot be run cannot
be verified or demoed, and this repository's bar is that a state actually runs. This state addresses
the same debt on the C2 compose line; carrying it to the C3/FDC3 line is a follow-on state, and
TD-01402 stays open until then.

## User Stories

- As a developer, I want instruments modeled on an industry standard so TraderX names securities the
  way the industry already does, instead of inventing a local scheme.
- As a developer, I want an instrument to carry multiple identifiers so a symbol can be resolved
  across systems that do not agree on tickers.
- As a trader, I want to trade an ETF as well as a stock, so the platform is not silently limited to
  US common stock.
- As a maintainer, I want the CDM subset that TraderX adopts — and the parts it skips — written down,
  so later states extend the model instead of re-deciding it.
- As a maintainer, I want the removal of `/stocks` declared rather than silently aliased, so the
  inheritance contract stays honest.

## Functional Requirements

- FR-01601: Reference data SHALL expose instruments at `GET /instruments` and `GET /instruments/{ticker}`.
- FR-01602: `/stocks` and `/stocks/{ticker}` SHALL be **removed** and SHALL NOT be aliased or
  redirected. This is a declared, breaking divergence from the inherited state `009` contract; see
  `contracts/contract-delta.md`.
- FR-01603: An instrument SHALL carry `ticker`, `companyName`, `currency`, `securityType`, its
  matching CDM sub-type, and a list of CDM `AssetIdentifier` values.
- FR-01604: `securityType` SHALL use CDM `SecurityTypeEnum` literals. This state emits `Equity` and
  `Fund` only.
- FR-01605: Instrument identifiers SHALL use CDM `AssetIdTypeEnum` literals. This state populates
  `BBGTICKER` and `FIGI`.
- FR-01606: Every instrument SHALL carry a `BBGTICKER` identifier whose value equals its `ticker`.
- FR-01607: Every instrument in the supported ticker set SHALL carry both `BBGTICKER` and `FIGI`.
- FR-01608: Exactly one of `equityType` / `fundType` SHALL be present, matching `securityType`, per
  CDM's `EquitySubType` and `FundSubType` conditions.
- FR-01609: The seed universe SHALL include exchange-traded funds (`SPY`, `QQQ`, `IWM`, `VTI`, `GLD`)
  alongside equities, so a second `securityType` is exercised at runtime rather than only documented.
- FR-01610: An ETF SHALL be tradable through the inherited trade and order paths, and SHALL produce a
  position, exactly as an equity does.
- FR-01611: Instrument identifiers SHALL be baked into seed data offline. The runtime SHALL NOT call
  an external symbology provider.
- FR-01612: Seed rows that cannot be resolved to a FIGI SHALL be emitted with `BBGTICKER` only and
  SHALL log a warning. They SHALL NOT fail generation or startup.
- FR-01613: Seed rows whose security type is neither common stock nor an exchange-traded product
  SHALL be mapped to the closest CDM type and SHALL log. They SHALL NOT be silently dropped.
- FR-01614: `trade-service` ticker validation SHALL resolve against `/instruments/{ticker}`.
- FR-01615: The instrument key SHALL remain ticker text. Surrogate instrument identifiers and foreign
  keys are out of scope; see `data-model.md`.
- FR-01616: The supported ticker universe SHALL remain aligned between `reference-data` and
  `price-publisher`, preserving the inherited contract from state `008` (FR-1014).
- FR-01617: Inherited pricing, trade, position, and order-management flows from state `009` remain
  compatible except where this pack explicitly changes them.

## Non-Functional Requirements

- NFR-01601: The adopted CDM subset, and the layers deliberately skipped, SHALL be documented in
  `data-model.md` with enum literals quoted from CDM source rather than paraphrased.
- NFR-01602: CDM's `Asset -> Instrument -> Security` choice tree SHALL be treated as taxonomy
  documentation. The runtime model SHALL stay flat; no discriminated union is built.
- NFR-01603: CDM sub-type conditions SHALL be enforced at seed load time, not documented and left to
  drift. A disagreement between `securityType` and the populated sub-type SHALL fail loudly.
- NFR-01604: `ISIN` and `CUSIP` SHALL remain valid `identifierType` values but SHALL stay unpopulated,
  because those symbologies are licensed. `FIGI` is used as the second identifier because it is
  openly licensed and free to redistribute.
- NFR-01605: Observability, messaging, and runtime topology inherited from states `007`–`009` remain
  intact; this state changes no messaging subjects.
- NFR-01606: Runtime and topology constraints are captured in `system/runtime-topology.md`.
- NFR-01607: Architecture updates are encoded in `system/architecture.model.json`.
- NFR-01608: Generated state branches SHALL keep the inherited `C2` build/publish workflow and GHCR
  run-bundle assets.
- NFR-01609: The generated API explorer SHALL document this state's reference-data contract rather
  than the state `001` baseline contract.

## Success Criteria

- SC-01601: Generation hook exists and is runnable (`pipeline/generate-state-016-cdm-generic-instruments.sh`).
- SC-01602: State smoke test path is defined (`scripts/test-state-016-cdm-generic-instruments.sh`).
- SC-01603: `GET /instruments` returns every supported instrument carrying both `BBGTICKER` and
  `FIGI`, asserted against what compose serves rather than against the seed file.
- SC-01604: `GET /instruments/SPY` returns `securityType: Fund` with `fundType: ExchangeTradedFund`
  and no `equityType`.
- SC-01605: `GET /instruments/IBM` returns `securityType: Equity` with `equityType.equityType:
  Ordinary`, no `fundType`, and a human-readable `companyName`.
- SC-01606: `GET /instruments/{ticker}` resolves for supplemental tickers that are absent from the
  seed CSV (`UBS`, `DB`, `FNMA`, `FNF`), each carrying both identifiers.
- SC-01607: `GET /stocks` returns 404 directly and through ingress.
- SC-01608: Every supported ETF is present in the reference-data ticker configuration and in the
  price publisher snapshot, satisfying the inherited alignment contract (SC-1006).
- SC-01609: An ETF can be ordered, force-filled, and observed as a position through the inherited
  order and trade pipeline.
- SC-01610: State `009`'s lifecycle scripts are untouched and still probe `/stocks`.
- SC-01611: Generated snapshot branch and tag strategy are defined in the state catalog.

## Known Gaps

- TD-01402 remains open on the C3/FDC3 line. This state addresses the same debt on the C2 line only;
  see "Lineage tradeoff".
- TD-01601: This state's smoke test does not chain state `009`'s smoke script, unlike the
  `014 -> 012 -> 011` convention. `test-state-009` resolves its compose file from a fixed path, and
  the one-line fix is blocked because state `004`'s patchset embeds the full text of every script
  under `scripts/` as deletion hunks, so editing any of them breaks generation from `004` onward. The
  inherited helpers that take no compose path are chained live; the compose-bound checks are restated.
  See `tests/smoke/README.md`.
- TD-01602: `companyName` is retained as the JSON field name, which is semantically imperfect for an
  ETF. Renaming it reaches 14 consumers across `trade-service`, the reference-data contract, and
  three states' frontend overrides, for no CDM benefit. It should be renamed when the surrogate-ID
  migration already touches those files.
- TD-01603: The Angular `Stock` model and `SymbolService.getStocks()` keep their names. Only the
  endpoint moved; renaming the client-side model reaches the trade page, both tickets, the mocks and
  their specs, which this state leaves alone.
