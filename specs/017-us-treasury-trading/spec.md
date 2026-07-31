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

1. Tickets and APIs reject non-positive or non-$100 Treasury quantities.
2. Direct sells cannot exceed settled face amount.
3. New sell orders cannot exceed settled face amount less remaining face
   reserved by other open sell orders.
4. The trade processor locks the position, persists authoritative rejection
   reasons, and never mutates or publishes a position for a rejected trade.

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

### US5 — Use one multi-asset trading UI

As a trader, I use one set of tickets and blotters for stocks, ETFs, and
Treasuries, with optional asset-class filters.

1. Selectors group Stocks, ETFs, and U.S. Treasuries.
2. Treasury views use Face Amount, Clean Price, approximate YTM, coupon,
   maturity, clean value, and the accrued-interest exclusion.
3. Treasury clean prices have no dollar prefix or equity direction arrow.

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
  fill `floorTo100(remaining ÷ 2)`.
- **FR-01716**: Seed account 17017, users `user02`, `user08`, `user10`, and five
  settled $100,000 positions/trades.
- **FR-01717**: Present all asset classes through unified selectors and
  blotters with Treasury-specific labels and valuation.

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

## Success Criteria

- **SC-01701**: Generation from a clean State 016 parent reproduces the runtime.
- **SC-01702**: Automated unit/integration coverage passes for reference data,
  pricing, maturity, routing, reservation, idempotency, concurrent oversell,
  pending retry, and UI valuation.
- **SC-01703**: The State 017 smoke test passes after chaining State 016 and
  State 009, including the clean-database lingering-order prerequisite.
- **SC-01704**: Repository front-matter, SpecKit, readiness, spec-coverage, and
  prepublication gates pass.
- **SC-01705**: Generated Docker Compose starts, serves five Treasury records,
  completes a two-fill Treasury order, and stops cleanly.

## Exclusions

Auctions, live Treasury feeds, accrued interest, dirty prices, coupon and
maturity cash flows, yield curves, bills, TIPS, floating-rate notes, other bond
markets, short Treasuries, full CDM trade lifecycle, and FIGI transactional
identity are excluded.
