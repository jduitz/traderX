# Implementation Plan: 017-us-treasury-trading

## Scope

Generate from State 016 and add a bounded U.S. Treasury vertical slice without
changing inherited stock/ETF transaction identity or routing.

## Workstreams

1. Extend reference data and OpenAPI with asset-neutral names and five verified
   CDM Debt records.
2. Add deterministic clean-price simulation, approximate YTM, and maturity.
3. Add Treasury face valuation, seed data, long-only validation, rejection
   persistence, and processor idempotency.
4. Add matcher reservation, synchronous Treasury routing, stable IDs, pending
   persistence, and retry/restart reconciliation.
5. Extend the State 016-derived Angular overrides with unified grouped selectors
   and Treasury semantics.
6. Package changes as a State 017 patch plus frontend overrides and add the
   render/generation/lifecycle harness.
7. Regenerate from scratch, run unit/integration/frontend/gate checks, then
   start, smoke, and stop the Compose runtime.
8. Remediate reliability with fixed matcher/processor lock stripes, snapshot
   reconciliation around unlocked HTTP, bounded processor metadata lookup,
   restored inherited non-Treasury pricing and histogram compatibility, and a
   repeat-safe pause/unpause smoke scenario.

## Technical Constraints

- Preserve `security`, integer quantity, and three-decimal price columns.
- No CUSIP/ISIN persistence or Yahoo Treasury calls.
- No shared template edits.
- Sequential generation only.
- Never hand-edit the generated overlay patch. Test edits in an isolated child
  generated root, recapture from exact State 016 parent and tested State 017
  snapshots (excluding the frontend override-owned Angular tree), regenerate
  from the captured patch, and compare with the tested candidate.
- Do not publish the future snapshot branch/tag during implementation.
- Matcher paths do not nest account/security and order-ID stripes; future
  nesting must acquire account/security first. Processor booking stripes are
  acquired before database position locks and never in reverse.
- The bounded position-service call remains inside the Treasury sell
  reservation stripe by design; Treasury metadata resolution remains outside.

## Exit Criteria

- Clean regeneration has no manual generated-output dependency.
- Touched Node, Java, and Angular suites pass.
- State 017 → State 016 → State 009 smoke chain passes twice consecutively on
  the same database volume.
- Static repository and prepublication gates pass.
- Docker runtime starts, passes end-to-end checks, and stops.
- The feature branch is committed and clean; manual acceptance remains pending.
