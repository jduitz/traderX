# Functional Testing Guide

State: `017-us-treasury-trading`

This guide captures intended functional behavior for this generated snapshot branch.

## What Should Work

- Builds on state `016` and preserves inherited stock and ETF behavior.
- Adds five fixed-rate U.S. Treasury Debt instruments with verified FIGIs, auction-price provenance, simulated clean prices, and approximate YTM.
- Adds long-only face-amount trading, retry-safe synchronous Treasury booking, and unified multi-asset UI behavior.

## Suggested Functional Validation

1. Start runtime using [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md).
2. Execute the state's smoke test script when available.
3. Confirm user-facing behavior and invariants described in [LEARNING.md](./LEARNING.md).
4. If behavior differs from expectations, compare with parent state using lineage links in [README.md](./README.md).

## Smoke Test Commands

```bash
./scripts/test-state-017-us-treasury-trading.sh
```

## Canonical References

- Spec pack: `specs/017-us-treasury-trading`
- Runtime guide: [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md)
- Snapshot learning guide: [LEARNING.md](./LEARNING.md)
- Snapshot metadata: [STATE.md](./STATE.md)
- Canonical Getting Started (main): https://github.com/jduitz/traderX/blob/main/docs/spec-kit/getting-started-with-traderx.md
- Canonical SpecKit docs (source commit): https://github.com/jduitz/traderX/tree/808b683ec73267dd9cb52d1d1887d573856357f2/docs/spec-kit
