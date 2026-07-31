# Non-Functional Delta

- State generation is sequential and reproducible from State 016.
- A single optional fixed UTC instant provides deterministic maturity tests.
- Treasury reference-data failure is fail-closed.
- Position validation and mutation share a database transaction and pessimistic
  row lock.
- Stable execution IDs and lookup-before-insert provide retry idempotency; the
  primary key remains final duplicate protection.
- The matcher persists uncertain request data and exposes reconciliation
  counters in health and Prometheus metrics.
- Connect/read timeouts are two/five seconds.
- No CUSIP, ISIN, secret, or live-market credential is stored.
- Inherited stock and ETF routing and valuation remain regression-tested.
