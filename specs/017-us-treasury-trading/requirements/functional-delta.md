# Functional Delta

State 017 preserves State 016 stocks, ETFs, `/instruments`, pricing, orders,
trades, positions, PostgreSQL, NATS, ingress, and observability.

It adds:

- five CDM-shaped fixed-rate Treasury Debt records keyed by TraderX display
  codes;
- clean-price simulation and approximate YTM;
- maturity-aware validation and quote suppression;
- face-amount valuation and Treasury-specific average clean purchase price;
- long-only Treasury validation at trade service, matcher, and locked processor;
- synchronous authoritative Treasury order booking with stable idempotency IDs;
- persisted pending execution reconciliation;
- account 17017 and Treasury seed trades/positions;
- unified multi-asset ticket and blotter presentation.

The only public reference-data rename is `ticker`/`companyName` to
`instrumentKey`/`displayName`. Transactional APIs deliberately retain
`security`, `quantity`, and `price`.
