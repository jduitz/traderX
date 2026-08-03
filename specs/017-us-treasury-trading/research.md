# Research and Decisions

## Treasury identity and source verification

The five auction-result PDFs on TreasuryDirect were rechecked during
implementation. Each auction CUSIP was submitted transiently to OpenFIGI and
returned one exact FIGI result:

| Instrument key | FIGI | Official clean price | Runtime seed |
|---|---|---:|---:|
| `UST-20280630` | `BBG022ZR1Z79` | 99.878432 | 99.878 |
| `UST-20310630` | `BBG022ZR1Z51` | 99.664909 | 99.665 |
| `UST-20360515` | `BBG0221YLR31` | 99.256552 | 99.257 |
| `UST-20460515` | `BBG0226BZH97` | 98.481099 | 98.481 |
| `UST-20560515` | `BBG0221YLR40` | 99.292811 | 99.293 |

CUSIPs were lookup inputs only and are not persisted in this pack or generated
runtime. The `UST-*` key is an application identifier (`Other`), not a claimed
market ticker.

## Booking route

Repository inspection found that State 009's matcher posts stocks/ETFs to
trade service, which publishes `/trades` asynchronously. State 017 preserves
that route and adds a trade-processor HTTP dependency only for Treasury fills.

## Retry model

A stable trade ID prevents duplicate processor mutation, while four pending
fields preserve the request whose response is uncertain. Both are necessary:
idempotency alone cannot tell the matcher that reconciliation remains due.
Reconciliation snapshots pending identity, quantity, and price under the
order-ID stripe, releases that stripe for processor HTTP, and reapplies only to
an unchanged pending record. A transient in-flight set suppresses duplicate
in-process requests without becoming durable state.

## Locking and timeout decisions

Matcher creation uses account/security stripes; fill, cancellation, and
reconciliation use order-ID stripes. No implemented path nests the two sets.
The Treasury sell position lookup remains inside its account/security stripe:
the two/five-second bounded network call can delay only the same account and
security, and that serialization closes the over-reservation race.

Processor booking uses fixed trade-ID stripes before database position locks.
Only canonical `UST-` keys trigger reference-data lookup, which happens before
the transaction and must return authoritative `US_TREASURY`/`Debt` metadata.
This convention is deliberately load-bearing in State 017; supporting other
debt-key forms requires a future registry classification design.

Concurrent first-buy insertion for a missing position row remains out of scope.
`findForUpdate` cannot lock an absent row, though the inherited retry path can
self-heal a primary-key collision. All five supported Treasury position rows
are seeded, so insert-safe/upsert handling is deferred with new instruments.

## Pricing model

Auction prices are deterministic bootstrap anchors. A shared random roll makes
Treasury movements correlated, a local roll avoids lockstep behavior, and mean
reversion plus term-specific bands prevents drift. Approximate YTM is display
analytics only; transaction value always uses clean price.

## Operational precedents

The State 009 smoke requires a lingering open order, so State 017 reuses a
matching open IBM prerequisite or creates it once before chaining the parent
checks. Seed validation identifies the five fixed trades rather than assuming
absolute mutable totals, and two consecutive runs on one volume verify actual
repeatability. State 016's snapshot
wrapper-path issue is avoided during manual verification by using direct Docker
Compose if necessary. The inherited NATS ingress WebSocket workaround is not
captured in generated source.
