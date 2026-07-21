# Data Model: CDM Generic Instruments

## Scope

This state defines data model impact relative to `009-order-management-matcher`.

Before this state, the entire instrument concept was:

```ts
export interface Stock {
  ticker: string;
  companyName: string;
}
```

## CDM adoption scope

Source of truth for every literal below, verified against `finos/common-domain-model`:

- `rosetta-source/src/main/rosetta/base-staticdata-asset-common-enum.rosetta`
- `rosetta-source/src/main/rosetta/base-staticdata-asset-common-type.rosetta`

### What CDM offers

CDM models an asset as a choice type, and instruments as a further choice beneath it:

```
choice Asset:            Cash | Commodity | DigitalAsset | Instrument
choice Instrument:       ListedDerivative | Loan | Security
type   AssetBase:        identifier AssetIdentifier (1..*), taxonomy, isExchangeListed,
                         party, partyRole, ancillaryPartyRole, assetType AssetTypeEnum (1..1)
type   InstrumentBase extends AssetBase
type   Security extends InstrumentBase:
                         securityType SecurityTypeEnum (1..1)
                         debtType     DebtType            (0..1)
                         equityType   EquityType          (0..1)
                         fundType     FundProductTypeEnum (0..1)
```

Above that sits a second layer — `EconomicTerms`, `Payout`, `TradableProduct`, product
qualification — which models bilateral OTC derivatives.

### What this state adopts

**The asset identifier and taxonomy layer only.**

- `AssetIdentifier { identifier, identifierType }`, where `identifierType` is `AssetIdTypeEnum`.
- `Security.securityType` and its conditional sub-type fields.

### What this state skips, and why

`EconomicTerms`, `Payout`, `TradableProduct`, and product qualification. That layer models the
economics of bilateral OTC derivatives. TraderX is a blotter where you buy 100 shares; it has no
payout to model. Adopting it would add a large amount of structure that nothing in the runtime reads.

### Taxonomy versus runtime model

**The `Asset -> Instrument -> Security` tree above is documentation.** It records what the TraderX
record maps onto in CDM terms. It is not built.

The runtime model is flat: a `Security`-shaped record plus its identifiers. Nothing constructs the
`Asset` or `Instrument` choice types, and **no discriminated union should be built** on the strength
of this section. When a later state adds an asset class that is not a security, that is the point at
which the choice type earns its keep.

`AssetBase.assetType` is likewise not emitted. Every instrument this state serves is
`AssetTypeEnum -> Security`, so the field would be a constant on every record.

## Enum literals

Quoted from CDM source, not paraphrased. Note there is no `TICKER` member — the Bloomberg ticker
symbology is `BBGTICKER`.

```
enum AssetIdTypeEnum extends ProductIdTypeEnum:
    CurrencyCode, ExchangeCode, ClearingCode

enum ProductIdTypeEnum:
    BBGID, BBGTICKER, CUSIP, FIGI, ISDACRP, ISIN, Name, REDID, RIC, Other,
    Sicovam, SEDOL, UPI, Valoren, Wertpapier

enum SecurityTypeEnum:
    Debt, Equity, Fund, Warrant, Certificate

enum EquityTypeEnum:
    Ordinary, NonConvertiblePreference, DepositaryReceipt, ConvertiblePreference

enum DepositaryReceiptTypeEnum:
    ADR, GDR, IDR, EDR

enum FundProductTypeEnum:
    MoneyMarketFund, ExchangeTradedFund, MutualFund, OtherFund
```

`AssetIdTypeEnum` extends `ProductIdTypeEnum`, so its value set is the union of both blocks above.

Note the asymmetry in `Security`: `fundType` is the `FundProductTypeEnum` enum directly, while
`equityType` is a **type** that wraps the enum so a depositary receipt can name its flavour:

```
type EquityType:
    equityType        EquityTypeEnum              (0..1)
    depositaryReceipt DepositaryReceiptTypeEnum   (0..1)

    condition DepositaryReceiptSubType:
        if equityType <> EquityTypeEnum -> DepositaryReceipt
        then depositaryReceipt is absent
```

The runtime model mirrors that asymmetry rather than flattening it, because flattening would lose the
ADR/GDR distinction the moment a depositary receipt is seeded.

## Entity Changes

### Added

`Instrument`, served by `reference-data`:

