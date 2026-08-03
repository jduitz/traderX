# Feature Specification: U.S. Treasury Trading

**Feature Branch**: `spec/017-us-treasury-trading`
**Created**: 2026-07-30
**Status**: Implemented
**Input**: Functional child of `016-cdm-generic-instruments`

## Intent

State 017 adds secondary-market trading for five fixed-rate U.S. Treasuries
without changing inherited stock and ETF behavior. Treasury quantity represents
USD face amount and Treasury prices represent simulated clean percent of par.

## User Stories and Acceptance Scenarios

### US1 — Discover Treasury instruments

As a trader, I can discover five U.S. Treasury Debt instruments in the unified
instrument selector and inspect coupon, term, maturity, FIGI, and auction-price
provenance.

1. Given State 017 is running, `/instruments` returns all inherited instruments
   plus exactly five `US_TREASURY` records.
2. Each Treasury has a TraderX `instrumentKey`, non-empty `displayName`, real
   FIGI, CDM-shaped Debt economics, and no committed CUSIP or ISIN.
3. Stocks and ETFs continue to use their ticker as both `instrumentKey` and
   `BBGTICKER`.

### US2 — View clean prices and approximate yield

As a trader, I receive correlated, maturity-sensitive simulated clean prices
and an approximate YTM with the same quote timestamp.

1. Auction prices remain in provenance at six decimals and runtime prices use
   three-decimal `HALF_UP` rounding.
2. Treasury prices are never requested from Yahoo Finance.
3. At the UTC maturity boundary, reference data marks the instrument matured
   and the publisher stops emitting new quotes.

### US3 — Trade Treasury face amount safely

As a trader, I can buy and sell Treasury face amount in $100 increments while
the system prevents negative Treasury positions.

1. Tickets show `Treasury quantity must be at least 100.` when Treasury face
   amount is below $100.
2. Tickets show `Treasury quantity must be a multiple of 100.` when Treasury
   face amount is at least $100 but is not divisible by $100.
3. Direct sells cannot exceed settled face amount and show
   `You cannot sell more Treasury face amount than you own and have available.`
4. New sell orders cannot exceed settled face amount less remaining face
   reserved by other open sell orders.
5. The trade processor locks the position, persists authoritative rejection
   reasons, and never mutates or publishes a position for a rejected trade.
6. Concurrent sell-order creation is serialized only for the same account and
   Treasury key so open-order reservations cannot be overcommitted.

### US4 — Reconcile uncertain Treasury executions

As an operator, I can rely on retry-safe synchronous Treasury booking even when
the matcher loses a response.

1. The matcher persists stable pending request data before HTTP booking.
2. Timeout and 5xx responses keep that pending data unchanged.
3. Every cycle reconciles pending requests before normal price matching,
   including after restart and after the market moves out of the money.
4. A duplicate stable trade ID returns the existing booking without another
   position mutation or duplicate publication.
5. Cancellation returns HTTP 409 while reconciliation is pending.
6. Processor HTTP runs without holding an order mutation lock; the matcher
   applies a response only if the persisted pending ID, quantity, and price
   still equal the pre-request snapshot.
7. One in-process reconciliation may run per order at a time, while unrelated
   order creation, fill, and cancellation remain available.

### US5 — Use one multi-asset trading UI

As a trader, I use one set of tickets and blotters for stocks, ETFs, and
Treasuries, with optional asset-class filters.

1. Selectors group Stocks, ETFs, and U.S. Treasuries.
2. Treasury views use Face Amount, Clean Price, approximate YTM, coupon,
   maturity, clean value, and the accrued-interest exclusion.
3. Treasury clean prices have no dollar prefix or equity direction arrow.
4. Compact ticket and blotter surfaces show `UST 2Y`, `UST 5Y`, `UST 10Y`,
   `UST 20Y`, or `UST 30Y`; full issue names remain visible in instrument
   details and exact `UST-YYYYMMDD` keys remain the transactional identifiers.
5. Truncated position-blotter headers expose their full financial labels on
   hover.

## Functional Requirements

- **FR-01701**: Preserve `security` as the transactional/database key; add
  `instrumentKey` only to reference-data contracts.
- **FR-01702**: Replace public `companyName` with `displayName`.
- **FR-01703**: Serve the five approved Treasury records and their documented
  FIGIs, CDM Debt subset, and auction provenance.
- **FR-01704**: Keep the literal historical configuration names
  `SUPPORTED_TICKERS`, `REFERENCE_DATA_SUPPORTED_TICKERS`, and `PRICE_TICKERS`;
  their values are general instrument keys.
- **FR-01705**: Implement the specified correlated price formula, term-specific
  step/band limits, three-decimal rounding, and approximate-YTM formula.
- **FR-01706**: Use one optional `TRADERX_FIXED_UTC_INSTANT` clock contract
  across reference data, pricing, trade validation, matcher validation, and
  tests.
- **FR-01707**: Reject new Treasury activity at maturity; maturity cash flows
  are out of scope.
- **FR-01708**: Interpret Treasury quantity as positive integer USD face amount
  divisible by 100 and value it as `face × clean price ÷ 100`.
