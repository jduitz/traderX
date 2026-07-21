# Functional Testing Guide

State: `016-cdm-generic-instruments`

This guide captures intended functional behavior for this generated snapshot branch.

## What Should Work

- Builds on state `009` and preserves order-management, pricing, and observability runtime behavior.
- Replaces the two-string stock concept with an instrument model shaped after the FINOS Common Domain Model, carrying CDM asset identifiers (`BBGTICKER`, `FIGI`) and security types.
- Replaces `/stocks` with `/instruments` as a declared, non-aliased break, and seeds ETFs alongside equities so a second CDM `securityType` is exercised at runtime.

## Suggested Functional Validation

1. Start runtime using [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md).
2. Execute the state's smoke test script when available.
3. Confirm user-facing behavior and invariants described in [LEARNING.md](./LEARNING.md).
4. If behavior differs from expectations, compare with parent state using lineage links in [README.md](./README.md).

## Smoke Test Commands

```bash
./scripts/test-state-016-cdm-generic-instruments.sh
```

## Canonical References

- Spec pack: `specs/016-cdm-generic-instruments`
- Runtime guide: [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md)
- Snapshot learning guide: [LEARNING.md](./LEARNING.md)
- Snapshot metadata: [STATE.md](./STATE.md)
- Canonical Getting Started (main): https://github.com/jduitz/traderX/blob/main/docs/spec-kit/getting-started-with-traderx.md
- Canonical SpecKit docs (source commit): https://github.com/jduitz/traderX/tree/25ea8b839e2fdd1dfe9b77f8917e3d5e4a8260bd/docs/spec-kit
