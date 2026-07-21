# Contract Delta: 016-cdm-generic-instruments

Parent state: `009-order-management-matcher`

State contract: `contracts/reference-data/openapi.yaml` (this pack). The generated API explorer
resolves a component's contract from the active state's pack when one is published there, and falls
back to the state `001` baseline otherwise, so this state's explorer documents `/instruments`.

## Declared replacement: `/stocks` -> `/instruments`

The inheritance rule permits divergence from an inherited contract only when the state declares it.
**This state declares one, and it is breaking.**

| Inherited from `009` | State `016` | Disposition |
|---|---|---|
| `GET /stocks` | `GET /instruments` | **Replaced.** `/stocks` returns 404. |
| `GET /stocks/{ticker}` | `GET /instruments/{ticker}` | **Replaced.** `/stocks/{ticker}` returns 404. |
| `Security` schema | `Instrument` schema | **Replaced.** |

`/stocks` is **removed, not aliased and not redirected**. A compatibility alias was rejected: it
would leave two names for one resource with nothing to retire the old one, and the point of this
state is that the model is now named after what it holds. States `001`–`009` keep `/stocks` and are
unaffected — the replacement applies from `016` forward on this line, and their lifecycle scripts and
runtimes are untouched.

Asserted by SC-01607: `GET /stocks` returns 404 both directly and through ingress.

## OpenAPI Changes

### Removed

- `/stocks`, `/stocks/{ticker}`
- `components.schemas.Security`

### Added

- `/instruments`, `/instruments/{ticker}`
- `components.schemas.Instrument`
- `components.schemas.AssetIdentifier`
- `components.schemas.AssetIdTypeEnum`
- `components.schemas.SecurityTypeEnum`
- `components.schemas.EquityType`
- `components.schemas.EquityTypeEnum`
- `components.schemas.DepositaryReceiptTypeEnum`
- `components.schemas.FundProductTypeEnum`

The enum schemas carry CDM's full value sets, not just the members this state populates. `ISIN` and
`CUSIP` are therefore valid `identifierType` values in the contract but never appear in a payload:
those symbologies are licensed and cannot be redistributed in seed data. `FIGI` is the second
identifier because it is openly licensed. See NFR-01604.

### Changed

`Security { ticker, companyName }` becomes:

```json
{
  "ticker": "IBM",
  "companyName": "IBM",
  "currency": "USD",
  "securityType": "Equity",
  "equityType": { "equityType": "Ordinary" },
  "identifiers": [
    { "identifier": "IBM", "identifierType": "BBGTICKER" },
    { "identifier": "BBG000BLNNH6", "identifierType": "FIGI" }
  ]
}
```

`ticker` and `companyName` keep their names and meaning, so this is additive for existing readers of
those two fields.

## Consumer Impact

| Consumer | Change |
|---|---|
| `trade-service` `validateTicker()` | Calls `/instruments/{ticker}`. It asserts on the status code only, so the URL is the whole change. |
| `trade-service` `Security.java` | Unchanged. `ticker` and `companyName` still deserialize; the added fields are ignored. |
| Angular `SymbolService` | Fetches `/instruments`. The method and `Stock` model keep their names; see TD-01603. |
| Angular `StateMetadataService` | Default status check probes `:18085/instruments`. |
| Generated UI status metadata | `reference-data` check probes `/reference-data/instruments` from state `016` onward. |
| Prometheus blackbox probe | Targets `http://reference-data:18085/instruments`. |
| `016` lifecycle scripts | Readiness probes `/instruments`. `009`'s scripts still probe `/stocks`. |

## Event Contract Changes

None. This state changes no messaging subjects and no payload contracts; see
`system/messaging-subject-map.md`. The instrument key remains ticker text, so `pricing.<TICKER>`
keeps its shape.

## Compatibility Notes

- **Breaking for `/stocks` callers.** Any consumer of `/stocks` on this line must move to
  `/instruments`. Every in-repo consumer is listed above and has been moved.
- **Non-breaking for the two inherited fields.** `ticker` and `companyName` are unchanged, so
  existing deserializers that read only those fields keep working.
- The supported ticker universe grows by five ETFs. The inherited alignment contract from state `008`
  (FR-1014 / SC-1006) still holds: every supported ticker has a price stream and a quote.
