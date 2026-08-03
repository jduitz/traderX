# Contract Delta

## Reference data

- `GET /instruments`
- `GET /instruments/{instrumentKey}`
- public names are `instrumentKey` and `displayName`;
- Treasuries add a compact `shortDisplayName` for tickets and blotters without
  changing transactional identifiers;
- Treasuries add `assetClass=US_TREASURY`, `securityType=Debt`, maturity, and
  `debtEconomics`;
- the authoritative OpenAPI is `reference-data/openapi.yaml`.

## Pricing

`pricing.<instrumentKey>` remains the NATS subject. Treasury payloads add:

- `cleanPrice` and `priceSemantics=CLEAN_PERCENT_OF_PAR`;
- `approximateYtmPercent`;
- `quoteTimestamp`, also used as `asOf`;
- `maturityDate`, `matured`, `simulated`, and official seed provenance.

## Trades

Requests retain `security`, `quantity`, and `price`. Treasury semantics are face
amount and clean percent of par. Responses permit `Rejected` and optional
`rejectionReason`/`sourceOrderId`.

## Orders

Responses retain public lifecycle statuses and add nullable pending execution
details. Treasury fills are acknowledged synchronously by internal
`POST /tradeservice/order`; stocks and ETFs still use trade service and
`/trades`.

Validation distinguishes unsupported metadata, face amounts below $100,
non-$100 increments, maturity, insufficient settled/unreserved face, and
pending-cancellation conflict. The UI surfaces these validation failures without
closing the active ticket. Trade-service validation failures use the JSON shape
`{"detail":"<message>"}` so the UI can render the authoritative server message.

Position blotter headers provide their complete financial labels as hover text
when responsive sizing truncates the visible header.
