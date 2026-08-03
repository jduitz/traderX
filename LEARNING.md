# Learning Guide For 017-us-treasury-trading

This snapshot is code-first output. Canonical intent remains in SpecKit artifacts.

## Learning Focus

- Understand how fixed-rate U.S. Treasury Debt instruments extend the CDM-shaped reference model without changing transactional identity.
- Review clean percent-of-par pricing, approximate YTM, maturity handling, and face-amount valuation.
- Trace long-only enforcement from early validation and reservations through authoritative position locking.
- Validate retry-safe synchronous Treasury booking while stocks and ETFs retain their inherited asynchronous route.

## Read In This Snapshot

- [README.md](./README.md)
- [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md)
- [STATE.md](./STATE.md)
- [docs/README.md](./docs/README.md)
- [docs/learning/README.md](./docs/learning/README.md)

## Canonical Spec Sources

- Feature pack: `specs/017-us-treasury-trading`
- State docs map route: `/docs/spec-kit/state-docs`
- Learning guide route: `/docs/learning/state-017-us-treasury-trading`
- Learning guide markdown path in source branch: `docs/learning/state-017-us-treasury-trading.md`
- Source branch feature pack (exact commit): https://github.com/jduitz/traderX/tree/808b683ec73267dd9cb52d1d1887d573856357f2/specs/017-us-treasury-trading
- Source branch learning guide (exact commit): https://github.com/jduitz/traderX/blob/808b683ec73267dd9cb52d1d1887d573856357f2/docs/learning/state-017-us-treasury-trading.md