| Field | Type | Notes |
|---|---|---|
| `ticker` | `string` | Instrument key, unchanged. Equals the `BBGTICKER` identifier value. |
| `companyName` | `string` | Display name, from the seed file. See TD-01602. |
| `currency` | `string` | `USD` throughout this state. |
| `securityType` | `SecurityTypeEnum` | `Equity` or `Fund` in this state. |
| `equityType` | `EquityType?` | Present iff `securityType` is `Equity`. |
| `fundType` | `FundProductTypeEnum?` | Present iff `securityType` is `Fund`. |
| `identifiers` | `AssetIdentifier[]` | `[{BBGTICKER, "IBM"}, {FIGI, "BBG000BLNNH6"}]` |

### Changed

- `Stock` becomes `Instrument`; `stock.model.ts` becomes `instrument.model.ts`, and the module,
  service, and controller are renamed to match.
- The seed file gains `FIGI` and `SecurityType` columns and five ETF rows. It is renamed
  `data/s-and-p-500-companies.csv` -> `data/instruments.csv`, because it no longer holds only S&P 500
  companies.

### Removed

- `Stock`, and the `/stocks` resource. Declared, not aliased; see `contracts/contract-delta.md`.

## Invariants

Enforced at seed load time in `load-csv-data.ts`, not left to documentation:

1. **`ticker` equals the `BBGTICKER` identifier value.** Fails loudly.
2. **Exactly one of `equityType` / `fundType` is present and matches `securityType`** (CDM's
   `EquitySubType` and `FundSubType` conditions). TypeScript does not enforce this at runtime, and a
   documented-only rule drifts. A disagreement means the mapping code is wrong, so it throws.
3. **Every instrument in the supported ticker set carries both `BBGTICKER` and `FIGI`.** Scoped to
   the supported set, not the whole seed file — see the unmapped-ticker policy. Asserted by the smoke
   test against the live endpoint rather than at load time: a supported-but-unmapped ticker should
   surface as a failing assertion, not as a container that will not start.

## Seed data policy

Identifiers are baked in offline. TraderX never calls a symbology provider at runtime.

- **Unmapped tickers.** The seed file is a historical S&P 500 snapshot, so some constituents have
  since been acquired or delisted. Those rows are emitted with `BBGTICKER` only and log a warning.
  Generation does not fail. 14 of 510 rows are in this state, none of them in the supported set.
- **Delisted-but-supported tickers.** `DFS` is in the default supported set but was acquired and
  delisted, so it does not resolve as an active listing. Its identifiers are baked in explicitly.
- **Ticker reuse.** A delisted ticker can be reissued to a different security. `INFO` was IHS Markit
  and now belongs to an exchange-traded fund. Because the S&P 500 constituent list contains no funds
  by construction, a constituent row resolving to an exchange-traded product is treated as a false
  match and falls back to `BBGTICKER` only.
- **Non-common-stock results.** REITs map to `Equity` / `Ordinary` — a REIT's listed shares are
  ordinary shares, and CDM has no REIT-specific member — and log. 27 of 510 rows are REITs. ADR and
  preference mappings exist so a future seed row is classified rather than silently defaulted.
- **Supplemental instruments.** `UBS`, `DB`, `FNMA` and `FNF` are not in the seed CSV at all but are
  in the default supported set, so a FIGI column alone would never reach them. Their identifiers are
  baked into the supplemental seed list itself.

## Compatibility Notes

- The instrument key stays ticker text. No surrogate `instrumentId`, and no foreign key. Every
  consumer still passes `String security`, so trade, position, and order models are untouched.
- `companyName` is retained rather than renamed to `name`. See TD-01602 in `spec.md`.
- `CIK` is not emitted as an identifier — CDM's `AssetIdTypeEnum` has no CIK member and this state
  does not add one. The `CIK` column is retained in the seed file; not emitting it is not the same as
  dropping the source data.

## Explicitly out of scope

Database schema. Trade and position models. `OrderBook`. `Side` (still Buy/Sell). `Quantity` (still
an integer). State `008`'s pricing model. Surrogate instrument IDs. Any non-equity, non-fund asset
class. The UI beyond what the `/instruments` rename forces.

## Traceability

- FR-01603, FR-01604, FR-01605: the `Instrument` shape and its enum sources.
- FR-01606, FR-01607, FR-01608: the invariants above.
- FR-01612, FR-01613: the seed data policy above.
- FR-01615: the key decision under "Compatibility Notes".
- NFR-01601, NFR-01602, NFR-01603, NFR-01604: "CDM adoption scope" and "Invariants".
