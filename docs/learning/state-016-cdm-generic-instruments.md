---
title: "State 016: CDM Generic Instruments"
---

# State 016 Learning Guide

## Position In Learning Graph

- Previous state(s): [009-order-management-matcher](/docs/learning/state-009-order-management-matcher)
- Dotted-line parent(s): none
- Next state(s): [017-us-treasury-trading](/docs/learning/state-017-us-treasury-trading)

## Convergence Metadata

- Convergence state: `no`
- Convergence level: `none`
- Lineage role: `canonical`
- Nearest previous convergence: `none`
- Nearest next convergence: `none`

## Rendered Code

- Generated branch: [code/generated-state-016-cdm-generic-instruments](https://github.com/finos/traderX/tree/code/generated-state-016-cdm-generic-instruments)
- Authoring branch (spec source): [main](https://github.com/finos/traderX/tree/main)

## Code Comparison With Previous State

- Compare against `009-order-management-matcher`: [code/generated-state-009-order-management-matcher...code/generated-state-016-cdm-generic-instruments](https://github.com/finos/traderX/compare/code%2Fgenerated-state-009-order-management-matcher...code%2Fgenerated-state-016-cdm-generic-instruments)

## Plain-English Code Delta

- **Added:** FR-01601: `GET /instruments` and `GET /instruments/{ticker}` on `reference-data`.
- **Added:** FR-01603: Instruments carry `ticker`, `companyName`, `currency`, `securityType`, the matching CDM
- **Added:** FR-01604: `securityType` uses CDM `SecurityTypeEnum` literals; this state emits `Equity` and `Fund`.
- **Added:** FR-01605: Identifiers use CDM `AssetIdTypeEnum` literals; this state populates `BBGTICKER` and `FIGI`.
- **Added:** FR-01606: Every instrument carries a `BBGTICKER` identifier equal to its `ticker`.
- **Added:** FR-01607: Every instrument in the supported ticker set carries both `BBGTICKER` and `FIGI`.
- **Added:** FR-01608: Exactly one of `equityType` / `fundType` is present and matches `securityType`, enforced
- **Added:** FR-01609: Exchange-traded funds (`SPY`, `QQQ`, `IWM`, `VTI`, `GLD`) join the seed universe, giving

## Run This State

```bash
./scripts/start-state-016-cdm-generic-instruments-generated.sh
```

## Canonical Spec Links

- State spec pack: [/specs/cdm-generic-instruments](/specs/cdm-generic-instruments)
- Architecture: [/specs/cdm-generic-instruments/system/architecture](/specs/cdm-generic-instruments/system/architecture)
- Flows / topology: [/specs/cdm-generic-instruments/system/runtime-topology](/specs/cdm-generic-instruments/system/runtime-topology)
- Research: [link](/specs/cdm-generic-instruments/research)
- Data model: [link](/specs/cdm-generic-instruments/data-model)
- Quickstart: [link](/specs/cdm-generic-instruments/quickstart)

