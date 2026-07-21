# Learning Guide For 016-cdm-generic-instruments

This snapshot is code-first output. Canonical intent remains in SpecKit artifacts.

## Learning Focus

- Understand how an industry standard (FINOS CDM) is adopted as a subset: what the asset identifier and taxonomy layer buys, and why the derivative economics layer is skipped.
- Compare a two-string `Security` against a CDM `Security`-shaped record with multiple asset identifiers, and see why the taxonomy stays documentation rather than a runtime union.
- Review how a resource is replaced rather than aliased, and how a state declares a breaking contract change without disturbing its ancestors.
- Validate that an ETF flows through validation, matching, trade, and position exactly as an equity does.

## Read In This Snapshot

- [README.md](./README.md)
- [RUN_FROM_CLONE.md](./RUN_FROM_CLONE.md)
- [STATE.md](./STATE.md)
- [docs/README.md](./docs/README.md)
- [docs/learning/README.md](./docs/learning/README.md)

## Canonical Spec Sources

- Feature pack: `specs/016-cdm-generic-instruments`
- State docs map route: `/docs/spec-kit/state-docs`
- Learning guide route: `/docs/learning/state-016-cdm-generic-instruments`
- Learning guide markdown path in source branch: `docs/learning/state-016-cdm-generic-instruments.md`
- Source branch feature pack (exact commit): https://github.com/jduitz/traderX/tree/25ea8b839e2fdd1dfe9b77f8917e3d5e4a8260bd/specs/016-cdm-generic-instruments
- Source branch learning guide (exact commit): https://github.com/jduitz/traderX/blob/25ea8b839e2fdd1dfe9b77f8917e3d5e4a8260bd/docs/learning/state-016-cdm-generic-instruments.md