- **FR-01709**: Calculate Treasury buy average cost by face weighting, preserve
  average clean price on sells, and reset it at zero position.
- **FR-01710**: Enforce long-only Treasury positions through early validation,
  derived order reservations, and authoritative pessimistic position locking.
- **FR-01711**: Extend Trades with `Rejected`, `rejectionReason`, and
  `sourceOrderId`; publish rejected trades but no rejected position update.
- **FR-01712**: Route Treasury matcher executions synchronously to trade
  processor while stocks and ETFs retain asynchronous trade-service routing.
- **FR-01713**: Use `<orderId>-exec-<filledBefore>` as the stable Treasury trade
  ID and enforce the 50-character database limit.
- **FR-01714**: Persist pending trade ID, quantity, price, and timestamp before
  submission and reconcile unchanged pending requests before normal matching.
- **FR-01715**: Fill Treasury remainder fully at or below $100,000; otherwise
  fill `floorTo100(remaining ÷ 2)`. Stock and ETF force-fill completes the
  entire remainder, while their normal automatic fill retains the inherited
  threshold behavior.
- **FR-01716**: Seed account 17017, users `user02`, `user08`, `user10`, and five
  settled $100,000 positions/trades.
- **FR-01717**: Present all asset classes through unified selectors and
  blotters with Treasury-specific labels and valuation.
- **FR-01718**: Treat exact `UST-` keys as State 017's canonical Treasury
  routing discriminator. The processor must still confirm returned reference
  metadata is both `US_TREASURY` and `Debt`; the prefix alone never authorizes
  Treasury booking.
- **FR-01719**: Use fixed 256-stripe matcher locks: account/security stripes
  for creation and reservation, and order-ID stripes for fill, cancellation,
  and reconciliation. No current path holds both; any future nesting acquires
  account/security before order ID.
- **FR-01720**: Use 256 fixed processor booking stripes keyed by trade ID,
  acquired before the database position lock, and check trade idempotency both
  before reference-data resolution and inside the transaction.
- **FR-01721**: Unknown non-Treasury symbols retain inherited lazy fallback
  prices, while unknown `UST-` keys return HTTP 404 without Yahoo fallback.

## Non-Functional Requirements

- **NFR-01701**: Generation remains sequential and derives from State 016.
- **NFR-01702**: State-specific behavior lives in the State 017 patch and
  frontend overrides; shared `templates/**` remain unchanged.
- **NFR-01703**: Reference-data or maturity uncertainty fails closed for
  Treasury activity.
- **NFR-01704**: Matcher HTTP timeouts are two seconds to connect and five
  seconds to respond.
- **NFR-01705**: Retry/success/rejection reconciliation counters are exposed in
  health and Prometheus metrics.
- **NFR-01706**: No secret, CUSIP, ISIN, live Treasury API, or Yahoo Treasury
  dependency is committed.
- **NFR-01707**: Clean regeneration and the full parent smoke chain are required
  before publication.
- **NFR-01708**: Trade-processor reference-data timeouts are configurable with
  `REFERENCE_DATA_CONNECT_TIMEOUT_MS` (default 2000) and
  `REFERENCE_DATA_READ_TIMEOUT_MS` (default 5000). Treasury reference-data
  failure persists a fail-closed rejection.
- **NFR-01709**: Treasury metadata lookup occurs before the processor database
  transaction. Non-`UST-` stock and ETF bookings perform no metadata lookup.
- **NFR-01710**: The inherited matcher histogram exposes finite buckets at
  0.01, 0.05, 0.1, 0.25, 0.5, and 1 second plus `+Inf`, sum, and count. This
  remediation retains placeholder values; measured latency is deferred.
- **NFR-01711**: The complete 009 → 016 → 017 smoke chain must pass twice on
  the same PostgreSQL volume without a reset between runs.

## Success Criteria

- **SC-01701**: Generation from a clean State 016 parent reproduces the runtime.
- **SC-01702**: Automated unit/integration coverage passes for reference data,
  pricing, maturity, routing, reservation, idempotency, concurrent oversell,
  pending retry, and UI valuation.
- **SC-01703**: The State 017 smoke test passes after chaining State 016 and
  State 009 twice consecutively on one volume, including reuse of the
  lingering-order prerequisite and timeout reconciliation after a controlled
  trade-processor pause.
- **SC-01704**: Repository front-matter, SpecKit, readiness, spec-coverage, and
  prepublication gates pass.
- **SC-01705**: Generated Docker Compose starts, serves five Treasury records,
  completes a two-fill Treasury order, and stops cleanly.

## Exclusions

Auctions, live Treasury feeds, accrued interest, dirty prices, coupon and
maturity cash flows, yield curves, bills, TIPS, floating-rate notes, other bond
markets, short Treasuries, full CDM trade lifecycle, and FIGI transactional
identity are excluded. Registry-based classification of debt keys outside the
canonical `UST-` convention and insert-safe concurrent first buys for an
unseeded position are also excluded; all five supported Treasury positions are
seeded before runtime trading.
